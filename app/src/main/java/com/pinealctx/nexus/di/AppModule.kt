package com.pinealctx.nexus.di

import android.content.Context
import com.pinealctx.nexus.core.NexusCoreWrapper
import com.pinealctx.nexus.core.SecureStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSecureStorage(
        @ApplicationContext context: Context
    ): SecureStorage {
        return SecureStorage(context)
    }

    @Provides
    @Singleton
    fun provideNexusCoreWrapper(
        @ApplicationContext context: Context,
        secureStorage: SecureStorage
    ): NexusCoreWrapper {
        val wrapper = NexusCoreWrapper(context, secureStorage)
        try {
            wrapper.initialize()
        } catch (e: Exception) {
            android.util.Log.e("NexusApp", "Failed to initialize core", e)
        }
        return wrapper
    }
}
