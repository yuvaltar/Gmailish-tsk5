package com.example.gmailish.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.gmailish.data.entity.BlacklistEntity;

import java.util.List;

@Dao
public interface BlacklistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(BlacklistEntity entry);

    @Query("DELETE FROM blacklist WHERE url = :url")
    int delete(String url);

    // Optional: keep LiveData if some UI observes it
    @Query("SELECT * FROM blacklist ORDER BY url ASC")
    LiveData<List<BlacklistEntity>> observeAll();

    // Java-friendly synchronous getter for repositories
    @Query("SELECT * FROM blacklist ORDER BY url ASC")
    List<BlacklistEntity> getAllSync();

    @Query("SELECT EXISTS(SELECT 1 FROM blacklist WHERE url = :url LIMIT 1)")
    boolean exists(String url);
}
