package com.engine.core;

import com.engine.model.UserProfile;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UserProfileManager {
    private final Map<String, UserProfile> users = new ConcurrentHashMap<>();

    public void addUser(UserProfile u) {
        users.put(u.traderId, u);
    }

    public UserProfile getUser(String id) {
        return users.get(id);
    }

    public boolean userExists(String id) {
        return users.containsKey(id);
    }

    public Collection<UserProfile> getAllProfiles() {
        return users.values();
    }

    public void printAllProfiles() {
        System.out.println("\n===== TRADER PROFILES =====");
        for (UserProfile u : users.values()) {
            System.out.println(u);
        }
    }
}
