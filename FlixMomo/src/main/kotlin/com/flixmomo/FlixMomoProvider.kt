package com.flixmomo

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class FlixMomoProvider : MainAPI() {

    override var mainUrl = "https://flixmomo.org"
    override var name = "FlixMomo"
    override var lang = "en"
    override var supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var hasMainPage = false

    // ================= SEARCH =================
    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/search?keyword=${query.replace(" ", "+")}").document
        return doc.select(".movie-item").mapNotNull { it.toSearch() }
    }

    // ================= LOAD =================
    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1")?.text()?.trim() ?: "Unknown"
        val poster = doc.selectFirst("img[src]")?.attr("src")

        return if (url.contains("/movie/")) {
            MovieLoadResponse(
                title,
                url,
                name,
                TvType.Movie,
                url
            ).apply {
                posterUrl = poster
            }
        } else {
            val episodes = mutableListOf<Episode>()

            doc.select("a[href*=/season/]").distinctBy { it.attr("href") }.forEach { season ->
                val seasonHref = season.attr("href")
                if (seasonHref.isBlank()) return@forEach
                
                val seasonNum = seasonHref
                    .substringAfter("/season/")
                    .substringBefore("?")
                    .toIntOrNull() ?: 1

                runCatching {
                    val seasonDoc = app.get(mainUrl + seasonHref).document
                    seasonDoc.select("a[href*=?e=]").forEach { ep ->
                        val epHref = ep.attr("href")
                        if (epHref.isBlank()) return@forEach
                        
                        val epNum = epHref
                            .substringAfter("e=")
                            .substringBefore("&")
                            .toIntOrNull() ?: return@forEach

                        episodes.add(
                            Episode(
                                mainUrl + epHref,
                                episode = epNum,
                                season = seasonNum
                            )
                        )
                    }
                }
            }

            TvSeriesLoadResponse(
                title,
                url,
                name,
                TvType.TvSeries,
                episodes.distinctBy { it.data }
            ).apply {
                posterUrl = poster
            }
        }
    }

    // ================= VIDEO LINKS =================
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val doc = app.get(data).document

        doc.select("a[data-player]").forEach { player ->
            val playerUrl = player.attr("data-player")
            if (playerUrl.isBlank()) return@forEach
            
            runCatching {
                val playerDoc = app.get(playerUrl).document
                playerDoc.select("source[src]").forEach { src ->
                    val videoUrl = src.attr("src")
                    if (videoUrl.isNotBlank() && videoUrl.endsWith(".mp4")) {
                        callback(
                            ExtractorLink(
                                name,
                                qualityFromName(videoUrl),
                                videoUrl,
                                "",
                                quality = getQuality(videoUrl)
                            )
                        )
                    }
                }
            }
        }
        return true
    }

    // ================= HELPERS =================
    private fun Element.toSearch(): SearchResponse? {
        val link = selectFirst("a")?.attr("href") ?: return null
        if (link.isBlank()) return null
        
        val title = selectFirst(".title")?.text() ?: return null
        val poster = selectFirst("img")?.attr("src")
        val type = if (link.contains("/tv/")) TvType.TvSeries else TvType.Movie

        return MovieSearchResponse(
            title,
            mainUrl + link,
            name,
            type,
            poster
        )
    }

    private fun getQuality(url: String): Int = when {
        url.contains("2160", ignoreCase = true) -> Qualities.P2160.value
        url.contains("1440", ignoreCase = true) -> Qualities.P1440.value
        url.contains("1080", ignoreCase = true) -> Qualities.P1080.value
        url.contains("720", ignoreCase = true) -> Qualities.P720.value
        url.contains("480", ignoreCase = true) -> Qualities.P480.value
        url.contains("360", ignoreCase = true) -> Qualities.P360.value
        else -> Qualities.Unknown.value
    }

    private fun qualityFromName(url: String): String = when {
        url.contains("2160", ignoreCase = true) -> "4K"
        url.contains("1440", ignoreCase = true) -> "1440p"
        url.contains("1080", ignoreCase = true) -> "1080p"
        url.contains("720", ignoreCase = true) -> "720p"
        url.contains("480", ignoreCase = true) -> "480p"
        url.contains("360", ignoreCase = true) -> "360p"
        else -> "Unknown"
    }
}