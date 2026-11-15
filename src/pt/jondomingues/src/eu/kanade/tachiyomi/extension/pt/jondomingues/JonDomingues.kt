package eu.kanade.tachiyomi.extension.pt.jondomingues

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Protótipo inicial para jondomingues.com.
 *
 * Os seletores são deliberadamente tolerantes porque o site usa WordPress/
 * construtor visual. Devem ser refinados após inspeção do HTML recebido
 * diretamente pelo OkHttp dentro do ambiente de extensão.
 */
class JonDomingues : ParsedHttpSource() {

    override val name = "Jon Domingues"
    override val baseUrl = "https://jondomingues.com"
    override val lang = "pt-BR"
    override val supportsLatest = true

    override fun popularMangaRequest(page: Int) =
        GET(if (page == 1) "$baseUrl/" else "$baseUrl/page/$page/")

    override fun latestUpdatesRequest(page: Int) =
        GET(if (page == 1) "$baseUrl/" else "$baseUrl/page/$page/")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        GET(baseUrl.toHttpUrl().newBuilder().addQueryParameter("s", query).build().toString())

    override fun popularMangaSelector(): String =
        "article, .post, .elementor-post, .pagelayer-post"

    override fun latestUpdatesSelector(): String = popularMangaSelector()

    override fun searchMangaSelector(): String = popularMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga = mangaFromCard(element)

    override fun latestUpdatesFromElement(element: Element): SManga = mangaFromCard(element)

    override fun searchMangaFromElement(element: Element): SManga = mangaFromCard(element)

    private fun mangaFromCard(element: Element): SManga {
        val link = element.selectFirst("h1 a, h2 a, h3 a, .entry-title a, a[href*='jondomingues.com']")
            ?: element.selectFirst("a[href]")

        val image = element.selectFirst("img")
        return SManga.create().apply {
            title = (link?.text()?.takeIf { it.isNotBlank() }
                ?: element.selectFirst("h1, h2, h3")?.text()
                ?: "Edição sem título").trim()
            url = link?.absUrl("href")?.removePrefix(baseUrl).orEmpty()
            thumbnail_url = image?.let {
                firstNonBlank(
                    it.absUrl("data-src"),
                    it.absUrl("data-lazy-src"),
                    it.absUrl("data-original"),
                    it.absUrl("src"),
                    it.absUrl("srcset").substringBefore(" ")
                )
            }
        }
    }

    override fun popularMangaNextPageSelector(): String =
        "a.next, a[rel=next], .pagination a, .nav-links a"

    override fun latestUpdatesNextPageSelector(): String = popularMangaNextPageSelector()

    override fun searchMangaNextPageSelector(): String = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.title = document.selectFirst("h1")?.text()?.trim()
            ?: document.title().substringBefore(" – ").trim()
        manga.author = document.selectFirst(
            ".entry-content li:contains(Roteiro), .entry-content li:contains(arte), .entry-content li:contains(Autor)"
        )?.text()
        manga.description = document.selectFirst(".entry-content")?.text()?.trim()
        manga.thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
            ?: document.selectFirst(".entry-content img, article img")?.absUrl("src")
        manga.status = SManga.UNKNOWN
        manga.genre = document.select("a[rel=tag], .cat-links a").eachText().joinToString(", ")
        return manga
    }

    override fun chapterListSelector(): String =
        "a[href*='/ler-online-'], a[href*='ler-online']"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.name = element.text().ifBlank { "Ler online" }.trim()
        chapter.url = element.absUrl("href").removePrefix(baseUrl)
        return chapter
    }

    override fun pageListParse(document: Document): List<Page> {
        val images = document.select("img")
            .mapNotNull { image ->
                val url = firstNonBlank(
                    image.absUrl("data-src"),
                    image.absUrl("data-lazy-src"),
                    image.absUrl("data-original"),
                    image.absUrl("src")
                )
                if (url.isBlank() || url.startsWith("data:image")) null else url
            }
            .distinct()
            .filterNot { url ->
                val lower = url.lowercase()
                lower.contains("logo") || lower.contains("icon") || lower.contains("avatar")
            }

        return images.mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    override fun imageUrlParse(document: Document): String =
        document.selectFirst("meta[property=og:image]")?.attr("content")
            ?: document.selectFirst("img")?.absUrl("src").orEmpty()

    private fun firstNonBlank(vararg values: String): String =
        values.firstOrNull { it.isNotBlank() }.orEmpty()
}
