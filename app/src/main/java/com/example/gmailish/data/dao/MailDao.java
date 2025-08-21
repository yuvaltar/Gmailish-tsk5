package com.example.gmailish.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.gmailish.data.entity.MailEntity;

import java.util.List;

@Dao
public interface MailDao {

    // Upserts (blocking; call on background thread)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(MailEntity mail);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<MailEntity> mails);

    // Simple updates (blocking)
    @Query("UPDATE mails SET read = :read WHERE id = :mailId")
    int setRead(String mailId, boolean read);

    @Query("UPDATE mails SET starred = :starred WHERE id = :mailId")
    int setStarred(String mailId, boolean starred);

    // (Optional) quick edit helpers for drafts — not required but convenient
    @Query("UPDATE mails SET subject = :subject, content = :content WHERE id = :mailId")
    int updateSubjectAndContent(String mailId, String subject, String content);

    // Delete by id (blocking)
    @Query("DELETE FROM mails WHERE id = :mailId")
    int deleteById(String mailId);

    // Java-friendly synchronous getters (blocking; call on background thread)
    @Query("SELECT * FROM mails WHERE ownerId = :ownerId ORDER BY timestamp DESC")
    List<MailEntity> getMailsByOwnerSync(String ownerId);

    @Query(
            "SELECT * FROM mails " +
                    "WHERE ownerId = :ownerId AND (" +
                    " subject LIKE :query OR " +
                    " content LIKE :query OR " +
                    " senderName LIKE :query OR " +
                    " recipientEmail LIKE :query) " +
                    "ORDER BY timestamp DESC"
    )
    List<MailEntity> searchMailsSync(String ownerId, String query);

    @Query("SELECT * FROM mails WHERE ownerId = :ownerId AND starred = 1 ORDER BY timestamp DESC")
    List<MailEntity> getStarredByOwnerSync(String ownerId);

    // Get single mail by id
    @Query("SELECT * FROM mails WHERE id = :mailId LIMIT 1")
    MailEntity getByIdSync(String mailId);

    // (Optional) bulk fetch by ids — useful sometimes for batch UI work
    @Query("SELECT * FROM mails WHERE id IN (:ids)")
    List<MailEntity> getByIdsSync(List<String> ids);
}
