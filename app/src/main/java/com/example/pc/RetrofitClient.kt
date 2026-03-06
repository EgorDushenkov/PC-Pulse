package com.example.pc

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private val clients = mutableMapOf<String, ApiService>()

    fun getClient(ip: String): ApiService {
        val cleanIp = ip.trim()
        val baseUrl = if (cleanIp.startsWith("http")) {
            if (cleanIp.endsWith("/")) cleanIp else "$cleanIp/"
        } else {
            "http://$cleanIp:5000/"
        }

        return clients.getOrPut(baseUrl) {
            Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
        }
    }
}