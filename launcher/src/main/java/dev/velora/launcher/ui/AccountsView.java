package dev.velora.launcher.ui;

import dev.velora.launcher.LauncherContext;
import dev.velora.launcher.VeloraLauncherApp;
import dev.velora.launcher.auth.Account;
import dev.velora.launcher.auth.MicrosoftAuth;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/** Account management: Microsoft (device code flow) and offline accounts. */
public final class AccountsView {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private final LauncherContext ctx;
    private final VeloraLauncherApp app;

    public AccountsView(LauncherContext ctx, VeloraLauncherApp app) {
        this.ctx = ctx;
        this.app = app;
    }

    public Node build() {
        VBox list = new VBox(8);
        refresh(list);
        Button ms = Ui.primary("Microsoft-Konto hinzufügen", () -> microsoftLogin(list));
        var buttons = Ui.row(ms);
        if (LauncherContext.devMode()) {
            buttons.getChildren().add(Ui.secondary("Offline-Konto (Entwicklermodus)", () -> offlineAccount(list)));
        }
        Label info = Ui.hint("Die Anmeldung erfolgt über die offizielle Microsoft-Seite in deinem Browser (Gerätecode). "
                + "Velora sieht dein Passwort nie. Anmeldedaten werden mit Windows DPAPI verschlüsselt gespeichert und nur an "
                + "Microsoft, Xbox Live und Mojang übertragen. Gespielt werden kann nur mit einem Account, der Minecraft: Java Edition besitzt.");
        return Ui.page(Ui.title("Konten"), Ui.subtitle("Wähle das Konto, mit dem du spielen möchtest."), buttons, info, list,
                officialLauncherCard());
    }

    /** Multiplayer path that works without an own Azure app: let the official Minecraft Launcher do the login. */
    private Node officialLauncherCard() {
        Label status = Ui.hint(dev.velora.launcher.game.OfficialLauncherIntegration.officialLauncherDetected()
                ? "Offizieller Minecraft Launcher gefunden."
                : "Offizieller Minecraft Launcher nicht gefunden – installiere ihn von minecraft.net, das Profil erscheint dann automatisch.");
        Button install = Ui.primary("Velora im Minecraft Launcher installieren", () -> { });
        install.setOnAction(e -> {
            install.setDisable(true);
            status.setText("Installiere…");
            ctx.background.submit(() -> {
                try {
                    var r = new dev.velora.launcher.game.OfficialLauncherIntegration(ctx.installer).install(ctx.settings.selectedProfile());
                    Platform.runLater(() -> {
                        install.setDisable(false);
                        status.setText("Fertig: Profil \"" + ctx.settings.selectedProfile().name + "\" (" + r.versionId() + ") eingetragen. "
                                + (r.launcherInstalled() ? "Starte den Minecraft Launcher und wähle das Velora-Profil."
                                : "Installiere jetzt den offiziellen Minecraft Launcher – das Profil ist dort bereits vorhanden."));
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        install.setDisable(false);
                        status.setText("Fehler: " + ex.getMessage());
                    });
                }
            });
        });
        Button download = Ui.secondary("minecraft.net öffnen", () -> app.openUrl("https://www.minecraft.net/download"));
        return Ui.card(Ui.section("Multiplayer mit deinem Microsoft-Account"),
                Ui.hint("Ohne eigene Azure-App-ID: Velora trägt sich als Fabric-Profil in den offiziellen Minecraft Launcher ein. "
                        + "Der offizielle Launcher übernimmt die Anmeldung – damit funktionieren alle Online-Server. "
                        + "Velora nutzt dabei einen eigenen Spielordner, andere Clients bleiben unberührt."),
                Ui.row(install, download), status);
    }

    private void refresh(VBox list) {
        list.getChildren().clear();
        if (ctx.usableAccounts().isEmpty()) list.getChildren().add(Ui.hint("Noch kein Konto angemeldet."));
        String selected = ctx.selectedAccount().map(Account::id).orElse("");
        for (Account a : ctx.usableAccounts()) {
            Label name = new Label(a.username);
            name.getStyleClass().add("news-title");
            String type = a.type == Account.Type.MICROSOFT ? "Microsoft-Konto" : "Offline-Konto";
            Label meta = Ui.hint(type + " · UUID " + a.uuid);
            Button use = Ui.secondary(a.id().equals(selected) ? "Aktiv" : "Verwenden", () -> {
                ctx.settings.selectedAccount = a.id();
                ctx.settings.save();
                refresh(list);
            });
            use.setDisable(a.id().equals(selected));
            Button remove = Ui.danger("Entfernen", () -> {
                if (Ui.confirm(app.stage(), "Konto entfernen?", a.username + " wird aus dem Launcher entfernt (gespeicherte Anmeldung wird gelöscht).")) {
                    ctx.accounts.remove(a);
                    refresh(list);
                }
            });
            VBox textCol = new VBox(2, name, meta);
            var row = Ui.row(textCol, Ui.spacer(), use, remove);
            VBox card = Ui.card(row);
            if (a.id().equals(selected)) card.getStyleClass().add("card-active");
            list.getChildren().add(card);
        }
    }

