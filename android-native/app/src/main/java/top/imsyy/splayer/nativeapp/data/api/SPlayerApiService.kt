package top.imsyy.splayer.nativeapp.data.api

import okhttp3.ResponseBody
import retrofit2.http.FieldMap
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.QueryMap
import retrofit2.http.Url

interface SPlayerApiService {
    @GET
    suspend fun get(
        @Url url: String,
        @QueryMap(encoded = true) params: Map<String, String>,
    ): ResponseBody

    @FormUrlEncoded
    @POST
    suspend fun post(
        @Url url: String,
        @FieldMap(encoded = true) data: Map<String, String>,
        @QueryMap(encoded = true) params: Map<String, String> = emptyMap(),
    ): ResponseBody
}
