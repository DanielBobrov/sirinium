package com.dlab.sirinium.data.remote

import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

// Singleton для создания экземпляра Retrofit
object RetrofitClient {
    private const val BASE_URL = "https://eralas.ru/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(1, TimeUnit.SECONDS)
        .readTimeout(1, TimeUnit.SECONDS)
        .build()

    // Создаем Gson с зарегистрированной кастомной TypeAdapterFactory
    private val gson = GsonBuilder()
        .registerTypeAdapterFactory(TeachersTypeAdapterFactory()) // ИЗМЕНЕНО: Регистрируем фабрику
        .create()

    val instance: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson)) // Используем наш Gson с фабрикой
            .build()
        retrofit.create(ApiService::class.java)
    }
}
