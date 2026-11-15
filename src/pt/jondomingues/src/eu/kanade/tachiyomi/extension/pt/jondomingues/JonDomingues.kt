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

@Source
abstract class JonDomingues : KeiSource() {

    private val publisherCategories = listOf(
        "/category/dc-comics/",
        "/category/dynamite-entertainment/",
        "/category/idw-publishing/",
        "/category/image-comics/",
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

        publisherCategories.forEach { categoryPath ->
            val document = client.get("${baseUrl.trimEnd('/')}$categoryPath", Fetch.DOCUMENT).asJsoup()
            val categoryLinks = document.select("a[href*='/category/']").mapNotNull { link ->
                val href = normalizeUrl(link.absUrl("href"))
                val title = link.text().trim()
                if (title.isBlank() || !href.startsWith("${baseUrl.trimEnd('/')}/category/")) {
                    return@mapNotNull null
                }
                if (href.trimEnd('/') == "${baseUrl.trimEnd('/')}$categoryPath".trimEnd('/')) {
                    return@mapNotNull null
                }
                href to title
            }.distinctBy { it.first }

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
                    val hasDedicatedCategory = categories.values.any {
                        it.title.equals(seriesTitle, ignoreCase = true)
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
        val readerUrl = document.select("a[href*='/ler-online-'], a[href*='/ler-online/']")
            .map { it.absUrl("href") }
            .firstOrNull { it.isNotBlank() && it.startsWith(baseUrl) }
            ?: return emptyList()

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
        val editions = fetchArchiveEntries(archivePath)
            .filter { (_, title) ->
                expectedSeries == null || normalizeSeriesTitle(title).equals(expectedSeries, ignoreCase = true)
            }

        val chapters = mutableListOf<SChapter>()
        editions.forEach { (editionUrl, _) ->
            val edition = client.get(editionUrl, Fetch.DOCUMENT).asJsoup()
            val readerUrl = edition.select("a[href*='/ler-online-'], a[href*='/ler-online/']")
                .map { it.absUrl("href") }
                .firstOrNull { it.isNotBlank() && it.startsWith(baseUrl) }
                ?: return@forEach

            chapters += SChapter.create().apply {
                name = "Capítulo ${chapters.size + 1}"
                url = readerUrl.removePrefix(baseUrl)
                chapter_number = (chapters.size + 1).toFloat()
            }
        }

        return chapters.distinctBy { it.url }
            .mapIndexed { index, chapter ->
                chapter.apply {
                    name = "Capítulo ${index + 1}"
                    chapter_number = (index + 1).toFloat()
                }
            }
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
            parseEditionLinks(document).forEach { (url, title) ->
                results.putIfAbsent(url, title)
            }

            val hasNext = document.select("a[href]").any { link ->
                link.attr("rel").equals("next", ignoreCase = true) ||
                    link.hasClass("next") ||
                    link.text().contains("Próxima", ignoreCase = true) ||
                    link.text().contains("Next", ignoreCase = true)
            }
            if (!hasNext) break
        }

        return results.map { (url, title) -> url to title }
    }

    private fun parseEditionLinks(document: org.jsoup.nodes.Document): List<Pair<String, String>> =
        document.select("a[href]").mapNotNull { link ->
            val href = normalizeUrl(link.absUrl("href"))
            val label = link.text().trim()
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
                link.parents().any { it.normalName() in listOf("h1", "h2", "h3") }
            if (!isEditionLink) return@mapNotNull null

            val title = label
                .replace(Regex("(?i)^ler\\s*/?\\s*baixar\\s*"), "")
                .replace(Regex("(?i)^ler\\s+"), "")
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

    private fun normalizeSeriesTitle(title: String): String = title
        .replace(Regex("(?i)^ler\\s*/?\\s*baixar\\s*"), "")
        .replace(Regex("(?i)\\s*(?:#\\s*\\d+|(?:volume|vol\\.?)\\s*\\d+)\\b.*$"), "")
        .replace(Regex("(?i)\\s*[–-]\\s*ler online.*$"), "")
        .replace(Regex("(?i)\\s*\\(\\d{4}\\)\\s*$"), "")
        .trim()
        .ifBlank { title.trim() }

    private fun normalizeUrl(url: String): String =
        url.substringBefore("#").substringBefore("?").trimEnd('/').let { "$it/" }

    private fun firstNonBlank(vararg values: String): String =
        values.firstOrNull { it.isNotBlank() }.orEmpty()
}
