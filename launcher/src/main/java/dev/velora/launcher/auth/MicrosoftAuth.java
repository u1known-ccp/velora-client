package dev.velora.launcher.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Official Microsoft → Xbox Live → XSTS → Minecraft Services login using the OAuth 2.0 device code flow.
 * The user signs in on microsoft.com in their own browser; the launcher never sees a password.
 * Tokens are only sent to login.microsoftonline.com, *.xboxlive.com and api.minecraftservices.com.
 */
public final class MicrosoftAuth {
    private static final String AUTHORITY = "https://login.microsoftonline.com/consumers/oauth2/v2.0";
    private static final String SCOPE = "XboxLive.signin offline_access";
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final String clientId;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    public MicrosoftAuth(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException("Keine Microsoft-Client-ID konfiguriert (Einstellungen → Microsoft-Login).");
        }
        if (!isValidClientId(clientId)) {
            throw new IllegalStateException("Die Client-ID muss eine GUID sein (z.B. 1a2b3c4d-1234-5678-9abc-0123456789ab).");
        }
        this.clientId = clientId.trim();
    }

    public static boolean isValidClientId(String id) {
        return id != null && id.trim().matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    }

    /** Link to Mojang's App ID review form (from help.minecraft.net article 16254801392141). */
    public static final String REVIEW_FORM = "https://aka.ms/mce-reviewappid";

    /**
     * Checks the Azure side of the configuration (app exists, personal accounts allowed) by requesting a device code.
     * The code is simply not used. Mojang's approval can only be verified with a real login.
     */
    public void checkAzureConfiguration() throws AuthException {
        requestDeviceCode();
    }

    public record DeviceCode(String userCode, String verificationUri, String deviceCode, int intervalSeconds, int expiresInSeconds, String message) { }

    public static final class AuthException extends Exception {
        public AuthException(String message) { super(message); }

        public AuthException(String message, Throwable cause) { super(message, cause); }
    }

    // ------------------------------------------------------------------ step 1: device code

    public DeviceCode requestDeviceCode() throws AuthException {
        JsonObject json = postForm(AUTHORITY + "/devicecode", Map.of("client_id", clientId, "scope", SCOPE));
        if (json.has("error")) throw new AuthException(explainAzureError(json));
        return new DeviceCode(str(json, "user_code"), str(json, "verification_uri"), str(json, "device_code"),
                json.has("interval") ? json.get("interval").getAsInt() : 5,
                json.has("expires_in") ? json.get("expires_in").getAsInt() : 900, str(json, "message"));
    }

    /** Step 2: polls until the user finished signing in, then completes the whole chain. */
    public Account pollAndLogin(DeviceCode code, BooleanSupplier cancelled, Consumer<String> status) throws AuthException {
        long deadline = System.currentTimeMillis() + code.expiresInSeconds() * 1000L;
        int interval = Math.max(1, code.intervalSeconds());
        while (System.currentTimeMillis() < deadline) {
            if (cancelled.getAsBoolean()) throw new AuthException("Anmeldung abgebrochen");
            sleep(interval * 1000L);
            JsonObject json = postForm(AUTHORITY + "/token", Map.of(
                    "grant_type", "urn:ietf:params:oauth:grant-type:device_code",
                    "client_id", clientId,
                    "device_code", code.deviceCode()));
            if (json.has("access_token")) {
                status.accept("Microsoft-Anmeldung erfolgreich – verbinde mit Xbox Live…");
                return completeLogin(str(json, "access_token"), str(json, "refresh_token"), status);
            }
            String error = str(json, "error");
            switch (error) {
                case "authorization_pending" -> { }
                case "slow_down" -> interval += 5;
                case "authorization_declined" -> throw new AuthException("Anmeldung wurde abgelehnt.");
                case "expired_token" -> throw new AuthException("Der Code ist abgelaufen. Bitte erneut versuchen.");
                default -> throw new AuthException(explainAzureError(json));
            }
        }
        throw new AuthException("Der Code ist abgelaufen. Bitte erneut versuchen.");
    }

    /** Silent re-login with the stored refresh token. */
    public Account refresh(Account account, Consumer<String> status) throws AuthException {
        if (account.refreshToken == null || account.refreshToken.isEmpty()) throw new AuthException("Keine gespeicherte Anmeldung – bitte neu anmelden.");
        JsonObject json = postForm(AUTHORITY + "/token", Map.of(
                "grant_type", "refresh_token",
                "client_id", clientId,
                "refresh_token", account.refreshToken,
                "scope", SCOPE));
        if (!json.has("access_token")) throw new AuthException("Sitzung abgelaufen – bitte neu anmelden. (" + describe(json) + ")");
        return completeLogin(str(json, "access_token"), str(json, "refresh_token"), status);
    }

    // ------------------------------------------------------------------ Xbox / Minecraft chain

    private Account completeLogin(String msAccessToken, String refreshToken, Consumer<String> status) throws AuthException {
        // Xbox Live user token
        JsonObject xblBody = new JsonObject();
        JsonObject props = new JsonObject();
        props.addProperty("AuthMethod", "RPS");
        props.addProperty("SiteName", "user.auth.xboxlive.com");
        props.addProperty("RpsTicket", "d=" + msAccessToken);
        xblBody.add("Properties", props);
        xblBody.addProperty("RelyingParty", "http://auth.xboxlive.com");
        xblBody.addProperty("TokenType", "JWT");
        JsonObject xbl = postJson("https://user.auth.xboxlive.com/user/authenticate", xblBody, null);
        String xblToken = str(xbl, "Token");
        String uhs = userHash(xbl);
        if (xblToken.isEmpty()) throw new AuthException("Xbox-Live-Anmeldung fehlgeschlagen.");

        // XSTS token for Minecraft services
        status.accept("Hole Xbox-Sicherheitstoken…");
        JsonObject xstsBody = new JsonObject();
        JsonObject xstsProps = new JsonObject();
        xstsProps.addProperty("SandboxId", "RETAIL");
        JsonArray tokens = new JsonArray();
        tokens.add(xblToken);
        xstsProps.add("UserTokens", tokens);
        xstsBody.add("Properties", xstsProps);
        xstsBody.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        xstsBody.addProperty("TokenType", "JWT");
        JsonObject xsts = postJson("https://xsts.auth.xboxlive.com/xsts/authorize", xstsBody, null);
        if (xsts.has("XErr")) throw new AuthException(xstsError(xsts.get("XErr").getAsLong()));
        String xstsToken = str(xsts, "Token");
        String xuid = xuid(xsts);

        // Minecraft access token
        status.accept("Melde bei Minecraft an…");
        JsonObject mcBody = new JsonObject();
        mcBody.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);
        JsonObject mc = postJson("https://api.minecraftservices.com/authentication/login_with_xbox", mcBody, null);
        String mcToken = str(mc, "access_token");
        if (mcToken.isEmpty()) {
            throw new AuthException("Mojang hat diese App-ID (noch) nicht für die Minecraft-API freigeschaltet.\n\n"
                    + "Freigabe beantragen: " + REVIEW_FORM + " (Anträge werden wöchentlich geprüft).\n"
                    + "Bis dahin: Konten → \"Velora im Minecraft Launcher installieren\".\n\nAntwort von Mojang: " + describe(mc));
        }
        long expiresIn = mc.has("expires_in") ? mc.get("expires_in").getAsLong() : 86400;

        // Ownership check (required by Mojang for approved apps) – Game Pass licenses count as well
        status.accept("Prüfe Spielbesitz…");
        if (!ownsJavaEdition(getJson("https://api.minecraftservices.com/entitlements/mcstore", mcToken))) {
            throw new AuthException("Dieser Microsoft-Account besitzt Minecraft: Java Edition nicht (keine Lizenz gefunden).");
        }

        // Profile (name + UUID)
        status.accept("Lade Minecraft-Profil…");
        JsonObject profile = getJson("https://api.minecraftservices.com/minecraft/profile", mcToken);
        if (!profile.has("id")) throw new AuthException("Für diesen Account wurde noch kein Minecraft-Profil (Spielername) angelegt. "
                + "Bitte einmal im offiziellen Launcher oder auf minecraft.net einen Namen wählen.");

        Account account = new Account();
        account.type = Account.Type.MICROSOFT;
        account.username = str(profile, "name");
        account.uuid = str(profile, "id");
        account.xuid = xuid;
        account.accessToken = mcToken;
        account.refreshToken = refreshToken;
        account.accessTokenExpiresAt = System.currentTimeMillis() + expiresIn * 1000L;
        return account;
    }

    /** Java Edition license: "product_minecraft" (purchase or Game Pass) or "game_minecraft". */
    static boolean ownsJavaEdition(JsonObject entitlements) {
        if (!entitlements.has("items") || !entitlements.get("items").isJsonArray()) return false;
        for (JsonElement e : entitlements.getAsJsonArray("items")) {
            if (!e.isJsonObject() || !e.getAsJsonObject().has("name")) continue;
            String name = e.getAsJsonObject().get("name").getAsString();
            if (name.equals("product_minecraft") || name.equals("game_minecraft")) return true;
        }
        return false;
    }

    /** Turns Microsoft identity platform errors (AADSTS codes) into actionable German hints. */
    static String explainAzureError(JsonObject json) {
        String desc = str(json, "error_description");
        String err = str(json, "error");
        if (desc.contains("AADSTS700016")) {
            return "Microsoft kennt diese Client-ID nicht. Prüfe die ID (Übersicht der App-Registrierung) und dass als Kontotyp "
                    + "\"Nur persönliche Microsoft-Konten\" (oder inkl. persönlicher Konten) gewählt ist.";
        }
        if (desc.contains("AADSTS7000218") || err.equals("invalid_client")) {
            return "Öffentliche Clientflows sind deaktiviert. In Azure: App-Registrierung → Authentifizierung → "
                    + "\"Öffentliche Clientflows zulassen\" auf \"Ja\" setzen und speichern.";
        }
        if (desc.contains("AADSTS9002331") || desc.contains("AADSTS50020") || err.equals("unauthorized_client")) {
            return "Die App ist nicht für persönliche Microsoft-Konten freigegeben. In Azure den unterstützten Kontotyp "
                    + "\"Nur persönliche Microsoft-Konten\" wählen.";
        }
        return describe(json);
    }

    private static String userHash(JsonObject xbl) {
        try {
            return xbl.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** The XSTS response for the Minecraft relying party carries the user hash; the xuid is only present for some accounts. */
    private static String xuid(JsonObject xsts) {
        try {
            JsonObject claims = xsts.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject();
            return claims.has("xid") ? claims.get("xid").getAsString() : "";
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static String xstsError(long code) {
        if (code == 2148916233L) return "Dieser Microsoft-Account hat kein Xbox-Profil. Bitte einmal auf xbox.com anmelden.";
        if (code == 2148916235L) return "Xbox Live ist in deinem Land nicht verfügbar.";
        if (code == 2148916236L || code == 2148916237L) return "Für diesen Account ist eine Altersverifizierung (Südkorea) nötig.";
        if (code == 2148916238L) return "Kinder-Account: muss von einem Erwachsenen zu einer Familie hinzugefügt werden.";
        return "Xbox-Fehler " + code;
    }

    // ------------------------------------------------------------------ HTTP helpers

    private JsonObject postForm(String url, Map<String, String> form) throws AuthException {
        StringJoiner body = new StringJoiner("&");
        form.forEach((k, v) -> body.add(URLEncoder.encode(k, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(v, StandardCharsets.UTF_8)));
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        return send(req);
    }

    private JsonObject postJson(String url, JsonObject body, String bearer) throws AuthException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT)
                .header("Content-Type", "application/json").header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (bearer != null) b.header("Authorization", "Bearer " + bearer);
        return send(b.build());
    }

    private JsonObject getJson(String url, String bearer) throws AuthException {
        return send(HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT)
                .header("Authorization", "Bearer " + bearer).header("Accept", "application/json").GET().build());
    }

    private JsonObject send(HttpRequest req) throws AuthException {
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String text = resp.body();
            if (text == null || text.isBlank()) {
                if (resp.statusCode() / 100 == 2) return new JsonObject();
                throw new AuthException("HTTP " + resp.statusCode() + " von " + req.uri().getHost());
            }
            JsonElement el = JsonParser.parseString(text);
            if (!el.isJsonObject()) throw new AuthException("Unerwartete Antwort von " + req.uri().getHost());
            return el.getAsJsonObject();
        } catch (IOException e) {
            throw new AuthException("Netzwerkfehler: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AuthException("Abgebrochen", e);
        } catch (RuntimeException e) {
            throw new AuthException("Ungültige Antwort von " + req.uri().getHost(), e);
        }
    }

    private static String describe(JsonObject json) {
        String desc = str(json, "error_description");
        if (!desc.isEmpty()) return desc.split("\r?\n")[0];
        String err = str(json, "error");
        if (!err.isEmpty()) return err;
        String msg = str(json, "errorMessage");
        return msg.isEmpty() ? json.toString() : msg;
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    private static void sleep(long ms) throws AuthException {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AuthException("Abgebrochen", e);
        }
    }
}
