package com.example.gmailish.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.gmailish.data.entity.LabelEntity;

import java.util.List;

@Dao
public interface LabelDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(LabelEntity label);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<LabelEntity> labels);

    // Existing: list by owner (generic)
    @Query("SELECT * FROM labels WHERE ownerId = :ownerId ORDER BY name ASC")
    List<LabelEntity> getByOwnerSync(String ownerId);

    // Matches InboxActivity.loadLocalLabels() usage exactly
    @Query("SELECT * FROM labels WHERE ownerId = :ownerId ORDER BY name ASC")
    List<LabelEntity> getAllByOwner(String ownerId);

    // Fetch by owner and name (used by LabelRepository.getLabelByName)
    @Query("SELECT * FROM labels WHERE ownerId = :ownerId AND name = :name LIMIT 1")
    LabelEntity getByName(String ownerId, String name);

    // Delete by id (used by LabelRepository.deleteLabel)
    @Query("DELETE FROM labels WHERE id = :labelId")
    int deleteById(String labelId);
}
