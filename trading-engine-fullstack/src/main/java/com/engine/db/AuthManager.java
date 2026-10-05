package com.engine.db;

import com.engine.core.UserProfileManager;
import com.engine.model.UserProfile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class AuthManager {
    public static class UserCredential {
        public final String id;
        public final String username;
        public final String email;
        public final String fullName;
        public final String passwordHash;

        public UserCredential(String id, String username, String email, String fullName, String passwordHash) {
            this.id = id;
            this.username = username;
            this.email = email;
            this.fullName = fullName;
            this.passwordHash = passwordHash;
        }
    }

    private final Map<String, UserCredential> byUsername = new ConcurrentHashMap<>();
    private final Map<String, UserCredential> byEmail = new ConcurrentHashMap<>();
    private final Map<String, UserCredential> byId = new ConcurrentHashMap<>();
    private final AtomicInteger userCounter = new AtomicInteger(4);

    public AuthManager() {
        // Pre-populate default users: Alice, Bob, Carol with password "password123"
        String defaultHash = hashPassword("password123");
        saveCredential(new UserCredential("T001", "alice", "alice@trading.local", "Alice Smith", defaultHash));
        saveCredential(new UserCredential("T002", "bob", "bob@trading.local", "Bob Jones", defaultHash));
        saveCredential(new UserCredential("T003", "carol", "carol@trading.local", "Carol White", defaultHash));
    }

    private void saveCredential(UserCredential cred) {
        byUsername.put(cred.username.toLowerCase(), cred);
        byEmail.put(cred.email.toLowerCase(), cred);
        byId.put(cred.id, cred);
    }

    public synchronized UserCredential register(
            String username,
            String email,
            String fullName,
            String password,
            UserProfileManager um,
            DatabaseManager db) throws IllegalArgumentException {

        if (username == null || username.trim().length() < 3) {
            throw new IllegalArgumentException("Username must be at least 3 characters.");
        }
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("Invalid email address.");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters.");
        }

        String lowerUser = username.trim().toLowerCase();
        String lowerEmail = email.trim().toLowerCase();

        if (byUsername.containsKey(lowerUser)) {
            throw new IllegalArgumentException("Username '" + username + "' is already taken.");
        }
        if (byEmail.containsKey(lowerEmail)) {
            throw new IllegalArgumentException("Email '" + email + "' is already registered.");
        }

        String newId = "T" + String.format("%03d", userCounter.getAndIncrement());
        String hash = hashPassword(password);
        String name = (fullName != null && !fullName.trim().isEmpty()) ? fullName.trim() : username.trim();

        UserCredential cred = new UserCredential(newId, lowerUser, lowerEmail, name, hash);
        saveCredential(cred);

        // Initialize in-memory trading profile with $100,000 initial virtual cash
        UserProfile profile = new UserProfile(newId, name, 100000.0);
        um.addUser(profile);

        // Persist to MySQL
        if (db != null) {
            db.saveNewUser(newId, lowerUser, lowerEmail, name, hash, 100000.0);
        }

        System.out.println("[AUTH] Registered new user: " + lowerUser + " (" + newId + ")");
        return cred;
    }

    public UserCredential login(String usernameOrEmail, String password) {
        if (usernameOrEmail == null || password == null) return null;
        String key = usernameOrEmail.trim().toLowerCase();

        UserCredential cred = byUsername.get(key);
        if (cred == null) {
            cred = byEmail.get(key);
        }
        if (cred == null) return null;

        String hash = hashPassword(password);
        if (cred.passwordHash.equalsIgnoreCase(hash)) {
            return cred;
        }
        return null;
    }

    public UserCredential getById(String id) {
        return byId.get(id);
    }

    public Collection<UserCredential> getAllUsers() {
        return byId.values();
    }

    public static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available in JVM", e);
        }
    }
}
