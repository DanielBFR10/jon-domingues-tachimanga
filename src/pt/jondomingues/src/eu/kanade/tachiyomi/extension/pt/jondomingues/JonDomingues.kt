package eu.kanade.tachiyomi.extension.pt.jondomingues

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale

@Source
abstract class JonDomingues : KeiSource() {

    private val publisherCategories = listOf(
        "/category/dc-comics/",
        "/category/dynamite-entertainment/",
        "/category/idw-publishing/",
        "/image-comics/",
        "/category/marvel-comics/",
        "/category/mangas/",
    )

    override suspend fun getPopularManga(page: Int): MangasPage = fetchSeriesListing(page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchSeriesListing(page)

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .addQueryParameter("paged", page.toString())
            .build()
        return parseListing(client.get(url, Fetch.DOCUMENT).asJsoup())
    }

    private suspend fun fetchSeriesListing(page: Int): MangasPage {
        val categories = linkedMapOf<String, SManga>()
        val discoveredPublishers = (publisherCategories + discoverPublisherCategories()).distinct()

        discoveredPublishers.forEach { categoryPath ->
            val document = client.get("${baseUrl.trimEnd('/')}$categoryPath", Fetch.DOCUMENT).asJsoup()
            val categoryLinks = fetchArchiveCategoryLinks(categoryPath, document)

            val leafCategories = categoryLinks.filterNot { candidate ->
                categoryLinks.any { other ->
                    other.first != candidate.first &&
                        other.first.startsWith(candidate.first.trimEnd('/') + "/")
                }
            }

            leafCategories.forEach { (href, title) ->
                val key = href.removePrefix(baseUrl.trimEnd('/'))
                categories.putIfAbsent(
                    key,
                    SManga.create().apply {
                        url = key
                        this.title = title
                    },
                )
            }

            // Some sections, such as Mangás, contain series directly in the
            // publisher archive instead of a dedicated subcategory.
            fetchArchiveEntries(categoryPath)
                .map { normalizeSeriesTitle(it.second) }
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase() }
                .forEach { seriesTitle ->
                    val key = "__series__/$categoryPath?q=${URLEncoder.encode(seriesTitle, "UTF-8")}"
                    val hasDedicatedCategory = categories.values.any { existing ->
                        if (existing.url.startsWith("__series__/")) {
                            decodeSeriesUrl(existing.url)?.second?.let { sameSeries(it, seriesTitle) } == true
                        } else {
                            sameSeries(existing.title, seriesTitle) ||
                                categorySlugMatches(existing.url, seriesTitle)
                        }
                    }
                    if (!hasDedicatedCategory) {
                        categories.putIfAbsent(
                            key,
                            SManga.create().apply {
                                url = key
                                title = seriesTitle
                            },
                        )
                    }
                }
        }

