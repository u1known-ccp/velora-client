package dev.velora.launcher.ui;

import dev.velora.launcher.LauncherContext;
import dev.velora.launcher.LauncherPaths;
import dev.velora.launcher.VeloraLauncherApp;
import dev.velora.launcher.auth.Account;
import dev.velora.launcher.auth.MicrosoftAuth;
import dev.velora.launcher.config.LauncherSettings;
import dev.velora.launcher.game.GameLauncher;
import dev.velora.launcher.game.JavaManager;
import dev.velora.launcher.game.LaunchSpec;
import dev.velora.launcher.service.UpdateService;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runs the complete "Play" flow off the FX thread and exposes observable status for the UI. */
public final class LaunchController {
    private final LauncherContext ctx;
    private final VeloraLauncherApp app;
    private final LogBuffer log;

    public final StringProperty status = new SimpleStringProperty("Bereit");
    public final DoubleProperty progress = new SimpleDoubleProperty(0);
    public final BooleanProperty busy = new SimpleBooleanProperty(false);
    public final BooleanProperty running = new SimpleBooleanProperty(false);
    public final StringProperty updateNotice = new SimpleStringProperty("");
    private final AtomicBoolean cancel = new AtomicBoolean();
    private Process process;

    public LaunchController(LauncherContext ctx, VeloraLauncherApp app, LogBuffer log) {
        this.ctx = ctx;
        this.app = app;
        this.log = log;
    }

    public void cancel() { cancel.set(true); }

    public void play(String quickPlayServer) {
        if (busy.get()) return;
        Optional<Account> maybe = ctx.selectedAccount();
        if (maybe.isEmpty()) {
            Ui.info(app.stage(), "Kein Konto", "Bitte melde dich zuerst unter \"Konten\" an.");
            app.show("Konten");
            return;
        }
        Account account = maybe.get();
        LauncherSettings.GameProfile profile = ctx.settings.selectedProfile();
        busy.set(true);
        cancel.set(false);
        ctx.background.submit(() -> {
            try {
                Account ready = ensureLoggedIn(account);
                LaunchSpec spec = ctx.installer.install(profile, new dev.velora.launcher.game.VersionInstaller.Listener() {
                    @Override public void status(String text) { fx(() -> status.set(text)); }

                    @Override public void progress(double fraction) { fx(() -> progress.set(fraction)); }
                }, cancel::get);

                JavaManager.JavaInstall java = ctx.java.best(spec.requiredJava(), ctx.settings.javaPath).orElse(null);
                if (java == null) {
                    fx(() -> status.set("Lade Java " + spec.requiredJava() + " (Mojang-Runtime)…"));
                    java = ctx.java.installMojangRuntime(runtimeComponent(spec.requiredJava()), p -> fx(() -> progress.set(p)), cancel::get);
                }
                final JavaManager.JavaInstall javaFinal = java;
                fx(() -> {
                    status.set("Starte Minecraft…");
                    progress.set(-1);
                });
                log.add("[Velora] Java: " + javaFinal);
                Process p = new GameLauncher().launch(spec, ready, profile, javaFinal, ctx.settings.microsoftClientId,
                        ctx.build.version(), quickPlayServer, log::add);
                process = p;
                fx(() -> {
                    running.set(true);
                    busy.set(false);
                    status.set("Spiel läuft (" + profile.name + ")");
                    progress.set(0);
                    if (ctx.settings.closeOnLaunch) app.stage().setIconified(true);
                });
                int exit = p.waitFor();
                fx(() -> {
                    running.set(false);
                    status.set(exit == 0 ? "Spiel beendet" : "Spiel beendet (Code " + exit + ") – siehe Logs");
                    if (ctx.settings.closeOnLaunch) app.stage().setIconified(false);
                    if (exit != 0) app.show("Logs");
                });
            } catch (MicrosoftAuth.AuthException e) {
                fail("Anmeldung fehlgeschlagen", e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Abgebrochen", "");
            } catch (Exception e) {
                log.add("[Velora] Fehler: " + e);
                fail("Start fehlgeschlagen", e.getMessage() == null ? e.toString() : e.getMessage());
            }
        });
    }

    private static String runtimeComponent(int major) {
        return switch (major) {
            case 8 -> "jre-legacy";
            case 16 -> "java-runtime-alpha";
            case 17 -> "java-runtime-gamma";
            default -> "java-runtime-delta"; // Java 21
        };
    }

    private Account ensureLoggedIn(Account account) throws MicrosoftAuth.AuthException {
        if (account.type == Account.Type.OFFLINE) {
            if (!LauncherContext.devMode()) throw new MicrosoftAuth.AuthException("Offline-Konten sind nur im Entwicklermodus erlaubt. Bitte mit Microsoft anmelden.");
            return account;
        }
        if (account.isAccessTokenValid()) return account;
        fx(() -> status.set("Erneuere Microsoft-Anmeldung…"));
        Account refreshed = new MicrosoftAuth(ctx.settings.microsoftClientId).refresh(account, s -> fx(() -> status.set(s)));
        ctx.accounts.put(refreshed);
        return refreshed;
    }

    public void kill() {
        if (process != null && process.isAlive()) process.destroy();
    }

    private void fail(String header, String message) {
        fx(() -> {
            busy.set(false);
            progress.set(0);
            status.set(header);
            if (!message.isEmpty()) Ui.error(app.stage(), header, message);
        });
    }

    /** Background update check via the optional backend. */
    public void checkForUpdatesAsync() {
        if (!ctx.settings.autoUpdate || !ctx.settings.backendEnabled) return;
        ctx.background.submit(() -> {
            UpdateService updates = new UpdateService(ctx.backend());
            String mc = ctx.build.minecraftVersion();
            updates.check("client-" + mc, ctx.build.version(), "velora-client-").ifPresent(r ->
                    fx(() -> updateNotice.set("Client-Update " + r.version() + " geladen – wird beim nächsten Start verwendet.")));
            updates.check("launcher", ctx.build.version(), "VeloraLauncher-").ifPresent(r ->
                    fx(() -> updateNotice.set("Launcher-Update " + r.version() + " bereit: " + r.file().getFileName()
                            + " (Ordner " + LauncherPaths.updates() + ")")));
        });
    }

    private static void fx(Runnable r) { Platform.runLater(r); }
}
