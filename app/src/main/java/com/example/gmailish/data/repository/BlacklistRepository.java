package com.example.gmailish.data.repository;

import com.example.gmailish.data.dao.BlacklistDao;
import com.example.gmailish.data.entity.BlacklistEntity;

import java.util.List;

public class BlacklistRepository {

    private final BlacklistDao blacklistDao;

    public BlacklistRepository(BlacklistDao blacklistDao) {
        this.blacklistDao = blacklistDao;
    }

    // Reads (blocking; add a sync method to DAO if you only had Flow before)
    public List<BlacklistEntity> getAllSync() {
        return blacklistDao.getAllSync();
    }

    public boolean isBlacklisted(String url) {
        return blacklistDao.exists(url);
    }

    // Writes (blocking)
    public void save(BlacklistEntity entry) {
        blacklistDao.upsert(entry);
    }

    public int remove(String url) {
        return blacklistDao.delete(url);
    }
}
