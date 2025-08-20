package com.example.gmailish.data.local;

import com.example.gmailish.data.entity.MailEntity;
import com.example.gmailish.data.repository.MailRepository;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class RoomHelper {

    private final MailRepository mailRepository;

    @Inject
    public RoomHelper(MailRepository mailRepository) {
        this.mailRepository = mailRepository;
    }

    public void saveMails(List<MailEntity> mails) {
        mailRepository.saveMails(mails);
    }
}
