package dev.velora.launcher;

/**
 * Plain entry point (does not extend {@code Application}) so the shaded jar starts from the classpath
 * without JavaFX module-path configuration.
 */
public final class LauncherMain {
    private LauncherMain() { }

    public static void main(String[] args) throws Exception {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", System.getProperty("org.slf4j.simpleLogger.defaultLogLevel", "info"));
        if (java.util.Arrays.asList(args).contains("--selftest")) {
            System.exit(HeadlessSelfTest.run());
        }
        if (java.util.Arrays.asList(args).contains("--install-official")) {
            LauncherPaths.ensure();
            LauncherContext ctx = new LauncherContext();
            var r = new dev.velora.launcher.game.OfficialLauncherIntegration(ctx.installer).install(ctx.settings.selectedProfile());
            System.out.println("Velora-Profil eingetragen: " + r.profilesFile());
            System.out.println("Version: " + r.versionId() + " · Spielordner: " + r.gameDir());
            if (!r.launcherInstalled()) {
                System.out.println("Hinweis: Der offizielle Minecraft Launcher wurde nicht gefunden. Nach der Installation erscheint das Profil \"Velora\" automatisch.");
            }
            System.exit(0);
        }
        VeloraLauncherApp.main(args);
    }
}
