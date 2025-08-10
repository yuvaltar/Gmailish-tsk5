package com.example.gmailish.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.gmailish.data.dao.BlacklistDao
import com.example.gmailish.data.dao.LabelDao
import com.example.gmailish.data.dao.MailDao
import com.example.gmailish.data.dao.MailLabelDao
import com.example.gmailish.data.dao.UserDao
import com.example.gmailish.data.entity.BlacklistEntity
import com.example.gmailish.data.entity.LabelEntity
import com.example.gmailish.data.entity.MailEntity
import com.example.gmailish.data.entity.MailLabelCrossRef
import com.example.gmailish.data.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        MailEntity::class,
        LabelEntity::class,
        MailLabelCrossRef::class,
        BlacklistEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun mailDao(): MailDao
    abstract fun labelDao(): LabelDao
    abstract fun mailLabelDao(): MailLabelDao
    abstract fun blacklistDao(): BlacklistDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        @JvmStatic
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gmailish.db"
                )
                    .fallbackToDestructiveMigration() // dev only
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
