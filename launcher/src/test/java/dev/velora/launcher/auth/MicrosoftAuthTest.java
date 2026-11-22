package dev.velora.launcher.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MicrosoftAuthTest {

    private static JsonObject json(String s) { return JsonParser.parseString(s).getAsJsonObject(); }

    @Test
    void clientIdMustBeGuid() {
        assertTrue(MicrosoftAuth.isValidClientId("779397e4-2085-465c-ba84-dd67cc74e70e"));
        assertFalse(MicrosoftAuth.isValidClientId("my-app"));
        assertFalse(MicrosoftAuth.isValidClientId(""));
        assertThrows(IllegalStateException.class, () -> new MicrosoftAuth("not-a-guid"));
    }

    @Test
    void unknownAppIsExplained() {
        // real response shape captured from login.microsoftonline.com for an unknown client id
        String msg = MicrosoftAuth.explainAzureError(json("{\"error\":\"unauthorized_client\",\"error_description\":"
                + "\"AADSTS700016: Application with identifier 'x' was not found in the directory '9188040d'.\"}"));
        assertTrue(msg.contains("kennt diese Client-ID nicht"), msg);
    }

    @Test
    void publicClientFlowsDisabledIsExplained() {
        String msg = MicrosoftAuth.explainAzureError(json("{\"error\":\"invalid_client\",\"error_description\":"
                + "\"AADSTS7000218: The request body must contain the following parameter: 'client_assertion' or 'client_secret'.\"}"));
        assertTrue(msg.contains("Öffentliche Clientflows"), msg);
    }

    @Test
    void ownershipRequiresJavaLicense() {
        assertTrue(MicrosoftAuth.ownsJavaEdition(json("{\"items\":[{\"name\":\"product_minecraft\"},{\"name\":\"game_minecraft\"}]}")));
        assertTrue(MicrosoftAuth.ownsJavaEdition(json("{\"items\":[{\"name\":\"game_minecraft\"}]}")));
        assertFalse(MicrosoftAuth.ownsJavaEdition(json("{\"items\":[]}")));
        assertFalse(MicrosoftAuth.ownsJavaEdition(json("{\"items\":[{\"name\":\"product_minecraft_bedrock\"}]}")));
        assertFalse(MicrosoftAuth.ownsJavaEdition(json("{}")));
    }

    @Test
    void offlineAccountsAreDevOnly() {
        assertFalse(dev.velora.launcher.LauncherContext.devMode(), "dev mode must be off by default");
    }
}
