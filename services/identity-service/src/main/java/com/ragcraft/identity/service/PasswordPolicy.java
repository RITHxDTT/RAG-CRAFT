package com.ragcraft.identity.service;

import com.ragcraft.common.web.AppException;
import java.util.Set;

/** 8-64 characters, not the e-mail address, not a commonly used password. */
public final class PasswordPolicy {

    private static final Set<String> COMMON = Set.of("password", "password1", "password123", "12345678", "123456789", "1234567890",
            "qwerty123", "qwertyuiop", "iloveyou", "admin123", "welcome1", "letmein123", "abc12345", "11111111", "passw0rd",
            "changeme", "user1234", "monkey123");

    private PasswordPolicy() {}

    public static void check(String password, String email) {
        if (password == null || password.length() < 8 || password.length() > 64) {
            throw AppException.badRequest("Password must be 8–64 characters.");
        }
        if (email != null && password.equalsIgnoreCase(email.trim())) {
            throw AppException.badRequest("Password cannot be the same as your email.");
        }
        if (COMMON.contains(password.toLowerCase())) {
            throw AppException.badRequest("This password is too common. Choose another.");
        }
    }
}
