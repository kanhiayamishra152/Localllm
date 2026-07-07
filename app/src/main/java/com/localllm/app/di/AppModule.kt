package com.localllm.app.di

import android.content.Context
import androidx.room.Room
import com.localllm.app.data.db.AppDatabase
import com.localllm.app.data.db.ChatDao
import com.localllm.app.data.db.TaskDao
import com.localllm.app.data.remote.DuckDuckGoSearch
import com.localllm.app.data.remote.HuggingFaceApi
import com.localllm.app.domain.HardwareProfiler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "neuraltask_db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideChatDao(db: AppDatabase): ChatDao = db.chatDao()

    @Provides
    @Singleton
    fun provideTaskDao(db: AppDatabase): TaskDao = db.taskDao()

    @Provides
    @Singleton
    fun provideHuggingFaceApi(client: OkHttpClient): HuggingFaceApi = HuggingFaceApi(client)

    @Provides
    @Singleton
    fun provideDuckDuckGoSearch(client: OkHttpClient): DuckDuckGoSearch = DuckDuckGoSearch(client)

    @Provides
    @Singleton
    fun provideHardwareProfiler(@ApplicationContext context: Context): HardwareProfiler = HardwareProfiler(context)
}
