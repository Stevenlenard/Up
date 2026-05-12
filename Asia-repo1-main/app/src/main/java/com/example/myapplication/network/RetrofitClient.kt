package com.example.myapplication.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // Siguraduhin na ang folder sa htdocs ay tumutugma sa BASE_URL (halimbawa: Asia-repo1-main)
    private const val BASE_URL = "http://192.168.254.106/Asia-repo1-main/backend/"

    val instance: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(ApiService::class.java)
    }
}