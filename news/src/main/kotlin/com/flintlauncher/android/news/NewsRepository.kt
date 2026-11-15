package com.flintlauncher.android.news

/**
 * News feed. Explicitly unavailable in Spike 0: no news source is configured, and
 * presence is never faked. A future optional backend (e.g. Minecraft.net RSS or a
 * community feed) would implement [NewsRepository] and return real articles.
 */
sealed interface NewsAvailability {
    data object Unavailable : NewsAvailability
    data class Available(val articles: List<NewsArticle>) : NewsAvailability
}

data class NewsArticle(
    val id: String,
    val category: String,
    val title: String,
    val summary: String,
    val dateMillis: Long,
    val url: String,
)

interface NewsRepository {
    suspend fun load(): NewsAvailability
}

/** Honest stub: always reports unavailable. Never returns fabricated articles. */
object UnavailableNewsRepository : NewsRepository {
    override suspend fun load(): NewsAvailability = NewsAvailability.Unavailable
}
