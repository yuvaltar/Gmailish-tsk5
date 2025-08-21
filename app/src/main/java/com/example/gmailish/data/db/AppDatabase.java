package com.example.gmailish.data.db;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.gmailish.data.dao.BlacklistDao;
import com.example.gmailish.data.dao.LabelDao;
import com.example.gmailish.data.dao.MailDao;
import com.example.gmailish.data.dao.MailLabelDao;
import com.example.gmailish.data.dao.PendingOperationDao;
import com.example.gmailish.data.dao.UserDao;
import com.example.gmailish.data.entity.BlacklistEntity;
import com.example.gmailish.data.entity.LabelEntity;
import com.example.gmailish.data.entity.MailEntity;
import com.example.gmailish.data.entity.MailLabelCrossRef;
import com.example.gmailish.data.entity.PendingOperationEntity;
import com.example.gmailish.data.entity.UserEntity;

@Database(
        entities = {
                UserEntity.class,
                MailEntity.class,
                LabelEntity.class,
                MailLabelCrossRef.class,
                BlacklistEntity.class,
                PendingOperationEntity.class
        },
        // ⬇️ bump from 3 → 4 because we added "isDraft" to mails
        version = 4,
        exportSchema = false
)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {

    public abstract UserDao userDao();
    public abstract MailDao mailDao();
    public abstract LabelDao labelDao();
    public abstract MailLabelDao mailLabelDao();
    public abstract BlacklistDao blacklistDao();
    public abstract PendingOperationDao pendingOperationDao();

    /**
     * Migration 3 → 4: add the new "isDraft" column to the "mails" table.
     * Default 0 = not a draft. This preserves all existing rows.
     */
    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override public void migrate(SupportSQLiteDatabase db) {
            try {
                db.execSQL("ALTER TABLE mails ADD COLUMN isDraft INTEGER NOT NULL DEFAULT 0");
            } catch (Throwable ignore) {
                // Column already exists on some dev devices – ignore.
            }
            // Ensure index exists after upgrade
            db.execSQL("CREATE INDEX IF NOT EXISTS index_mails_isDraft ON mails(isDraft)");
        }
    };

}
