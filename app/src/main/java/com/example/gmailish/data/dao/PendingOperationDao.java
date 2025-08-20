package com.example.gmailish.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.gmailish.data.entity.PendingOperationEntity;

import java.util.List;

@Dao
public interface PendingOperationDao {


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(PendingOperationEntity op);

    @Query("SELECT * FROM pending_operations WHERE status = 'PENDING' ORDER BY createdAt ASC")
    List<PendingOperationEntity> getAllPending();

    @Query("UPDATE pending_operations SET status = 'DONE' WHERE id = :id")
    int markDone(String id);

    @Query("UPDATE pending_operations SET retryCount = retryCount + 1 WHERE id = :id")
    int incrementRetry(String id);

    @Query("DELETE FROM pending_operations WHERE id = :id")
    int delete(String id);
}