plugins {
    alias(kei.plugins.extension)
}

yuzono {
    name = "AnimeUnity"
    versionCode = 11
    contentWarning = ContentWarning.SAFE
    libVersion = "14"

    source {
        lang = "it"
        baseUrl("https://animeunity.cx") {
            withCustom = true
        }
    }
}
