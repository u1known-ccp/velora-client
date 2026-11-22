package dev.velora.launcher.ui;

import dev.velora.core.backend.NewsItem;
import dev.velora.launcher.LauncherContext;
import dev.velora.launcher.VeloraLauncherApp;
import dev.velora.launcher.auth.Account;
import dev.velora.launcher.config.LauncherSettings;
import dev.velora.launcher.service.NewsService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

/** Home page: hero, latest news, profile/account selection and the big play button. */
public final class PlayView {
    private final LauncherContext ctx;
    private final VeloraLauncherApp app;
    private final LaunchController launcher;

    public PlayView(LauncherContext ctx, VeloraLauncherApp app, LaunchController launcher) {
        this.ctx = ctx;
        this.app = app;
        this.launcher = launcher;
    }

    public Node build() {
        // hero
        Label kicker = new Label("MINECRAFT " + String.join(" · ", ctx.build.versions().stream().map(v -> v.minecraft()).toList()) + " · FABRIC");
        kicker.getStyleClass().add("hero-kicker");
        Label title = new Label("Velora Client");
        title.getStyleClass().add("hero-title");
        Label text = new Label("Leicht, schnell, deins. HUD-Editor, über 35 Mods, Profile und Performance-Optimierungen – "
                + "das Velora-Menü öffnest du im Spiel mit Rechts-Shift.");
        text.getStyleClass().add("hero-text");
        text.setWrapText(true);
        Label update = new Label();
        update.getStyleClass().add("update-notice");
        update.textProperty().bind(launcher.updateNotice);
        update.visibleProperty().bind(launcher.updateNotice.isNotEmpty());
        VBox hero = new VBox(8, kicker, title, text, update);
        hero.getStyleClass().add("hero");
        hero.setPadding(new Insets(36, 36, 30, 36));

        // news preview
        VBox news = new VBox(10);
        news.setPadding(new Insets(0, 32, 0, 32));
        Label newsTitle = Ui.section("Neuigkeiten");
        news.getChildren().add(newsTitle);
        HBox newsRow = new HBox(12);
        news.getChildren().add(newsRow);
        ctx.background.submit(() -> {
            List<NewsItem> items = NewsService.load(ctx.backend());
            Platform.runLater(() -> {
                for (NewsItem item : items.subList(0, Math.min(3, items.size()))) {
                    Label t = new Label(item.title());
                    t.getStyleClass().add("news-title");
                    Label d = new Label(item.date());
                    d.getStyleClass().add("hint");
                    Label b = new Label(item.body());
                    b.setWrapText(true);
                    b.getStyleClass().add("news-body");
                    b.setMaxHeight(64);
                    VBox card = Ui.card(t, d, b);
                    card.setPrefWidth(260);
                    HBox.setHgrow(card, Priority.ALWAYS);
                    newsRow.getChildren().add(card);
                }
            });
        });

        // bottom bar
        ComboBox<String> profileBox = new ComboBox<>();
        for (LauncherSettings.GameProfile p : ctx.settings.profiles) profileBox.getItems().add(p.name);
        profileBox.setValue(ctx.settings.selectedProfile().name);
        profileBox.setOnAction(e -> {
            ctx.settings.selectedProfile = profileBox.getValue();
            ctx.settings.save();
        });
        VBox profileCol = new VBox(4, Ui.hint("Profil"), profileBox);

        ComboBox<Account> accountBox = new ComboBox<>();
        accountBox.getItems().addAll(ctx.usableAccounts());
        ctx.selectedAccount().ifPresent(accountBox::setValue);
        accountBox.setPromptText("Kein Konto");
        accountBox.setOnAction(e -> {
            if (accountBox.getValue() != null) {
                ctx.settings.selectedAccount = accountBox.getValue().id();
                ctx.settings.save();
            }
        });
        VBox accountCol = new VBox(4, Ui.hint("Konto"), accountBox);

        Label status = new Label();
        status.getStyleClass().add("status");
        status.textProperty().bind(launcher.status);
        ProgressBar bar = new ProgressBar();
        bar.progressProperty().bind(launcher.progress);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.visibleProperty().bind(launcher.busy);
        VBox statusCol = new VBox(6, status, bar);
        statusCol.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(statusCol, Priority.ALWAYS);

        Button play = new Button("SPIELEN");
        play.getStyleClass().add("play-button");
        play.disableProperty().bind(launcher.busy.or(launcher.running));
        play.setOnAction(e -> launcher.play(null));
        Button stop = Ui.danger("Beenden", launcher::kill);
        stop.visibleProperty().bind(launcher.running);
        stop.managedProperty().bind(launcher.running);
        Button cancel = Ui.secondary("Abbrechen", launcher::cancel);
        cancel.visibleProperty().bind(launcher.busy);
        cancel.managedProperty().bind(launcher.busy);

        HBox bottom = new HBox(16, accountCol, profileCol, statusCol, cancel, stop, play);
        bottom.setAlignment(Pos.CENTER_LEFT);
        bottom.getStyleClass().add("bottom-bar");
        bottom.setPadding(new Insets(16, 24, 16, 24));

        BorderPane root = new BorderPane();
        VBox top = new VBox(22, hero, news);
        root.setTop(top);
        root.setBottom(bottom);
        StackPane wrapper = new StackPane(root);
        wrapper.getStyleClass().add("play-page");
        return wrapper;
    }
}
