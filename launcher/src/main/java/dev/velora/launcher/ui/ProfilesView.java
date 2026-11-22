package dev.velora.launcher.ui;

import dev.velora.launcher.LauncherContext;
import dev.velora.launcher.LauncherPaths;
import dev.velora.launcher.VeloraLauncherApp;
import dev.velora.launcher.config.LauncherSettings;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/** Game profiles: separate instance folders with their own RAM, JVM arguments and resolution. */
public final class ProfilesView {
    private final LauncherContext ctx;
    private final VeloraLauncherApp app;

    public ProfilesView(LauncherContext ctx, VeloraLauncherApp app) {
        this.ctx = ctx;
        this.app = app;
    }

    public Node build() {
        VBox list = new VBox(10);
        render(list);
        Button add = Ui.primary("Neues Profil", () -> {
            TextInputDialog d = new TextInputDialog("Velora PvP");
            d.initOwner(app.stage());
            d.setTitle("Neues Profil");
            d.setHeaderText("Name des Profils (eigener Spielordner)");
            d.showAndWait().map(String::trim).filter(s -> !s.isEmpty()).ifPresent(name -> {
                if (ctx.settings.profile(name).isPresent()) {
                    Ui.error(app.stage(), "Name vergeben", "Ein Profil mit diesem Namen existiert bereits.");
                    return;
                }
                LauncherSettings.GameProfile base = ctx.settings.selectedProfile();
                ctx.settings.profiles.add(new LauncherSettings.GameProfile(name, base.minecraftVersion, base.loaderVersion, base.ramMb));
                ctx.settings.save();
                render(list);
            });
        });
        return Ui.page(Ui.title("Profile"), Ui.subtitle("Jedes Profil hat einen eigenen Spielordner (Welten, Einstellungen, Velora-Konfiguration)."),
                Ui.row(add), list);
    }

    private void render(VBox list) {
        list.getChildren().clear();
        long systemMb = LauncherSettings.totalSystemMemoryMb();
        for (LauncherSettings.GameProfile p : ctx.settings.profiles) {
            Label name = new Label(p.name);
            name.getStyleClass().add("news-title");
            Label meta = Ui.hint("Minecraft " + p.minecraftVersion + " · Fabric Loader " + p.loaderVersion + " · " + LauncherPaths.instance(p.name));

            Slider ram = new Slider(1024, Math.max(2048, Math.min(systemMb - 1024, 16384)), p.ramMb);
            ram.setMajorTickUnit(1024);
            ram.setBlockIncrement(512);
            ram.setSnapToTicks(false);
            ram.setPrefWidth(260);
            Label ramLabel = new Label(p.ramMb + " MB");
            ram.valueProperty().addListener((o, a, b) -> {
                int mb = (int) (Math.round(b.doubleValue() / 256.0) * 256);
                ramLabel.setText(mb + " MB");
                p.ramMb = mb;
            });
            ram.valueChangingProperty().addListener((o, a, changing) -> { if (!changing) ctx.settings.save(); });

            TextField jvm = new TextField(p.jvmArgs == null ? "" : p.jvmArgs);
            jvm.setPromptText("Zusätzliche JVM-Argumente (optional)");
            jvm.focusedProperty().addListener((o, a, focused) -> {
                if (!focused) {
                    p.jvmArgs = jvm.getText().trim();
                    ctx.settings.save();
                }
            });
            Spinner<Integer> width = new Spinner<>(640, 7680, p.width > 0 ? p.width : 1280, 10);
            Spinner<Integer> height = new Spinner<>(480, 4320, p.height > 0 ? p.height : 720, 10);
            width.setEditable(true);
            height.setEditable(true);
            width.setPrefWidth(100);
            height.setPrefWidth(100);
            width.valueProperty().addListener((o, a, b) -> { p.width = b; ctx.settings.save(); });
            height.valueProperty().addListener((o, a, b) -> { p.height = b; ctx.settings.save(); });

            GridPane grid = new GridPane();
            grid.setHgap(12);
            grid.setVgap(8);
            grid.addRow(0, Ui.hint("Arbeitsspeicher"), Ui.row(ram, ramLabel));
            grid.addRow(1, Ui.hint("JVM-Argumente"), jvm);
            grid.addRow(2, Ui.hint("Fenstergröße"), Ui.row(width, new Label("×"), height));

            Button folder = Ui.secondary("Ordner öffnen", () -> app.openPath(LauncherPaths.instance(p.name)));
            Button select = Ui.secondary(p.name.equals(ctx.settings.selectedProfile) ? "Ausgewählt" : "Auswählen", () -> {
                ctx.settings.selectedProfile = p.name;
                ctx.settings.save();
                render(list);
            });
            select.setDisable(p.name.equals(ctx.settings.selectedProfile));
            Button delete = Ui.danger("Löschen", () -> {
                if (ctx.settings.profiles.size() <= 1) {
                    Ui.error(app.stage(), "Nicht möglich", "Mindestens ein Profil wird benötigt.");
                    return;
                }
                if (Ui.confirm(app.stage(), "Profil löschen?", "Das Profil wird aus dem Launcher entfernt. Der Spielordner bleibt erhalten.")) {
                    ctx.settings.profiles.remove(p);
                    if (p.name.equals(ctx.settings.selectedProfile)) ctx.settings.selectedProfile = ctx.settings.profiles.get(0).name;
                    ctx.settings.save();
                    render(list);
                }
            });
            VBox card = Ui.card(Ui.row(new VBox(2, name, meta), Ui.spacer(), select, folder, delete), grid);
            if (p.name.equals(ctx.settings.selectedProfile)) card.getStyleClass().add("card-active");
            list.getChildren().add(card);
        }
    }
}
