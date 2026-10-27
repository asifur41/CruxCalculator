package com.tapecalc.pro.di

import android.content.Context
import com.tapecalc.core.data.db.AppDatabase
import com.tapecalc.core.data.repo.TapeRepository
import com.tapecalc.core.data.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Wires the local database and preference store. There is no network client anywhere in the app. */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase = AppDatabase.create(context)

    @Provides
    @Singleton
    fun provideTapeRepository(db: AppDatabase): TapeRepository =
        TapeRepository(db.workspaceDao(), db.historyDao(), db.variableDao())

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository = SettingsRepository(context)
}
