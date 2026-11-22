package dev.velora.launcher.ui;

import dev.velora.core.backend.NewsItem;
import dev.velora.launcher.LauncherContext;
import dev.velora.launcher.VeloraLauncherApp;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.List;

public final class NewsView {
    private final LauncherContext ctx;
    private final VeloraLauncherApp app;

    public NewsView(LauncherContext ctx, VeloraLauncherApp app) {
        this.ctx = ctx;
        this.app = app;
    }

    public Node build() {
        VBox list = new VBox(12);
        Label loading = Ui.hint("Lade Neuigkeiten…");
        list.getChildren().add(loading);
        ctx.background.submit(() -> {
            List<NewsItem> items = dev.velora.launcher.service.NewsService.load(ctx.backend());
            Platform.runLater(() -> {
                list.getChildren().clear();
                if (items.isEmpty()) list.getChildren().add(Ui.hint("Keine Neuigkeiten verfügbar."));
                for (NewsItem item : items) {
                    Label t = new Label(item.title());
                    t.getStyleClass().add("news-title");
                    Label body = new Label(item.body());
                    body.setWrapText(true);
                    body.getStyleClass().add("news-body");
                    VBox card = Ui.card(t, Ui.hint(item.date()), body);
                    if (!item.url().isEmpty()) {
                        Hyperlink link = new Hyperlink("Mehr erfahren");
                        link.setOnAction(e -> app.openUrl(item.url()));
                        card.getChildren().add(link);
                    }
                    list.getChildren().add(card);
                }
            });
        });
        String source = ctx.backend().isEnabled() ? "Quelle: " + ctx.backend().getBaseUrl() : "Offline – mitgelieferte Neuigkeiten";
        return Ui.page(Ui.title("News"), Ui.subtitle(source), list);
    }
}
