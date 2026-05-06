package net.harutiro.gitappinstaller.data.remote

import kotlinx.serialization.json.Json
import net.harutiro.gitappinstaller.data.auth.TokenStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import java.util.concurrent.TimeUnit

interface GitHubUserApi {
    @Headers(
        "Accept: application/vnd.github+json",
        "X-GitHub-Api-Version: 2022-11-28",
    )
    @GET("user")
    suspend fun me(): retrofit2.Response<GitHubUserDto>
}

class GitHubServiceFactory(private val tokenStore: TokenStore) {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val converter = json.asConverterFactory("application/json".toMediaType())

    private val authedOkHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                    .header("User-Agent", "GitAppInstaller/1.0")
                tokenStore.current()?.takeIf { it.isNotBlank() }?.let { token ->
                    builder.header("Authorization", "Bearer $token")
                }
                chain.proceed(builder.build())
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    private val plainOkHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", "GitAppInstaller/1.0")
                    .build()
                chain.proceed(req)
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    val api: GitHubApi by lazy {
        Retrofit.Builder()
            .baseUrl(API_BASE_URL)
            .client(authedOkHttp)
            .addConverterFactory(converter)
            .build()
            .create(GitHubApi::class.java)
    }

    val userApi: GitHubUserApi by lazy {
        Retrofit.Builder()
            .baseUrl(API_BASE_URL)
            .client(authedOkHttp)
            .addConverterFactory(converter)
            .build()
            .create(GitHubUserApi::class.java)
    }

    val authApi: GitHubAuthApi by lazy {
        Retrofit.Builder()
            .baseUrl(GITHUB_BASE_URL)
            .client(plainOkHttp)
            .addConverterFactory(converter)
            .build()
            .create(GitHubAuthApi::class.java)
    }

    companion object {
        const val API_BASE_URL = "https://api.github.com/"
        const val GITHUB_BASE_URL = "https://github.com/"
    }
}
