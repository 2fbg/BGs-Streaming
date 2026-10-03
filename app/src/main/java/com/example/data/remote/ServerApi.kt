package com.example.data.remote

import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

/**
 * Interface Retrofit para download dinâmico da lista de servidores IPTV.
 */
interface ServerApi {

    @GET
    suspend fun getDynamicServersRaw(@Url fullUrl: String): Response<ResponseBody>

    companion object {
        private const val BASE_URL = "https://raw.githubusercontent.com/"

        fun create(): ServerApi {
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", "MK21Player/1.0 (Android)")
                        .header("Accept", "application/json, text/plain, */*")
                        .build()
                    chain.proceed(request)
                }
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .build()

            return retrofit.create(ServerApi::class.java)
        }
    }
}
