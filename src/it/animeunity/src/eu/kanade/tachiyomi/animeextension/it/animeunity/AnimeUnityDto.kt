package eu.kanade.tachiyomi.animeextension.it.animeunity

import eu.kanade.tachiyomi.animesource.model.SAnime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The site is an Inertia app: every page embeds `{ "props": { ... } }` in `#app[data-page]`.

@Serializable
class InertiaPage<T>(
    val props: T,
)

@Serializable
class ArchiveProps(
    val animes: AnimePage,
)

@Serializable
class AnimePage(
    @SerialName("current_page") val currentPage: Int,
    @SerialName("last_page") val lastPage: Int,
    val data: List<AnimeDto>,
)

@Serializable
class AnimeProps(
    val anime: AnimeDto,
    val loadedEpisodes: LoadedEpisodes? = null,
    val embedUrl: String? = null,
)

@Serializable
class AnimeDto(
    val id: Int,
    val slug: String,
    val title: String,
    @SerialName("title_it") val titleIt: String? = null,
    val type: String? = null,
    val season: String? = null,
    val year: Int? = null,
    val score: String? = null,
    val plot: String? = null,
    @SerialName("default_status") val defaultStatus: String? = null,
    val images: Images? = null,
    val variants: List<Variant> = emptyList(),
    val genres: List<NamedDto> = emptyList(),
    val studios: List<NamedDto> = emptyList(),
) {
    @Serializable
    class Images(
        val poster: String? = null,
        val cover: String? = null,
    )

    @Serializable
    class Variant(
        val language: String,
    )

    @Serializable
    class NamedDto(
        val name: String,
    )

    // The site has one entry per anime with an `ita` (dubbed) and a `jpn` (subtitled) variant.
    // They are exposed as two entries so that each one can be saved separately in the library.
    fun toSAnimeList(): List<SAnime> {
        val hasDub = variants.any { it.language == "ita" }
        val hasSub = variants.isEmpty() || variants.any { it.language != "ita" }
        return buildList {
            if (hasSub || !hasDub) add(toSAnime(dub = false))
            if (hasDub && hasSub) add(toSAnime(dub = true))
        }
    }

    private fun toSAnime(dub: Boolean) = SAnime.create().apply {
        url = "$id-$slug" + if (dub) DUB_SUFFIX else ""
        title = this@AnimeDto.title + if (dub) " (ITA)" else ""
        thumbnail_url = images?.poster ?: images?.cover
    }
}

const val DUB_SUFFIX = "#ita"

@Serializable
class LoadedEpisodes(
    val episodes: List<EpisodeDto>,
    val start: Int,
    @SerialName("tab_size") val tabSize: Int,
    val total: Int,
    val offset: Int,
)

@Serializable
class EpisodesApiResponse(
    val episodes: List<EpisodeDto>,
)

@Serializable
class EpisodeDto(
    val number: String,
    val videos: List<VideoDto>,
) {
    @Serializable
    class VideoDto(
        val id: Int,
        val language: String,
        @SerialName("is_hidden") val isHidden: Boolean = false,
    )
}
