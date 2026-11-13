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
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .cache(cache)
            .build()
}
