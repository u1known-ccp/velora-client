package dev.velora.launcher;

import dev.velora.launcher.ui.AccountsView;
import dev.velora.launcher.ui.LaunchController;
import dev.velora.launcher.ui.LogBuffer;
import dev.velora.launcher.ui.NewsView;
import dev.velora.launcher.ui.PlayView;
import dev.velora.launcher.ui.ProfilesView;
import dev.velora.launcher.ui.ServersView;
import dev.velora.launcher.ui.SettingsView;
import dev.velora.launcher.ui.Ui;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** JavaFX application: sidebar navigation + pages. */
public final class VeloraLauncherApp extends Application {
    private static final Logger LOGGER = LoggerFactory.getLogger("Velora/Launcher");

    private LauncherContext ctx;
    private Stage stage;
    private final StackPane content = new StackPane();
    private final Map<String, Button> navButtons = new LinkedHashMap<>();
    private final Map<String, Supplier<Node>> pages = new LinkedHashMap<>();
    private LogBuffer logBuffer;
    private TextArea logArea;
    private LaunchController launcher;

    public static void main(String[] args) {
        launch(VeloraLauncherApp.class, args);
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        try {
            LauncherPaths.ensure();
        } catch (IOException e) {
            LOGGER.error("Could not create launcher directories", e);
        }
        ctx = new LauncherContext();
        logArea = new TextArea();
        logArea.setEditable(false);
        logArea.getStyleClass().add("log-area");
        logBuffer = new LogBuffer(logArea);
        launcher = new LaunchController(ctx, this, logBuffer);

        pages.put("Spielen", () -> new PlayView(ctx, this, launcher).build());
        pages.put("News", () -> new NewsView(ctx, this).build());
        pages.put("Konten", () -> new AccountsView(ctx, this).build());
        pages.put("Profile", () -> new ProfilesView(ctx, this).build());
        pages.put("Server", () -> new ServersView(ctx, this, launcher).build());
        pages.put("Einstellungen", () -> new SettingsView(ctx, this).build());
        pages.put("Logs", this::buildLogs);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root-pane");
        root.setLeft(buildSidebar());
        root.setCenter(content);

        Scene scene = new Scene(root, 1060, 660);
        scene.getStylesheets().add(VeloraLauncherApp.class.getResource("/velora/launcher.css").toExternalForm());
        stage.setScene(scene);
        stage.setTitle("Velora Launcher " + ctx.build.version());
        stage.setMinWidth(900);
        stage.setMinHeight(580);
        try (InputStream icon = VeloraLauncherApp.class.getResourceAsStream("/velora/icon.png")) {
            if (icon != null) stage.getIcons().add(new Image(icon));
        } catch (IOException ignored) {
            // no icon
        }
        show("Spielen");
        stage.show();
        launcher.checkForUpdatesAsync();
        String snapshotDir = getParameters().getNamed().get("ui-snapshots");
        if (snapshotDir != null) snapshotAllPages(java.nio.file.Path.of(snapshotDir));
    }

    /** Debug aid ({@code --ui-snapshots=<dir>}): renders every page to a PNG and exits. */
    private void snapshotAllPages(java.nio.file.Path dir) {
        java.util.List<String> names = new java.util.ArrayList<>(pages.keySet());
        javafx.animation.Timeline timeline = new javafx.animation.Timeline();
        for (int i = 0; i < names.size(); i++) {
            String page = names.get(i);
            int index = i;
            timeline.getKeyFrames().add(new javafx.animation.KeyFrame(javafx.util.Duration.millis(1500 + i * 1500L), e -> show(page)));
            timeline.getKeyFrames().add(new javafx.animation.KeyFrame(javafx.util.Duration.millis(2700 + i * 1500L), e -> {
                try {
                    java.nio.file.Files.createDirectories(dir);
                    javafx.scene.image.WritableImage img = stage.getScene().snapshot(null);
                    int w = (int) img.getWidth(), h = (int) img.getHeight();
                    java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                    javafx.scene.image.PixelReader reader = img.getPixelReader();
                    for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) out.setRGB(x, y, reader.getArgb(x, y));
                    javax.imageio.ImageIO.write(out, "png", dir.resolve(String.format("launcher_%02d_%s.png", index + 1, page)).toFile());
                } catch (IOException ex) {
                    LOGGER.error("Snapshot failed", ex);
                }
            }));
        }
        timeline.getKeyFrames().add(new javafx.animation.KeyFrame(javafx.util.Duration.millis(3500 + names.size() * 1500L), e -> Platform.exit()));
        timeline.play();
    }

    private VBox buildSidebar() {
        Label logo = new Label("V");
        logo.getStyleClass().add("logo-mark");
        Label name = new Label("Velora");
        name.getStyleClass().add("logo-text");
        Label sub = new Label("Launcher " + ctx.build.version());
        sub.getStyleClass().add("logo-sub");
        VBox brand = new VBox(2, name, sub);
        HBox header = new HBox(10, logo, brand);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(4, 0, 18, 0));

        VBox nav = new VBox(4);
        for (String page : pages.keySet()) {
            Button b = new Button(page);
            b.getStyleClass().add("nav-button");
            b.setMaxWidth(Double.MAX_VALUE);
            b.setOnAction(e -> show(page));
            navButtons.put(page, b);
            nav.getChildren().add(b);
        }
        Label legal = Ui.hint("Nicht offiziell von Mojang oder Microsoft.");
        VBox sidebar = new VBox(8, header, nav, Ui.spacer(), legal);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(20, 14, 16, 14));
        sidebar.setPrefWidth(200);
        return sidebar;
    }

    public void show(String page) {
        navButtons.forEach((k, b) -> b.getStyleClass().remove("active"));
        Button active = navButtons.get(page);
        if (active != null) active.getStyleClass().add("active");
        Node node = pages.get(page).get();
        ScrollPane scroll = new ScrollPane(node);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("page-scroll");
        content.getChildren().setAll(page.equals("Logs") || page.equals("Spielen") ? node : scroll);
    }

    private Node buildLogs() {
        Button copy = Ui.secondary("Kopieren", () -> {
            var cb = javafx.scene.input.Clipboard.getSystemClipboard();
            var cc = new javafx.scene.input.ClipboardContent();
            cc.putString(logBuffer.text());
            cb.setContent(cc);
        });
        Button clear = Ui.secondary("Leeren", logBuffer::clear);
        Button folder = Ui.secondary("Instanz-Ordner öffnen", () -> openPath(LauncherPaths.instance(ctx.settings.selectedProfile)));
        VBox page = Ui.page(Ui.title("Logs"), Ui.subtitle("Ausgabe des laufenden Spiels"), Ui.row(copy, clear, folder), logArea);
        VBox.setVgrow(logArea, javafx.scene.layout.Priority.ALWAYS);
        return page;
    }

    public void openPath(java.nio.file.Path path) {
        try {
            java.nio.file.Files.createDirectories(path);
        } catch (IOException ignored) {
            // best effort
        }
        getHostServices().showDocument(path.toUri().toString());
    }

    public void openUrl(String url) {
        getHostServices().showDocument(url);
    }

    public Stage stage() { return stage; }

    @Override
    public void stop() {
        ctx.settings.save();
        ctx.background.shutdownNow();
        Platform.exit();
    }
}
