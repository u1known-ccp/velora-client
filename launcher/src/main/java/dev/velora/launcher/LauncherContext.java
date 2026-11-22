package dev.velora.launcher;

import dev.velora.core.backend.BackendClient;
import dev.velora.launcher.auth.Account;
import dev.velora.launcher.auth.AccountStore;
import dev.velora.launcher.config.BuildInfo;
import dev.velora.launcher.config.LauncherSettings;
import dev.velora.launcher.game.JavaManager;
import dev.velora.launcher.game.VersionInstaller;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared launcher state handed to all views. */
public final class LauncherContext {
    public final BuildInfo build = BuildInfo.load();
    public final LauncherSettings settings = LauncherSettings.load(build);
    public final AccountStore accounts = new AccountStore();
    public final JavaManager java = new JavaManager();
    public final VersionInstaller installer = new VersionInstaller(build);
    public final ExecutorService background = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "Velora-Launcher-Worker");
        t.setDaemon(true);
        return t;
    });
    private BackendClient backend;

    public LauncherContext() {
        accounts.load();
        rebuildBackend();
    }

    public void rebuildBackend() {
        backend = new BackendClient(settings.backendEnabled ? settings.backendUrl : "", "VeloraLauncher/" + build.version());
    }

    public BackendClient backend() { return backend; }

    /**
     * Developer mode ({@code -Dvelora.dev=true}): enables offline accounts for local testing.
     * Release builds never allow gameplay without Microsoft authentication and an ownership check
     * (Mojang requirement for approved App IDs).
     */
    public static boolean devMode() { return Boolean.getBoolean("velora.dev"); }

    /** Accounts that may be used in this mode (offline accounts only in developer mode). */
    public java.util.List<Account> usableAccounts() {
        return accounts.all().stream().filter(a -> a.type == Account.Type.MICROSOFT || devMode()).toList();
    }

    public Optional<Account> selectedAccount() {
        Optional<Account> a = accounts.byId(settings.selectedAccount).filter(x -> usableAccounts().contains(x));
        if (a.isPresent()) return a;
        return usableAccounts().stream().findFirst();
    }
}
