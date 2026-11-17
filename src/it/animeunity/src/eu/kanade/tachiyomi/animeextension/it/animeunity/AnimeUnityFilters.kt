package eu.kanade.tachiyomi.animeextension.it.animeunity

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import keiyoushi.utils.firstInstance
import okhttp3.HttpUrl

object AnimeUnityFilters {

    open class QueryPartFilter(
        displayName: String,
        private val param: String,
        private val vals: Array<Pair<String, String>>,
    ) : AnimeFilter.Select<String>(
        displayName,
        vals.map { it.first }.toTypedArray(),
    ) {
        fun addTo(builder: HttpUrl.Builder) {
            val value = vals[state].second
            if (value.isNotEmpty()) builder.addQueryParameter(param, value)
        }
    }

    class SortFilter : QueryPartFilter("Ordina per", "sort_by", SORT)

    class TypeFilter : QueryPartFilter("Tipo", "type", TYPE)

    class StatusFilter : QueryPartFilter("Stato", "status", STATUS)

    class SeasonFilter : QueryPartFilter("Stagione", "season", SEASON)

    class LanguageFilter : QueryPartFilter("Lingua", "language", LANGUAGE)

    class YearFromFilter : QueryPartFilter("Anno da", "year_from", YEARS)

    class YearToFilter : QueryPartFilter("Anno a", "year_to", YEARS)

    class GenreFilter :
        AnimeFilter.Group<AnimeFilter.CheckBox>(
            "Generi",
            GENRES.map { object : AnimeFilter.CheckBox(it.first, false) {} },
        )

    val FILTER_LIST
        get() = AnimeFilterList(
            SortFilter(),
            TypeFilter(),
            StatusFilter(),
            SeasonFilter(),
            LanguageFilter(),
            YearFromFilter(),
            YearToFilter(),
            AnimeFilter.Separator(),
            GenreFilter(),
        )

    fun addTo(
        builder: HttpUrl.Builder,
        filters: AnimeFilterList,
    ) {
        if (filters.isEmpty()) return
        filters.firstInstance<SortFilter>().addTo(builder)
        filters.firstInstance<TypeFilter>().addTo(builder)
        filters.firstInstance<StatusFilter>().addTo(builder)
        filters.firstInstance<SeasonFilter>().addTo(builder)
        filters.firstInstance<LanguageFilter>().addTo(builder)
        filters.firstInstance<YearFromFilter>().addTo(builder)
        filters.firstInstance<YearToFilter>().addTo(builder)
        filters.firstInstance<GenreFilter>().state.forEachIndexed { index, checkBox ->
            if (checkBox.state) builder.addQueryParameter("genres[]", GENRES[index].second)
        }
    }

    private val SORT = arrayOf(
        "Predefinito" to "",
        "Ultimi aggiunti" to "latest",
        "Più visti" to "views",
        "Più seguiti" to "members",
        "Valutazione" to "score",
        "Titolo A-Z" to "title_az",
        "Titolo Z-A" to "title_za",
    )

    private val TYPE = arrayOf(
        "Qualsiasi" to "",
        "TV" to "tv",
        "Movie" to "movie",
        "OVA" to "ova",
        "ONA" to "ona",
        "TV Short" to "tv_short",
        "Special" to "special",
    )

    private val STATUS = arrayOf(
        "Qualsiasi" to "",
        "In corso" to "ongoing",
        "Terminato" to "completed",
        "In arrivo" to "upcoming",
        "Droppato" to "dropped",
    )

    private val SEASON = arrayOf(
        "Qualsiasi" to "",
        "Inverno" to "winter",
        "Primavera" to "spring",
        "Estate" to "summer",
        "Autunno" to "fall",
    )

    private val LANGUAGE = arrayOf(
        "Qualsiasi" to "",
        "Italiano" to "ita",
        "Giapponese" to "jpn",
        "Cinese" to "chn",
        "Coreano" to "kor",
        "Inglese" to "eng",
    )

    private val YEARS = arrayOf("Qualsiasi" to "") + (2027 downTo 1970).map { it.toString() to it.toString() }

    private val GENRES = listOf(
        "Action" to "1", "Adventure" to "2", "Avant Garde" to "3", "Boys Love" to "4", "Comedy" to "5",
        "Demons" to "6", "Drama" to "7", "Ecchi" to "8", "Fantasy" to "9", "Game" to "10",
        "Girls Love" to "11", "Gore" to "12", "Gourmet" to "13", "Harem" to "14", "Hentai" to "15",
        "Historical" to "16", "Horror" to "17", "Isekai" to "18", "Josei" to "19", "Kids" to "20",
        "Mahou Shoujo" to "21", "Martial Arts" to "22", "Mecha" to "23", "Military" to "24", "Music" to "25",
        "Mystery" to "26", "Parody" to "27", "Police" to "28", "Psychological" to "29", "Racing" to "30",
        "Reincarnation" to "31", "Romance" to "32", "Samurai" to "33", "School" to "34", "Sci-fi" to "35",
        "Seinen" to "36", "Shoujo" to "37", "Shounen" to "38", "Slice of Life" to "39", "Space" to "40",
        "Sports" to "41", "Super Power" to "42", "Supernatural" to "43", "Survival" to "44", "Thriller" to "45",
        "Vampire" to "46",
    )
}
