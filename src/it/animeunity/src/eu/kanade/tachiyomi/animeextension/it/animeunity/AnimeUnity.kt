package eu.kanade.tachiyomi.animeextension.it.animeunity

import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.utils.AnimeHttpLegacySource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document

@Source
abstract class AnimeUnity :
    AnimeHttpLegacySource(),
    ConfigurableAnimeSource {

    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    private val playlistRegex = Regex("""https?://[^/\s]+/playlist/(.+)\?.*type=video&.*rendition=(\d+p?).*""")

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int): Request = GET("$baseUrl/top-anime?page=$page", headers = headers)

    override fun popularAnimeParse(response: Response): AnimesPage {
        val animes = response.asJsoup().inertiaProps<ArchiveProps>().animes
        return AnimesPage(animes.data.flatMap { it.toSAnimeList() }, animes.currentPage < animes.lastPage)
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/archive?sort_by=latest&status=ongoing&page=$page", headers = headers)

    override fun latestUpdatesParse(response: Response): AnimesPage = popularAnimeParse(response)

    // =============================== Search ===============================

    override fun searchAnimeRequest(
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): Request {
        val url = "$baseUrl/archive".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("query", query.trim())
            AnimeUnityFilters.addTo(this, filters)
            addQueryParameter("page", page.toString())
        }.build()
        return GET(url, headers)
    }

    override fun searchAnimeParse(response: Response): AnimesPage = popularAnimeParse(response)

    override fun getFilterList(): AnimeFilterList = AnimeUnityFilters.FILTER_LIST

    // =========================== Anime Details ============================

    override fun animeDetailsRequest(anime: SAnime): Request = GET("$baseUrl/anime/${anime.url.substringBefore(DUB_SUFFIX)}", headers)

    override suspend fun getAnimeDetails(anime: SAnime): SAnime = client
        .newCall(animeDetailsRequest(anime))
        .awaitSuccess()
        .use { animeDetailsParse(it) }
        .apply {
            url = anime.url
            if (anime.url.endsWith(DUB_SUFFIX)) title += " (ITA)"
        }

    override fun animeDetailsParse(response: Response): SAnime {
        val anime = response.asJsoup().inertiaProps<AnimeProps>().anime
        return SAnime.create().apply {
            title = anime.title
            thumbnail_url = anime.images?.poster ?: anime.images?.cover
            status = parseStatus(anime.defaultStatus)
            artist = anime.studios.joinToString { it.name }
            genre = anime.genres.joinToString { it.name }
            description =
                buildString {
                    anime.plot?.let { append(it) }
                    anime.type?.let { append("\n\nTipo: ${it.uppercase()}") }
                    if (anime.season != null || anime.year != null) {
                        append("\nStagione: ${anime.season.orEmpty()} ${anime.year ?: ""}".trimEnd())
                    }
                    append("\nValutazione: ★${anime.score ?: "-"}")
                }
        }
    }

    // ============================== Episodes ==============================

    override fun episodeListRequest(anime: SAnime): Request = GET("$baseUrl/anime/${anime.url.substringBefore(DUB_SUFFIX)}", headers)

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        val dub = anime.url.endsWith(DUB_SUFFIX)
        val props = client
            .newCall(episodeListRequest(anime))
            .awaitSuccess()
            .use { it.asJsoup().inertiaProps<AnimeProps>() }
        val animePath = "${props.anime.id}-${props.anime.slug}"
        val loaded = props.loadedEpisodes ?: return emptyList()

        val episodes = loaded.episodes.toMutableList()
        var start = loaded.start + loaded.tabSize
        while (loaded.tabSize > 0 && start < loaded.offset + loaded.total) {
            val api = "$baseUrl/api/anime/${props.anime.id}/episodes?start=$start"
            val tab = client.newCall(GET(api, headers)).awaitSuccess().use { it.parseAs<EpisodesApiResponse>() }
            if (tab.episodes.isEmpty()) break
            episodes.addAll(tab.episodes)
            start += loaded.tabSize
        }

        return episodes
            .mapNotNull { episode ->
                val videos = episode.videos.filterNot { it.isHidden }
                val video =
                    if (dub) {
                        videos.firstOrNull { it.language == "ita" }
                    } else {
                        videos.firstOrNull { it.language != "ita" } ?: videos.firstOrNull()
                    }
                video?.let {
                    SEpisode.create().apply {
                        name = "Episodio ${episode.number}"
                        episode_number = episode.number.substringBefore("-").toFloatOrNull() ?: 0F
                        url = "$animePath/${it.id}"
                    }
                }
            }.sortedByDescending { it.episode_number }
    }

    override fun episodeListParse(response: Response): List<SEpisode> = throw UnsupportedOperationException()

    // ============================ Video Links =============================

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val videoList = videosForVideoId(episode.url)
        require(videoList.isNotEmpty()) { "Failed to fetch videos" }
        return videoList
    }

    private suspend fun videosForVideoId(path: String): List<Video> {
        val page =
            client
                .newCall(GET("$baseUrl/anime/$path", headers))
                .awaitSuccess()
                .use { it.asJsoup() }
        val iframeUrl = page.inertiaProps<AnimeProps>().embedUrl ?: error("Failed to extract iframe")

        val iframeHeaders =
            headers
                .newBuilder()
                .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .add("Referer", "$baseUrl/")
                .build()
        val iframe =
            client
                .newCall(GET(iframeUrl, headers = iframeHeaders))
                .awaitSuccess()
                .use { it.asJsoup() }

        val script =
            iframe
                .select("script")
                .find { it.data().contains("masterPlaylist") }
                ?.data()
                ?.replace("\n", "\t")
                ?: error("Failed to find the master playlist")
        val playlistUrl = Regex("""url: ?'(.*?)'""").find(script)!!.groupValues[1]
        val expires = Regex("""'expires': ?'(\d+)'""").find(script)!!.groupValues[1]
        val token = Regex("""'token': ?'([\w-]+)'""").find(script)!!.groupValues[1]

        val masterPlUrl = buildString {
            append(playlistUrl)
            append(if (playlistUrl.contains('?')) '&' else '?')
            append("h=1&token=")
            append(token)
            append("&expires=")
            append(expires)
        }
        val masterPl =
            client
                .newCall(GET(masterPlUrl))
                .awaitSuccess()
                .use { it.body.string() }

        return playlistRegex.findAll(masterPl).map { match ->
            var videoUrl = match.groupValues[0]
            val filename = match.groupValues[1]
            if (!filename.endsWith(".m3u8")) {
                videoUrl = videoUrl.replace(filename, "$filename.m3u8")
            }
            val quality = match.groupValues[2].removeSuffix("p")
            Video(videoUrl = videoUrl, videoTitle = "${quality}p")
        }.toList()
    }

    override fun videoListRequest(episode: SEpisode): Request = throw UnsupportedOperationException()

    override fun videoListParse(response: Response): List<Video> = throw UnsupportedOperationException()

    // ============================= Utilities ==============================

    private inline fun <reified T> Document.inertiaProps(): T = selectFirst("#app")!!
        .attr("data-page")
        .parseAs<InertiaPage<T>>()
        .props

    private fun parseStatus(status: String?): Int = when (status) {
        "ongoing" -> SAnime.ONGOING
        "completed" -> SAnime.COMPLETED
        else -> SAnime.UNKNOWN
    }

    override fun List<Video>.sortVideos(): List<Video> {
        val quality = preferences.getString(PREF_QUALITY_KEY, PREF_QUALITY_DEFAULT)!!

        return this
            .sortedWith(
                compareBy(
                    { it.videoTitle.contains(quality) },
                    { it.videoTitle.substringBefore("p").toIntOrNull() ?: 0 },
                ),
            ).reversed()
    }

    companion object {
        private const val PREF_QUALITY_KEY = "preferred_quality"
        private const val PREF_QUALITY_DEFAULT = "1080"
    }

    // ============================== Settings ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context)
            .apply {
                key = PREF_QUALITY_KEY
                title = "Preferred quality"
                entries = arrayOf("1080p", "720p", "480p", "360p", "240p", "80p")
                entryValues = arrayOf("1080", "720", "480", "360", "240", "80")
                setDefaultValue(PREF_QUALITY_DEFAULT)
                summary = "%s"

                setOnPreferenceChangeListener { _, newValue ->
                    val selected = newValue as String
                    val index = findIndexOfValue(selected)
                    val entry = entryValues[index] as String
                    preferences.edit().putString(key, entry).commit()
                }
            }.also(screen::addPreference)
    }
}
