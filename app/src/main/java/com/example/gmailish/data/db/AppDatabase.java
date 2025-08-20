package com.example.gmailish.data.db;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

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
        version = 3,
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
}
