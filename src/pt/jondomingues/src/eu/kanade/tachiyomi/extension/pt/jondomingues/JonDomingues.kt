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

@Source
abstract class JonDomingues : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = fetchListing(if (page == 1) baseUrl else "$baseUrl/page/$page/")

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchListing(if (page == 1) baseUrl else "$baseUrl/page/$page/")

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

    private suspend fun fetchListing(url: String): MangasPage = parseListing(client.get(url, Fetch.DOCUMENT).asJsoup())

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

        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val updatedManga = if (fetchDetails) {
            manga.apply {
                title = document.selectFirst("h1")?.text()?.trim().orEmpty().ifBlank { title }
                description = document.selectFirst(
                    ".entry-content, .post-content, article",
                )?.text()?.trim()
                thumbnail_url = firstNonBlank(
                    document.selectFirst("meta[property=og:image]")?.attr("content").orEmpty(),
                    document.selectFirst(".entry-content img, article img")?.attr("abs:src").orEmpty(),
                )
                genre = document.select("a[rel=tag], .cat-links a").eachText().joinToString(", ")
            }
        } else {
            manga
        }

        val updatedChapters = if (fetchChapters) {
            document.select("a[href*='/ler-online-'], a[href*='ler-online']").map { link ->
                SChapter.create().apply {
                    name = link.text().ifBlank { "Ler online" }.trim()
                    url = link.absUrl("href").removePrefix(baseUrl)
                }
            }.distinctBy { it.url }
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

    private fun firstNonBlank(vararg values: String): String =
        values.firstOrNull { it.isNotBlank() }.orEmpty()
}
