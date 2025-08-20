// MailWithLabels.java
package com.example.gmailish.data.entity.relations;

import androidx.room.Embedded;
import androidx.room.Junction;
import androidx.room.Relation;

import com.example.gmailish.data.entity.LabelEntity;
import com.example.gmailish.data.entity.MailEntity;
import com.example.gmailish.data.entity.MailLabelCrossRef;

import java.util.List;

public class MailWithLabels {
    @Embedded public MailEntity mail;

    @Relation(
            parentColumn = "id",
            entityColumn = "id",
            associateBy = @Junction(
                    value = MailLabelCrossRef.class,
                    parentColumn = "mailId",
                    entityColumn = "labelId"
            )
    )
    public List<LabelEntity> labels;

    public MailWithLabels(MailEntity mail, List<LabelEntity> labels) {
        this.mail = mail; this.labels = labels;
    }
}
