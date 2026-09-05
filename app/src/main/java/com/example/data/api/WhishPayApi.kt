package com.example.data.api

import com.example.data.crypto.WhishSecurity
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

@JsonClass(generateAdapter = true)
data class WhishBaseResponse<T>(
    @Json(name = "status") val status: Boolean,
    @Json(name = "code") val code: String?,
    @Json(name = "dialog") val dialog: WhishDialog?,
    @Json(name = "data") val data: T?
)

@JsonClass(generateAdapter = true)
data class WhishDialog(
    @Json(name = "title") val title: String?,
    @Json(name = "message") val message: String?
)

@JsonClass(generateAdapter = true)
data class WhishBalanceDetails(
    @Json(name = "balanceDetails") val balanceDetails: BalanceDetails
)

@JsonClass(generateAdapter = true)
data class BalanceDetails(
    @Json(name = "balance") val balance: Double
)

@JsonClass(generateAdapter = true)
data class WhishPaymentRequest(
    @Json(name = "amount") val amount: String,
    @Json(name = "currency") val currency: String,
    @Json(name = "invoice") val invoice: String,
    @Json(name = "externalId") val externalId: Long,
    @Json(name = "successCallbackUrl") val successCallbackUrl: String,
    @Json(name = "failureCallbackUrl") val failureCallbackUrl: String,
    @Json(name = "successRedirectUrl") val successRedirectUrl: String,
    @Json(name = "failureRedirectUrl") val failureRedirectUrl: String
)

@JsonClass(generateAdapter = true)
data class WhishPaymentResponse(
    @Json(name = "collectUrl") val collectUrl: String
)

@JsonClass(generateAdapter = true)
data class WhishStatusRequest(
    @Json(name = "currency") val currency: String,
    @Json(name = "externalId") val externalId: Long
)

@JsonClass(generateAdapter = true)
data class WhishStatusResponse(
    @Json(name = "collectStatus") val collectStatus: String, // success, failed, pending
    @Json(name = "payerPhoneNumber") val payerPhoneNumber: String?
)

interface WhishPayService {
    @GET("payment/account/balance")
    suspend fun getBalance(): WhishBaseResponse<WhishBalanceDetails>

    @POST("payment/whish")
    suspend fun initiatePayment(
        @Body request: WhishPaymentRequest
    ): WhishBaseResponse<WhishPaymentResponse>

    @POST("payment/collect/status")
    suspend fun getStatus(
        @Body request: WhishStatusRequest
    ): WhishBaseResponse<WhishStatusResponse>
}

object WhishPayApi {
    private const val BASE_URL = "https://api.sandbox.whish.money/itel-service/api/"

    // NOTE: this whole interceptor — and the app's direct calls to Whish's API at all —
    // is slated for removal once payment initiation moves server-side (remediation plan
    // Phase 5). It is kept functional for now, sourced from the single WhishSecurity
    // config object instead of re-typed literals, so it isn't a second hardcoded copy.
    private val headerInterceptor = Interceptor { chain ->
        val original = chain.request()
        val request = original.newBuilder()
            .header("channel", WhishSecurity.CHANNEL_ID)
            .header("httpchannel", WhishSecurity.CHANNEL_ID)
            .header("secret", WhishSecurity.DEFAULT_SECRET_KEY)
            .header("websiteUrl", WhishSecurity.SOURCE_EMAIL)
            .header("User-Agent", "Whish/1.0 (https://whish.money; support@whish.money)")
            .header("Content-Type", "application/json")
            .build()
        chain.proceed(request)
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(headerInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()

    val service: WhishPayService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(WhishPayService::class.java)
    }
}