    private void offlineAccount(VBox list) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.initOwner(app.stage());
        dialog.setTitle("Offline-Konto");
        dialog.setHeaderText("Spielername für Einzelspieler / LAN (3–16 Zeichen, A–Z, 0–9, _)");
        dialog.showAndWait().ifPresent(name -> {
            if (!NAME.matcher(name).matches()) {
                Ui.error(app.stage(), "Ungültiger Name", "Erlaubt sind 3–16 Zeichen: Buchstaben, Ziffern und _.");
                return;
            }
            Account a = Account.offline(name);
            ctx.accounts.put(a);
            ctx.settings.selectedAccount = a.id();
            ctx.settings.save();
            refresh(list);
        });
    }

    private void microsoftLogin(VBox list) {
        MicrosoftAuth auth;
        try {
            auth = new MicrosoftAuth(ctx.settings.microsoftClientId);
        } catch (IllegalStateException e) {
            Ui.error(app.stage(), "Microsoft-Login nicht eingerichtet",
                    "Für den offiziellen Microsoft-Login wird eine eigene Azure-Anwendungs-ID (Client-ID) benötigt, "
                            + "die von Mojang für die Minecraft-API freigeschaltet wurde.\n\n"
                            + "Trage sie unter Einstellungen → Microsoft-Login ein. Anleitung: docs/MICROSOFT_LOGIN.md");
            return;
        }
        Stage dialog = new Stage();
        dialog.initOwner(app.stage());
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setTitle("Mit Microsoft anmelden");
        Label header = new Label("Mit Microsoft anmelden");
        header.getStyleClass().add("page-title");
        Label step = Ui.hint("Fordere Anmeldecode an…");
        Label code = new Label("");
        code.getStyleClass().add("device-code");
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(28, 28);
        Button open = Ui.primary("Code kopieren & Browser öffnen", () -> { });
        open.setDisable(true);
        AtomicBoolean cancelled = new AtomicBoolean();
        Button cancel = Ui.secondary("Abbrechen", () -> {
            cancelled.set(true);
            dialog.close();
        });
        VBox box = new VBox(14, header, step, code, Ui.row(open, cancel), spinner);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(24));
        box.getStyleClass().add("root-pane");
        Scene scene = new Scene(box, 480, 300);
        scene.getStylesheets().addAll(app.stage().getScene().getStylesheets());
        dialog.setScene(scene);
        dialog.setOnHidden(e -> cancelled.set(true));
        dialog.show();

        ctx.background.submit(() -> {
            try {
                MicrosoftAuth.DeviceCode dc = auth.requestDeviceCode();
                Platform.runLater(() -> {
                    step.setText("1. Öffne " + dc.verificationUri() + "\n2. Gib diesen Code ein und melde dich an:");
                    code.setText(dc.userCode());
                    open.setDisable(false);
                    open.setOnAction(e -> {
                        ClipboardContent c = new ClipboardContent();
                        c.putString(dc.userCode());
                        Clipboard.getSystemClipboard().setContent(c);
                        app.openUrl(dc.verificationUri());
                    });
                });
                Account account = auth.pollAndLogin(dc, cancelled::get, s -> Platform.runLater(() -> step.setText(s)));
                ctx.accounts.put(account);
                ctx.settings.selectedAccount = account.id();
                ctx.settings.save();
                Platform.runLater(() -> {
                    dialog.close();
                    refresh(list);
                    Ui.info(app.stage(), "Angemeldet", "Willkommen, " + account.username + "!");
                });
            } catch (MicrosoftAuth.AuthException e) {
                if (cancelled.get()) return;
                Platform.runLater(() -> {
                    dialog.close();
                    Ui.error(app.stage(), "Anmeldung fehlgeschlagen", e.getMessage());
                });
            }
        });
    }
}
