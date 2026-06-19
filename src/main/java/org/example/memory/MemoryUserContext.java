package org.example.memory;

public final class MemoryUserContext {

    private static final ThreadLocal<String> CURRENT_USER_ID = new ThreadLocal<>();

    private MemoryUserContext() {
    }

    public static void setUserId(String userId) {
        CURRENT_USER_ID.set(userId == null || userId.isBlank() ? "default_user" : userId.trim());
    }

    public static String getUserId() {
        String userId = CURRENT_USER_ID.get();
        return userId == null || userId.isBlank() ? "default_user" : userId;
    }

    public static void clear() {
        CURRENT_USER_ID.remove();
    }
}
