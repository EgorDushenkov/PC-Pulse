package com.example.pc

import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {

    @GET("/stats")
    fun getStats(): Call<PCStats>

    @GET("/screenshot")
    fun getScreenshot(): Call<ResponseBody>

    @POST("/volume")
    fun setVolume(@Query("val") value: Int): Call<String>

    @GET("/mixer")
    fun getMixerSessions(): Call<List<MixerSession>>

    @POST("/mixer/set")
    fun setMixerVolume(
        @Query("app") appName: String,
        @Query("vol") volume: Int
    ): Call<String>

    @POST("/process/kill")
    fun killProcess(@Query("pid") pid: Int): Call<String>

    @POST("/power/shutdown")
    fun shutdownPC(): Call<String>

    @POST("/power/sleep")
    fun sleepPC(): Call<String>

    @POST("/run")
    fun runCommand(@Query("path") path: String): Call<String>

    @POST("/media/command")
    fun sendMediaCommand(@Query("cmd") command: String): Call<String>
}
