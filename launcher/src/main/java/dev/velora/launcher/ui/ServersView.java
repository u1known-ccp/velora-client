package dev.velora.launcher.ui;

import dev.velora.launcher.LauncherContext;
import dev.velora.launcher.VeloraLauncherApp;
import dev.velora.launcher.config.LauncherSettings;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.util.regex.Pattern;

/** Server shortcuts: start the game and join a server directly (vanilla Quick Play). */
public final class ServersView {
    private static final Pattern ADDRESS = Pattern.compile("[A-Za-z0-9.\\-]+(:\\d{1,5})?");
    private final LauncherContext ctx;
    private final VeloraLauncherApp app;
    private final LaunchController launcher;

    public ServersView(LauncherContext ctx, VeloraLauncherApp app, LaunchController launcher) {
        this.ctx = ctx;
        this.app = app;
        this.launcher = launcher;
    }

    public Node build() {
        VBox list = new VBox(8);
        render(list);
        TextField name = new TextField();
        name.setPromptText("Name");
        TextField address = new TextField();
        address.setPromptText("Adresse, z.B. play.example.net");
        Button add = Ui.primary("Hinzufügen", () -> {
            String n = name.getText().trim();
            String a = address.getText().trim();
            if (n.isEmpty() || !ADDRESS.matcher(a).matches()) {
                Ui.error(app.stage(), "Ungültige Eingabe", "Bitte Name und eine gültige Server-Adresse angeben.");
                return;
            }
            ctx.settings.servers.add(new LauncherSettings.ServerShortcut(n, a));
            ctx.settings.save();
            name.clear();
            address.clear();
            render(list);
        });
        return Ui.page(Ui.title("Server"), Ui.subtitle("Direkt beim Start mit einem Server verbinden."),
                Ui.card(Ui.section("Neuer Server"), Ui.row(name, address, add)), list);
    }

    private void render(VBox list) {
        list.getChildren().clear();
        for (LauncherSettings.ServerShortcut s : ctx.settings.servers) {
            Label n = new Label(s.name);
            n.getStyleClass().add("news-title");
            Button join = Ui.primary("Beitreten", () -> {
                app.show("Spielen");
                launcher.play(s.address);
            });
            join.disableProperty().bind(launcher.busy.or(launcher.running));
            Button remove = Ui.danger("Entfernen", () -> {
                ctx.settings.servers.remove(s);
                ctx.settings.save();
                render(list);
            });
            list.getChildren().add(Ui.card(Ui.row(new VBox(2, n, Ui.hint(s.address)), Ui.spacer(), join, remove)));
        }
        if (ctx.settings.servers.isEmpty()) list.getChildren().add(Ui.hint("Keine Server gespeichert."));
    }
}
