package com.localllm.app.di

import android.content.Context
import androidx.room.Room
import com.localllm.app.data.db.AppDatabase
import com.localllm.app.data.db.ChatDao
import com.localllm.app.data.remote.DuckDuckGoSearch
import com.localllm.app.data.remote.HuggingFaceApi
import com.localllm.app.data.repository.ChatRepository
import com.localllm.app.data.repository.ModelRepository
import com.localllm.app.domain.HardwareProfiler
import com.localllm.app.domain.InferenceEngine
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
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "localllm_database"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    @Singleton
    fun provideChatDao(database: AppDatabase): ChatDao = database.chatDao()

    @Provides
    @Singleton
    fun provideHuggingFaceApi(client: OkHttpClient): HuggingFaceApi {
        return HuggingFaceApi(client)
    }

    @Provides
    @Singleton
    fun provideDuckDuckGoSearch(client: OkHttpClient): DuckDuckGoSearch {
        return DuckDuckGoSearch(client)
    }

    @Provides
    @Singleton
    fun provideModelRepository(
        @ApplicationContext context: Context,
        huggingFaceApi: HuggingFaceApi,
        client: OkHttpClient
    ): ModelRepository {
        return ModelRepository(context, huggingFaceApi, client)
    }

    @Provides
    @Singleton
    fun provideChatRepository(chatDao: ChatDao): ChatRepository {
        return ChatRepository(chatDao)
    }

    @Provides
    @Singleton
    fun provideHardwareProfiler(@ApplicationContext context: Context): HardwareProfiler {
        return HardwareProfiler(context)
    }

    @Provides
    @Singleton
    fun provideInferenceEngine(): InferenceEngine {
        return InferenceEngine()
    }
}
