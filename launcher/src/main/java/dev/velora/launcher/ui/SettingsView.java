package dev.velora.launcher.ui;

import dev.velora.launcher.LauncherContext;
import dev.velora.launcher.LauncherPaths;
import dev.velora.launcher.VeloraLauncherApp;
import dev.velora.launcher.game.JavaManager;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.util.List;

/** Launcher settings: Microsoft login app id, Java, behaviour, backend. */
public final class SettingsView {
    private static final String AUTO = "Automatisch (empfohlen)";
    private final LauncherContext ctx;
    private final VeloraLauncherApp app;

    public SettingsView(LauncherContext ctx, VeloraLauncherApp app) {
        this.ctx = ctx;
        this.app = app;
    }

    public Node build() {
        var s = ctx.settings;

        // Microsoft login
        TextField clientId = new TextField(s.microsoftClientId);
        clientId.setPromptText("Azure Application (client) ID");
        clientId.textProperty().addListener((o, a, b) -> {
            s.microsoftClientId = b.trim();
            s.save();
        });
        Label checkResult = Ui.hint("");
        checkResult.setManaged(false);
        Button check = Ui.primary("Client-ID prüfen", () -> { });
        check.setOnAction(e -> {
            checkResult.setManaged(true);
            check.setDisable(true);
            checkResult.setText("Prüfe Azure-Konfiguration…");
            ctx.background.submit(() -> {
                String result;
                try {
                    new dev.velora.launcher.auth.MicrosoftAuth(s.microsoftClientId).checkAzureConfiguration();
                    result = "✔ Azure-Konfiguration in Ordnung (App gefunden, persönliche Konten + Gerätecode erlaubt). "
                            + "Ob Mojang die ID freigeschaltet hat, zeigt sich beim ersten Login unter \"Konten\".";
                } catch (IllegalStateException ex) {
                    result = "✖ " + ex.getMessage();
                } catch (dev.velora.launcher.auth.MicrosoftAuth.AuthException ex) {
                    result = "✖ " + ex.getMessage();
                }
                String text = result;
                Platform.runLater(() -> {
                    check.setDisable(false);
                    checkResult.setText(text);
                });
            });
        });
        Button portal = Ui.secondary("Azure-Portal öffnen", () -> app.openUrl("https://entra.microsoft.com/#view/Microsoft_AAD_RegisteredApps/ApplicationsListBlade"));
        Button form = Ui.secondary("Mojang-Freigabe beantragen", () -> app.openUrl(dev.velora.launcher.auth.MicrosoftAuth.REVIEW_FORM));
        VBox msCard = Ui.card(Ui.section("Microsoft-Login"),
                Ui.hint("Velora nutzt den offiziellen OAuth-Gerätecode-Flow. Dafür brauchst du eine eigene Azure-App-Registrierung, "
                        + "die Mojang für die Minecraft-API freigeschaltet hat (Anleitung: docs/MICROSOFT_LOGIN.md)."),
                clientId, Ui.row(check, portal, form), checkResult);

        // Java
        ComboBox<String> java = new ComboBox<>();
        java.getItems().add(AUTO);
        java.setValue(s.javaPath.isBlank() ? AUTO : s.javaPath);
        java.setMaxWidth(Double.MAX_VALUE);
        java.setEditable(true);
        Label javaInfo = Ui.hint("Suche Java-Installationen…");
        ctx.background.submit(() -> {
            List<JavaManager.JavaInstall> found = ctx.java.detect();
            Platform.runLater(() -> {
                for (JavaManager.JavaInstall j : found) java.getItems().add(j.home().toString());
                javaInfo.setText(found.isEmpty() ? "Keine Java-Installation gefunden – Velora lädt beim Start automatisch Java 21 von Mojang."
                        : found.size() + " Installation(en) gefunden: " + String.join(", ", found.stream().map(j -> "Java " + j.version()).toList()));
            });
        });
        java.valueProperty().addListener((o, a, b) -> {
            s.javaPath = b == null || b.equals(AUTO) ? "" : b.trim();
            s.save();
        });
        ProgressBar javaProgress = new ProgressBar(0);
        javaProgress.setVisible(false);
        Button download = Ui.secondary("Mojang Java 21 herunterladen", () -> {
            javaProgress.setVisible(true);
            ctx.background.submit(() -> {
                try {
                    JavaManager.JavaInstall j = ctx.java.installMojangRuntime("java-runtime-delta",
                            p -> Platform.runLater(() -> javaProgress.setProgress(p)), () -> false);
                    Platform.runLater(() -> {
                        javaProgress.setVisible(false);
                        java.getItems().add(j.home().toString());
                        Ui.info(app.stage(), "Java installiert", j.toString());
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        javaProgress.setVisible(false);
                        Ui.error(app.stage(), "Download fehlgeschlagen", e.getMessage());
                    });
                }
            });
        });
        VBox javaCard = Ui.card(Ui.section("Java"), java, javaInfo, Ui.row(download, javaProgress));

        // behaviour
        CheckBox minimize = new CheckBox("Launcher beim Spielstart minimieren");
        minimize.setSelected(s.closeOnLaunch);
        minimize.selectedProperty().addListener((o, a, b) -> { s.closeOnLaunch = b; s.save(); });
        CheckBox autoUpdate = new CheckBox("Automatisch nach Updates suchen (benötigt Backend)");
        autoUpdate.setSelected(s.autoUpdate);
        autoUpdate.selectedProperty().addListener((o, a, b) -> { s.autoUpdate = b; s.save(); });
        VBox behaviourCard = Ui.card(Ui.section("Verhalten"), minimize, autoUpdate);

        // backend
        CheckBox backendEnabled = new CheckBox("Velora-Backend verwenden (News, Updates, Cosmetics)");
        backendEnabled.setSelected(s.backendEnabled);
        TextField backendUrl = new TextField(s.backendUrl);
        backendUrl.setPromptText("https://api.example.net");
        backendEnabled.selectedProperty().addListener((o, a, b) -> { s.backendEnabled = b; s.save(); ctx.rebuildBackend(); });
        backendUrl.focusedProperty().addListener((o, a, focused) -> {
            if (!focused) {
                s.backendUrl = backendUrl.getText().trim();
                s.save();
                ctx.rebuildBackend();
            }
        });
        VBox backendCard = Ui.card(Ui.section("Backend (optional)"), backendEnabled, backendUrl,
                Ui.hint("Ohne Backend funktioniert alles lokal: mitgelieferte News, kostenlose Cosmetics, keine Cloud-Sync."));

        Button folder = Ui.secondary("Velora-Ordner öffnen", () -> app.openPath(LauncherPaths.root()));
        return Ui.page(Ui.title("Einstellungen"), msCard, javaCard, behaviourCard, backendCard, Ui.row(folder));
    }
}