        val all = categories.values.sortedBy { it.title.lowercase() }
        val pageSize = 24
        val start = (page - 1).coerceAtLeast(0) * pageSize
        return MangasPage(all.drop(start).take(pageSize), all.size > start + pageSize)
    }

    private fun parseListing(document: org.jsoup.nodes.Document): MangasPage {
        val mangas = document.select(
            "article, .post, .elementor-post, .pagelayer-post, .entry",
        ).mapNotNull { card ->
            val link = card.selectFirst(
                "h1 a[href], h2 a[href], h3 a[href], .entry-title a[href], a[href*='jondomingues.com']",
            ) ?: card.selectFirst("a[href]")
            val href = link?.absUrl("href").orEmpty()
            if (href.isBlank() || !href.startsWith(baseUrl)) return@mapNotNull null

            val image = card.selectFirst("img")
            SManga.create().apply {
                url = href.removePrefix(baseUrl)
                title = link?.text().orEmpty().ifBlank {
                    card.selectFirst("h1, h2, h3")?.text().orEmpty()
                }.trim()
                thumbnail_url = image?.let {
                    firstNonBlank(
                        it.attr("abs:data-src"),
                        it.attr("abs:data-lazy-src"),
                        it.attr("abs:data-original"),
                        it.attr("abs:src"),
                        it.attr("abs:srcset").substringBefore(" "),
                    )
                }
            }.takeIf { it.title.isNotBlank() }
        }.distinctBy { it.url }

        val hasNext = document.selectFirst(
            "a.next[href], a[rel=next][href], .pagination a[href], .nav-links a[href]",
        ) != null
        return MangasPage(mangas, hasNext)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!fetchDetails && !fetchChapters) return SMangaUpdate(manga, chapters)

        val isSeries = manga.url.startsWith("/category/") || manga.url.startsWith("__series__/")
        val archiveUrl = if (manga.url.startsWith("__series__/")) {
            decodeSeriesUrl(manga.url)?.first?.let { "${baseUrl.trimEnd('/')}$it" }
        } else {
            getMangaUrl(manga)
        } ?: return SMangaUpdate(manga, emptyList())

        val document = client.get(archiveUrl, Fetch.DOCUMENT).asJsoup()
        val updatedManga = if (fetchDetails) {
            manga.apply {
                if (isSeries) {
                    description = document.selectFirst(".entry-content, .post-content, article")
                        ?.text()?.trim().orEmpty()
                    thumbnail_url = firstNonBlank(
                        document.selectFirst("meta[property=og:image]")?.attr("content").orEmpty(),
                        document.selectFirst(".entry-content img, article img")?.attr("abs:src").orEmpty(),
                    )
                    genre = document.select("a[rel=tag], .cat-links a").eachText().joinToString(", ")
                }
            }
        } else {
            manga
        }

        val updatedChapters = if (fetchChapters) {
            if (isSeries) parseSeriesChapters(manga) else parseSingleEdition(document)
        } else {
            chapters
        }

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        val imageUrls = document.select("img").mapNotNull { image ->
            firstNonBlank(
                image.attr("abs:data-src"),
                image.attr("abs:data-lazy-src"),
                image.attr("abs:data-original"),
                image.attr("abs:src"),
            ).takeIf { it.isNotBlank() && !it.startsWith("data:image") }
        }.filterNot { url ->
            val lower = url.lowercase()
            lower.contains("logo") || lower.contains("icon") ||
                lower.contains("avatar") || lower.contains("emoji")
        }.distinct()

        return imageUrls.mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    private fun parseSingleEdition(document: org.jsoup.nodes.Document): List<SChapter> {
        val readerUrl = findReaderUrl(document) ?: return emptyList()

        return listOf(
            SChapter.create().apply {
                name = "Capítulo 1"
                url = readerUrl.removePrefix(baseUrl)
                chapter_number = 1f
            },
        )
    }

    private suspend fun parseSeriesChapters(manga: SManga): List<SChapter> {
        val seriesData = if (manga.url.startsWith("__series__/")) {
            decodeSeriesUrl(manga.url)
        } else {
            null
        }

        val archivePath = seriesData?.first ?: manga.url
        val expectedSeries = seriesData?.second
        // A dedicated category is the authoritative boundary of a series. Do not
        // filter its posts by the displayed title: the site mixes translated and
        // original names (for example, "Absolute Batman" / "Batman Absoluto").
        val editions = fetchArchiveEntries(archivePath)
            .filter { (_, title) ->
                expectedSeries == null || sameSeries(normalizeSeriesTitle(title), expectedSeries)
            }
            .distinctBy { normalizeUrl(it.first) }
            .sortedWith(
                compareBy<Pair<String, String>> { editionNumber(it.second) ?: Float.MAX_VALUE }
                    .thenBy { it.second.lowercase(Locale.ROOT) },
            )

        val chapters = mutableListOf<SChapter>()
        editions.forEach { (editionUrl, _) ->
            val edition = client.get(editionUrl, Fetch.DOCUMENT).asJsoup()
            val readerUrl = findReaderUrl(edition) ?: return@forEach

            chapters += SChapter.create().apply {
                url = readerUrl.removePrefix(baseUrl)
            }
        }

        return chapters.distinctBy { normalizeUrl(it.url) }
            .mapIndexed { index, chapter ->
                chapter.apply {
                    name = "Capítulo ${index + 1}"
                    chapter_number = (index + 1).toFloat()
                }
            }
    }

    private suspend fun discoverPublisherCategories(): List<String> {
        val document = client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
        return document.select("a[href*='/category/']").mapNotNull { link ->
            val href = normalizeUrl(link.absUrl("href"))
            val path = href.removePrefix(baseUrl.trimEnd('/'))
            val segments = path.trim('/').split('/')
            if (href.startsWith("${baseUrl.trimEnd('/')}/category/") &&
                segments.size == 2 &&
                segments.firstOrNull() == "category"
            ) path else null
        }.distinct()
    }

    private suspend fun fetchArchiveCategoryLinks(
        categoryPath: String,
        firstPage: org.jsoup.nodes.Document,
    ): List<Pair<String, String>> {
        val results = linkedMapOf<String, String>()
        val normalizedPath = categoryPath.trimEnd('/') + "/"
        val parentUrl = "${baseUrl.trimEnd('/')}/${normalizedPath.trim('/')}"
        for (page in 1..30) {
            val document = if (page == 1) firstPage else {
                val pageUrl = "${baseUrl.trimEnd('/')}${normalizedPath.trimEnd('/')}/page/$page/"
                client.get(pageUrl, Fetch.DOCUMENT).asJsoup()
            }
            document.select("a[href*='/category/']").forEach { link ->
                val href = normalizeUrl(link.absUrl("href"))
                val title = link.text().trim()
                if (title.isNotBlank() &&
                    href.startsWith(parentUrl.trimEnd('/') + "/")
                ) {
                    results.putIfAbsent(href, title)
                }
            }
            val hasNext = document.select("a[href]").any { link ->
                link.attr("rel").equals("next", ignoreCase = true) ||
                    link.hasClass("next") ||
                    link.text().contains("Próxima", ignoreCase = true) ||
                    link.text().contains("Next", ignoreCase = true)
            }
            if (!hasNext) break
        }
        val all = results.toList()
        return all.filterNot { candidate ->
            all.any { other ->
                other.first != candidate.first &&
                    other.first.startsWith(candidate.first.trimEnd('/') + "/")
            }
        }
    }

    private fun findReaderUrl(document: org.jsoup.nodes.Document): String? {
        val contentLinks = document.select(
            ".entry-content a[href], .post-content a[href], article a[href], main a[href]",
        )
        val preferred = contentLinks.firstOrNull { link ->
            val href = normalizeUrl(link.absUrl("href"))
            val label = link.text().trim()
            href.startsWith("${baseUrl.trimEnd('/')}/") &&
                (label.contains("ler online", ignoreCase = true) ||
                    label.equals("ler", ignoreCase = true)) &&
                (href.contains("/ler-online-") || href.contains("/ler-online/"))
        }
        if (preferred != null) return normalizeUrl(preferred.absUrl("href"))

        return contentLinks.firstOrNull { link ->
            val href = normalizeUrl(link.absUrl("href"))
            href.startsWith("${baseUrl.trimEnd('/')}/") &&
                (href.contains("/ler-online-") || href.contains("/ler-online/"))
        }?.absUrl("href")?.let(::normalizeUrl)
    }

    private fun editionNumber(title: String): Float? {
        val issue = Regex("(?i)#\\s*(\\d+(?:[.,]\\d+)?)").find(title)?.groupValues?.get(1)
        val volume = Regex("(?i)\\b(?:volume|vol\\.?)\\s*(\\d+)").find(title)?.groupValues?.get(1)
        return (issue ?: volume)?.replace(',', '.')?.toFloatOrNull()
    }

    private fun normalizedKey(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    private fun sameSeries(first: String, second: String): Boolean {
        val firstYear = Regex("\\((\\d{4})\\)").find(first)?.groupValues?.get(1)
        val secondYear = Regex("\\((\\d{4})\\)").find(second)?.groupValues?.get(1)
        val a = normalizedKey(first.replace(Regex("\\s*\\(\\d{4}\\)\\s*$"), ""))
        val b = normalizedKey(second.replace(Regex("\\s*\\(\\d{4}\\)\\s*$"), ""))
        if (firstYear != null && secondYear != null && firstYear != secondYear &&
            !(a == "the boys" && b == "the boys")
        ) return false
        if (a == b) return true
        val aliases = listOf(
            setOf("absolute batman", "batman absoluto"),
        )
        return aliases.any { a in it && b in it }
    }

    private fun categorySlugMatches(categoryUrl: String, title: String): Boolean {
        val slug = categoryUrl.substringBefore('?').trimEnd('/').substringAfterLast('/')
        val baseTitle = title.replace(Regex("(?i)\\s*\\(\\d{4}\\)\\s*$"), "").trim()
        val titleSlug = normalizedKey(baseTitle).replace(' ', '-')
        if (slug.equals(titleSlug, ignoreCase = true)) return true
        return sameSeries(slug.replace('-', ' '), baseTitle)
    }

    private suspend fun fetchArchiveEntries(categoryPath: String): List<Pair<String, String>> {
        val results = linkedMapOf<String, String>()
        val normalizedPath = if (categoryPath.startsWith("http")) {
            normalizeUrl(categoryPath).removePrefix(baseUrl.trimEnd('/'))
        } else {
            categoryPath
        }

        for (page in 1..30) {
            val pageUrl = if (page == 1) {
                "${baseUrl.trimEnd('/')}$normalizedPath"
            } else {
                "${baseUrl.trimEnd('/')}${normalizedPath.trimEnd('/')}/page/$page/"
            }
            val document = client.get(pageUrl, Fetch.DOCUMENT).asJsoup()
            val previousCount = results.size
            parseEditionLinks(document).forEach { (url, title) ->
                results.putIfAbsent(url, title)
            }
            if (page > 1 && results.size == previousCount) break

            val hasNext = document.select("a[href]").any { link ->
                link.attr("rel").equals("next", ignoreCase = true) ||
                    link.hasClass("next") ||
                    link.text().contains("Próxima", ignoreCase = true) ||
                    link.text().contains("Next", ignoreCase = true)
            } || document.select("button, [role=button]").any { control ->
                control.text().contains("Carregar mais", ignoreCase = true)
            }
            if (!hasNext) break
        }

        return results.map { (url, title) -> url to title }
    }

    private fun parseEditionLinks(document: org.jsoup.nodes.Document): List<Pair<String, String>> =
        document.select("a[href]").mapNotNull { link ->
            val href = normalizeUrl(link.absUrl("href"))
            val label = link.text().trim().ifBlank {
                link.selectFirst("img")?.let { image ->
                    firstNonBlank(
                        image.attr("alt"),
                        image.attr("title"),
                        link.attr("aria-label"),
                    )
                }.orEmpty()
            }
            if (!href.startsWith("${baseUrl.trimEnd('/')}/") ||
                href.contains("/category/") ||
                href.contains("/page/") ||
                href.contains("/ler-online-") ||
                href.contains("/wp-") ||
                label.isBlank()
            ) {
                return@mapNotNull null
            }

            val isEditionLink = label.startsWith("Ler", ignoreCase = true) ||
                label.startsWith("Capa", ignoreCase = true) ||
                link.parents().any { it.normalName() in listOf("h1", "h2", "h3") }
            if (!isEditionLink) return@mapNotNull null

            val title = label
                .replace(Regex("(?i)^ler\\s*/?\\s*baixar\\s*"), "")
                .replace(Regex("(?i)^ler\\s+"), "")
                .replace(Regex("(?i)^capa(?:\\s+da\\s+hq|\\s+de)?\\s*:?\\s*"), "")
                .replace(Regex("(?i)\\s*[–-]\\s*ler online.*$"), "")
                .replace(Regex("(?i)\\s*[–-]\\s*download.*$"), "")
                .trim()
                .ifBlank { label }

            href to title
        }.distinctBy { it.first }

    private fun decodeSeriesUrl(url: String): Pair<String, String>? {
        val prefix = "__series__/"
        if (!url.startsWith(prefix)) return null
        val payload = url.removePrefix(prefix)
        val queryIndex = payload.indexOf("?q=")
        if (queryIndex < 0) return null
        val categoryPath = payload.substring(0, queryIndex)
        val title = URLDecoder.decode(payload.substring(queryIndex + 3), "UTF-8")
        return categoryPath to title
    }

    private fun normalizeSeriesTitle(title: String): String {
        val cleaned = title
            .replace(Regex("(?i)^ler\\s*/?\\s*baixar\\s*"), "")
            .replace(Regex("(?i)\\s*[–-]\\s*ler online.*$"), "")
            .trim()
        // Keep the publication year so separate relaunches (e.g. Justiceiro
        // 2022 and Justiceiro 2026) never become one mixed series.
        val year = Regex("\\((\\d{4})\\)").find(cleaned)?.groupValues?.get(1)
        val base = cleaned
            .replace(Regex("(?i)\\s*(?:#\\s*\\d+|(?:volume|vol\\.?)\\s*\\d+)\\b.*$"), "")
            .replace(Regex("\\s*\\(\\d{4}\\)\\s*$"), "")
            .trim()
            .ifBlank { title.trim() }
        return if (year != null) "$base ($year)" else base
    }

    private fun normalizeUrl(url: String): String =
        url.substringBefore("#").substringBefore("?").trimEnd('/').let { "$it/" }

    private fun firstNonBlank(vararg values: String): String =
        values.firstOrNull { it.isNotBlank() }.orEmpty()
}
