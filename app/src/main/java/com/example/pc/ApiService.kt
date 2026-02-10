package com.example.pc

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {

    // --- Основная статистика ---
    @GET("/stats")
    fun getStats(): Call<PCStats>

    // --- Скриншот ---
    @GET("/screenshot")
    fun getScreenshot(): Call<ResponseBody>

    // --- Общая громкость ---
    @POST("/volume")
    fun setVolume(@Query("val") value: Int): Call<String>

    // --- Микшер громкости (GET) ---
    @GET("/mixer")
    fun getMixerSessions(): Call<List<MixerSession>>

    // --- Микшер громкости (SET) ---
    @POST("/mixer/set")
    fun setMixerVolume(
        @Query("app") appName: String,
        @Query("vol") volume: Int
    ): Call<String>

    // --- Завершение процесса ---
    @POST("/process/kill")
    fun killProcess(@Query("pid") pid: Int): Call<String>

    // --- Управление питанием ---
    @POST("/power/shutdown")
    fun shutdownPC(): Call<String>

    @POST("/power/sleep")
    fun sleepPC(): Call<String>
}
