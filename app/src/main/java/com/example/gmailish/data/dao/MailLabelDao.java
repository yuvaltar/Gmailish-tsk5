package com.example.gmailish.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.gmailish.data.entity.MailEntity;
import com.example.gmailish.data.entity.MailLabelCrossRef;

import java.util.List;

@Dao
public interface MailLabelDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long add(MailLabelCrossRef ref);

    @Query("DELETE FROM mail_label_cross_ref WHERE mailId = :mailId AND labelId = :labelId")
    int remove(String mailId, String labelId);

    @Query("DELETE FROM mail_label_cross_ref WHERE mailId = :mailId")
    int clearForMail(String mailId);

    // NEW: clear all cross-refs for a given label (used by LabelRepository.deleteLabel)
    @Query("DELETE FROM mail_label_cross_ref WHERE labelId = :labelId")
    int clearForLabel(String labelId);

    // For listing mails by label
    @Query("SELECT m.* FROM mails m INNER JOIN mail_label_cross_ref x ON m.id = x.mailId WHERE x.labelId = :labelId ORDER BY m.timestamp DESC")
    List<MailEntity> getMailsForLabelSync(String labelId);

    // NEW: get labels for a mail (used by offline mail detail building)
    @Query("SELECT labelId FROM mail_label_cross_ref WHERE mailId = :mailId")
    List<String> getLabelsForMailSync(String mailId);
    @Query(
            "SELECT m.* FROM mails m " +
                    "INNER JOIN mail_label_cross_ref x ON m.id = x.mailId " +
                    "WHERE x.labelId = :labelId AND m.ownerId = :ownerId " +
                    "ORDER BY m.timestamp DESC"
    )
    List<MailEntity> getMailsForLabelSync(String labelId, String ownerId);
}