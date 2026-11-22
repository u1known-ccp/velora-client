package dev.velora.launcher.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.velora.core.config.JsonFiles;
import dev.velora.launcher.LauncherPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Persists accounts; tokens are encrypted with {@link SecretCrypto} before they touch the disk. */
public final class AccountStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("Velora/Accounts");
    private final List<Account> accounts = new ArrayList<>();

    public synchronized List<Account> all() { return List.copyOf(accounts); }

    public synchronized Optional<Account> byId(String id) {
        return accounts.stream().filter(a -> a.id().equals(id)).findFirst();
    }

    public synchronized void put(Account account) {
        accounts.removeIf(a -> a.id().equals(account.id()));
        accounts.add(account);
        save();
    }

    public synchronized void remove(Account account) {
        accounts.removeIf(a -> a.id().equals(account.id()));
        save();
    }

    public synchronized void load() {
        accounts.clear();
        JsonFiles.read(LauncherPaths.accountsFile()).ifPresent(root -> {
            JsonArray arr = root.has("accounts") ? root.getAsJsonArray("accounts") : new JsonArray();
            for (JsonElement e : arr) {
                try {
                    JsonObject o = e.getAsJsonObject();
                    Account a = new Account();
                    a.type = Account.Type.valueOf(o.get("type").getAsString());
                    a.username = o.get("username").getAsString();
                    a.uuid = o.get("uuid").getAsString();
                    a.xuid = o.has("xuid") ? o.get("xuid").getAsString() : "";
                    a.accessTokenExpiresAt = o.has("expiresAt") ? o.get("expiresAt").getAsLong() : 0;
                    if (a.type == Account.Type.OFFLINE) {
                        a.accessToken = "0";
                    } else {
                        a.accessToken = SecretCrypto.decrypt(o.has("accessToken") ? o.get("accessToken").getAsString() : "");
                        a.refreshToken = SecretCrypto.decrypt(o.has("refreshToken") ? o.get("refreshToken").getAsString() : "");
                    }
                    accounts.add(a);
                } catch (RuntimeException ex) {
                    LOGGER.warn("Skipping invalid account entry");
                }
            }
        });
    }

    private void save() {
        JsonArray arr = new JsonArray();
        for (Account a : accounts) {
            JsonObject o = new JsonObject();
            o.addProperty("type", a.type.name());
            o.addProperty("username", a.username);
            o.addProperty("uuid", a.uuid);
            o.addProperty("xuid", a.xuid);
            o.addProperty("expiresAt", a.accessTokenExpiresAt);
            if (a.type == Account.Type.MICROSOFT) {
                o.addProperty("accessToken", SecretCrypto.encrypt(a.accessToken));
                o.addProperty("refreshToken", SecretCrypto.encrypt(a.refreshToken));
            }
            arr.add(o);
        }
        JsonObject root = new JsonObject();
        root.addProperty("format", 1);
        root.add("accounts", arr);
        try {
            JsonFiles.write(LauncherPaths.accountsFile(), root);
        } catch (IOException e) {
            LOGGER.error("Could not save accounts", e);
        }
    }
}
