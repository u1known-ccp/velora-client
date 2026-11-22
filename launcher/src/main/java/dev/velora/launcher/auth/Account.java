package dev.velora.launcher.auth;

/**
 * A launcher account. Secrets are only kept in memory in plain form; {@link AccountStore} encrypts them on disk.
 * OFFLINE accounts have no tokens and can only play singleplayer / offline-mode LAN.
 */
public final class Account {
    public enum Type { MICROSOFT, OFFLINE }

    public Type type;
    public String username;
    public String uuid;
    public String xuid = "";
    /** Minecraft access token (never written in plain text). */
    public transient String accessToken = "";
    /** Microsoft refresh token (never written in plain text). */
    public transient String refreshToken = "";
    public long accessTokenExpiresAt;

    public Account() { }

    public static Account offline(String name) {
        Account a = new Account();
        a.type = Type.OFFLINE;
        a.username = name;
        a.uuid = java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toString().replace("-", "");
        a.accessToken = "0";
        return a;
    }

    public boolean isAccessTokenValid() {
        return type == Type.OFFLINE || (!accessToken.isEmpty() && System.currentTimeMillis() < accessTokenExpiresAt - 60_000);
    }

    public String id() { return type + ":" + uuid; }

    @Override
    public String toString() {
        return username + (type == Type.OFFLINE ? " (Offline)" : "");
    }
}
