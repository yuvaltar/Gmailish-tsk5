package com.example.gmailish.data.entity.relations

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import com.example.gmailish.data.entity.LabelEntity
import com.example.gmailish.data.entity.MailEntity
import com.example.gmailish.data.entity.MailLabelCrossRef

data class LabelWithMails(
    @Embedded val label: LabelEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = MailLabelCrossRef::class,
            parentColumn = "labelId",
            entityColumn = "mailId"
        )
    )
    val mails: List<MailEntity>
)