package com.example.gmailish.data.db;

import android.content.Context;

import androidx.room.Room;

public final class AppDbProvider {
    private static volatile AppDatabase INSTANCE;

    private AppDbProvider() {}

    public static AppDatabase get(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDbProvider.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "gmailish.db"
                            )
                            // Keep data when going 3 → 4
                            .addMigrations(AppDatabase.MIGRATION_3_4)
                            // Optional safety: if another future path is missing, wipe instead of crashing
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
