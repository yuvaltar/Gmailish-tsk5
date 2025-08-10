package com.example.gmailish.di

import android.content.Context
import androidx.room.Room
import com.example.gmailish.data.db.AppDatabase
import com.example.gmailish.data.dao.*
import com.example.gmailish.data.repository.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "gmailish.db")
            .fallbackToDestructiveMigration() // dev only
            .build()

    @Provides fun provideUserDao(db: AppDatabase): UserDao = db.userDao()
    @Provides fun provideMailDao(db: AppDatabase): MailDao = db.mailDao()
    @Provides fun provideLabelDao(db: AppDatabase): LabelDao = db.labelDao()
    @Provides fun provideMailLabelDao(db: AppDatabase): MailLabelDao = db.mailLabelDao()
    @Provides fun provideBlacklistDao(db: AppDatabase): BlacklistDao = db.blacklistDao()

    @Provides @Singleton
    fun provideUserRepository(userDao: UserDao): UserRepository = UserRepository(userDao)

    @Provides @Singleton
    fun provideMailRepository(
        mailDao: MailDao,
        labelDao: LabelDao,
        mailLabelDao: MailLabelDao
    ): MailRepository = MailRepository(mailDao, labelDao, mailLabelDao)

    @Provides @Singleton
    fun provideLabelRepository(
        labelDao: LabelDao,
        mailLabelDao: MailLabelDao
    ): LabelRepository = LabelRepository(labelDao, mailLabelDao)

    @Provides @Singleton
    fun provideBlacklistRepository(blacklistDao: BlacklistDao): BlacklistRepository =
        BlacklistRepository(blacklistDao)
}