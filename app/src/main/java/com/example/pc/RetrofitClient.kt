package com.example.pc

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private var retrofit: Retrofit? = null

    fun getClient(ip: String): ApiService {
        // Чистим IP от лишних пробелов
        val cleanIp = ip.trim()

        // Собираем правильный URL.
        // Если ты ввел "192.168.1.23", получится "http://192.168.1.23:5000/"
        val baseUrl = if (cleanIp.startsWith("http")) {
            if (cleanIp.endsWith("/")) cleanIp else "$cleanIp/"
        } else {
            "http://$cleanIp:5000/"
        }

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(ApiService::class.java)
    }
}