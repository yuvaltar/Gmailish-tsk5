// LabelWithMails.java
package com.example.gmailish.data.entity.relations;

import androidx.room.Embedded;
import androidx.room.Junction;
import androidx.room.Relation;

import com.example.gmailish.data.entity.LabelEntity;
import com.example.gmailish.data.entity.MailEntity;
import com.example.gmailish.data.entity.MailLabelCrossRef;

import java.util.List;

public class LabelWithMails {
    @Embedded public LabelEntity label;

    @Relation(
            parentColumn = "id",
            entityColumn = "id",
            associateBy = @Junction(
                    value = MailLabelCrossRef.class,
                    parentColumn = "labelId",
                    entityColumn = "mailId"
            )
    )
    public List<MailEntity> mails;

    public LabelWithMails(LabelEntity label, List<MailEntity> mails) {
        this.label = label; this.mails = mails;
    }
}
