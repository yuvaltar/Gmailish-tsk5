package com.example.gmailish.data.repository;

import com.example.gmailish.data.dao.UserDao;
import com.example.gmailish.data.entity.UserEntity;

public class UserRepository {

    private final UserDao userDao;

    public UserRepository(UserDao userDao) {
        this.userDao = userDao;
    }

    // Reads (blocking; call on background thread)
    public UserEntity getUserByUsername(String username) {
        return userDao.getByUsername(username);
    }

    // Writes (blocking)
    public void saveUser(UserEntity user) {
        userDao.upsert(user);
    }

    public void clearUsers() {
        userDao.clear();
    }
}
