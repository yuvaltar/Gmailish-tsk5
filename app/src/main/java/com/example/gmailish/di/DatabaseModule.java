package com.example.gmailish.di;

import android.content.Context;

import androidx.room.Room;

import com.example.gmailish.data.dao.PendingOperationDao;
import com.example.gmailish.data.repository.PendingOperationRepository;
import com.example.gmailish.data.dao.BlacklistDao;
import com.example.gmailish.data.dao.LabelDao;
import com.example.gmailish.data.dao.MailDao;
import com.example.gmailish.data.dao.MailLabelDao;
import com.example.gmailish.data.dao.UserDao;
import com.example.gmailish.data.db.AppDatabase;
import com.example.gmailish.data.repository.BlacklistRepository;
import com.example.gmailish.data.repository.LabelRepository;
import com.example.gmailish.data.repository.MailRepository;
import com.example.gmailish.data.repository.UserRepository;
import com.example.gmailish.data.sync.PendingSyncManager;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public final class DatabaseModule {

    @Provides
    @Singleton
    public AppDatabase provideDatabase(@ApplicationContext Context context) {
        return Room.databaseBuilder(context, AppDatabase.class, "gmailish.db")
                .fallbackToDestructiveMigration() // dev only
                .build();
    }

    @Provides public UserDao provideUserDao(AppDatabase db) { return db.userDao(); }
    @Provides public MailDao provideMailDao(AppDatabase db) { return db.mailDao(); }
    @Provides public LabelDao provideLabelDao(AppDatabase db) { return db.labelDao(); }
    @Provides public MailLabelDao provideMailLabelDao(AppDatabase db) { return db.mailLabelDao(); }
    @Provides public BlacklistDao provideBlacklistDao(AppDatabase db) { return db.blacklistDao(); }
    @Provides public PendingOperationDao providePendingOperationDao(AppDatabase db) { return db.pendingOperationDao(); }

    @Provides @Singleton
    public UserRepository provideUserRepository(UserDao userDao) {
        return new UserRepository(userDao);
    }

    @Provides @Singleton
    public MailRepository provideMailRepository(MailDao mailDao, LabelDao labelDao, MailLabelDao mailLabelDao) {
        return new MailRepository(mailDao, labelDao, mailLabelDao);
    }

    @Provides @Singleton
    public LabelRepository provideLabelRepository(LabelDao labelDao, MailLabelDao mailLabelDao) {
        return new LabelRepository(labelDao, mailLabelDao);
    }

    @Provides @Singleton
    public BlacklistRepository provideBlacklistRepository(BlacklistDao blacklistDao) {
        return new BlacklistRepository(blacklistDao);
    }

    @Provides @Singleton
    public PendingOperationRepository providePendingOperationRepository(PendingOperationDao dao) {
        return new PendingOperationRepository(dao);
    }

    @Provides @Singleton
    public PendingSyncManager providePendingSyncManager(PendingOperationRepository repo) {
        return new PendingSyncManager(repo);
    }
}
