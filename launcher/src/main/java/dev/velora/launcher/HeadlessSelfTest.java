package dev.velora.launcher;

import dev.velora.launcher.auth.Account;
import dev.velora.launcher.config.LauncherSettings;
import dev.velora.launcher.game.GameLauncher;
import dev.velora.launcher.game.JavaManager;
import dev.velora.launcher.game.LaunchSpec;
import dev.velora.launcher.game.VersionInstaller;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * End-to-end test without UI ({@code --selftest}): installs the selected profile exactly like the
 * "Play" button, starts the game with an offline test account and the in-game self test, and
 * reports whether the client finished its checks. Exit code 0 = passed.
 */
final class HeadlessSelfTest {
    private HeadlessSelfTest() { }

    static int run() throws Exception {
        LauncherPaths.ensure();
        LauncherContext ctx = new LauncherContext();
        LauncherSettings.GameProfile base = ctx.settings.selectedProfile();
        LauncherSettings.GameProfile profile = new LauncherSettings.GameProfile(base.name, base.minecraftVersion, base.loaderVersion, base.ramMb);
        profile.jvmArgs = "-Dvelora.selftest=true";
        System.out.println("[Launcher-SelfTest] Profil: " + profile.name + " (Minecraft " + profile.minecraftVersion + ", Loader " + profile.loaderVersion + ")");
        long start = System.currentTimeMillis();
        int[] lastPct = {-1};
        LaunchSpec spec = ctx.installer.install(profile, new VersionInstaller.Listener() {
            private String last = "";

            @Override public void status(String text) {
                String head = text.replaceAll("\\s*\\(.*", "");
                if (!head.equals(last)) {
                    last = head;
                    System.out.println("[Launcher-SelfTest] " + head);
                }
            }

            @Override public void progress(double fraction) {
                int pct = (int) (fraction * 100);
                if (fraction >= 0 && pct / 20 != lastPct[0] / 20) {
                    lastPct[0] = pct;
                    System.out.println("[Launcher-SelfTest]   " + pct + "%");
                }
            }
        }, () -> false);
        System.out.println("[Launcher-SelfTest] Installation fertig in " + (System.currentTimeMillis() - start) / 1000 + " s, "
                + spec.classpath().size() + " Classpath-Einträge, Main-Class " + spec.mainClass());

        JavaManager.JavaInstall java = ctx.java.best(spec.requiredJava(), ctx.settings.javaPath)
                .orElseThrow(() -> new IllegalStateException("Kein Java " + spec.requiredJava() + " gefunden"));
        System.out.println("[Launcher-SelfTest] Java: " + java);

        AtomicBoolean passed = new AtomicBoolean();
        AtomicBoolean veloraLoaded = new AtomicBoolean();
        Process p = new GameLauncher().launch(spec, Account.offline("VeloraTester"), profile, java, "", ctx.build.version(), null, line -> {
            if (line.contains("Velora") && line.contains("ready")) veloraLoaded.set(true);
            if (line.contains("[SelfTest] PASSED")) passed.set(true);
            if (line.contains("[SelfTest]") || line.contains("ERROR") || line.contains("Velora ") || line.contains("Exception")) {
                System.out.println("[Game] " + line);
            }
        });
        int exit = p.waitFor();
        Thread.sleep(500);
        System.out.println("[Launcher-SelfTest] Spiel beendet mit Code " + exit + ", Velora geladen: " + veloraLoaded.get()
                + ", Client-Selbsttest bestanden: " + passed.get());
        return passed.get() ? 0 : 1;
    }
}
