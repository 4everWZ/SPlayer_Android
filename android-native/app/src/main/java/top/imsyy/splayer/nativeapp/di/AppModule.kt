package top.imsyy.splayer.nativeapp.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import top.imsyy.splayer.nativeapp.data.api.SPlayerApiService
import top.imsyy.splayer.nativeapp.data.local.AppSettingsStore
import top.imsyy.splayer.nativeapp.data.local.SPlayerDatabase

@Qualifier
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `playlist_detail_cache` (
                    `playlistId` INTEGER NOT NULL,
                    `name` TEXT NOT NULL,
                    `coverUrl` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `playCount` INTEGER NOT NULL,
                    `subscribedCount` INTEGER NOT NULL,
                    `trackCount` INTEGER NOT NULL,
                    `tracksJson` TEXT NOT NULL,
                    `cachedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`playlistId`)
                )
                """.trimIndent(),
            )
        }
    }

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SPlayerDatabase {
        return Room.databaseBuilder(context, SPlayerDatabase::class.java, "splayer_native.db")
            .addMigrations(MIGRATION_1_2)
            .build()
    }

    @Provides
    fun providePlaybackQueueDao(database: SPlayerDatabase) = database.playbackQueueDao()

    @Provides
    fun provideRecentPlayDao(database: SPlayerDatabase) = database.recentPlayDao()

    @Provides
    fun provideFailedSourceDao(database: SPlayerDatabase) = database.failedSourceDao()

    @Provides
    fun providePlaylistDetailCacheDao(database: SPlayerDatabase) = database.playlistDetailCacheDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(appSettingsStore: AppSettingsStore): OkHttpClient {
        val requestCookieInterceptor = Interceptor { chain ->
            val request = chain.request()
            val cookieHeader = appSettingsStore.buildCookieHeader()
            val newRequest = request.newBuilder().apply {
                if (cookieHeader.isNotBlank()) {
                    header("Cookie", cookieHeader)
                }
                header("User-Agent", "SPlayer-Native/2.0")
            }.build()
            chain.proceed(newRequest)
        }

        val responseCookieInterceptor = Interceptor { chain ->
            val response = chain.proceed(chain.request())
            val cookies = response.headers("Set-Cookie")
            if (cookies.isNotEmpty()) {
                val parsed = cookies.associate { cookie ->
                    val pair = cookie.substringBefore(";").split("=", limit = 2)
                    pair.first() to pair.getOrElse(1) { "" }
                }
                kotlinx.coroutines.runBlocking {
                    appSettingsStore.updateCookies(
                        musicU = parsed["MUSIC_U"],
                        csrf = parsed["__csrf"],
                        nmtid = parsed["NMTID"],
                    )
                }
            }
            response
        }

        return OkHttpClient.Builder()
            .addInterceptor(requestCookieInterceptor)
            .addInterceptor(responseCookieInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(appSettingsStore: AppSettingsStore, okHttpClient: OkHttpClient): Retrofit {
        val baseUrl = appSettingsStore.apiRoot.trimEnd('/') + "/"
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): SPlayerApiService {
        return retrofit.create(SPlayerApiService::class.java)
    }
}
