package com.example.data

import com.squareup.moshi.JsonClass
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class GoogleDriveFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: String? = null
)

@JsonClass(generateAdapter = true)
data class GoogleDriveResponse(
    val files: List<GoogleDriveFile>? = null
)

interface GoogleDriveService {
    @GET("drive/v3/files")
    suspend fun listFiles(
        @Header("Authorization") authHeader: String,
        @Query("pageSize") pageSize: Int = 50,
        @Query("fields") fields: String = "files(id, name, mimeType, size)",
        @Query("q") query: String? = null
    ): GoogleDriveResponse

    companion object {
        fun create(): GoogleDriveService {
            return Retrofit.Builder()
                .baseUrl("https://www.googleapis.com/")
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
                .create(GoogleDriveService::class.java)
        }
    }
}
