package com.flintlauncher.android.network

import okhttp3.Cache
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Builds the one shared OkHttp client. Every request carries a descriptive User-Agent. */
object FlintHttp {
    fun create(userAgent: String, cache: Cache? = null): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
            }
            .connectTimeout(20, TimeUnit.SECONDS)
            // A download may pause between chunks on slow mobile networks. OkHttp's read
            // timeout is an inactivity limit per socket read, not a total transfer deadline.
            // Keep it generous; DownloadManager owns bounded retries and checkpoint recovery.
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .cache(cache)
            .build()
}
