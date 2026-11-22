package dev.velora.launcher.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/** Small factory helpers for consistently styled controls. */
public final class Ui {
    private Ui() { }

    public static Label title(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("page-title");
        return l;
    }

    public static Label subtitle(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("page-subtitle");
        l.setWrapText(true);
        return l;
    }

    public static Label section(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("section");
        return l;
    }

    public static Label hint(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("hint");
        l.setWrapText(true);
        return l;
    }

    public static Button primary(String text, Runnable action) {
        return button(text, "primary", action);
    }

    public static Button secondary(String text, Runnable action) {
        return button(text, "secondary", action);
    }

    public static Button danger(String text, Runnable action) {
        return button(text, "danger", action);
    }

    private static Button button(String text, String style, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().add(style);
        b.setOnAction(e -> action.run());
        // never truncate button labels – long text next to them wraps instead
        b.setMinWidth(Region.USE_PREF_SIZE);
        return b;
    }

    public static VBox card(Node... children) {
        VBox box = new VBox(8, children);
        box.getStyleClass().add("card");
        box.setPadding(new Insets(14));
        return box;
    }

    public static HBox row(Node... children) {
        HBox box = new HBox(8, children);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    public static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        VBox.setVgrow(r, Priority.ALWAYS);
        return r;
    }

    public static VBox page(Node... children) {
        VBox box = new VBox(12, children);
        box.getStyleClass().add("page");
        box.setPadding(new Insets(28, 32, 28, 32));
        return box;
    }

    public static void error(Window owner, String header, String message) {
        Alert a = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        a.initOwner(owner);
        a.setHeaderText(header);
        a.setTitle("Velora Launcher");
        a.showAndWait();
    }

    public static void info(Window owner, String header, String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        a.initOwner(owner);
        a.setHeaderText(header);
        a.setTitle("Velora Launcher");
        a.showAndWait();
    }

    public static boolean confirm(Window owner, String header, String message) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        a.initOwner(owner);
        a.setHeaderText(header);
        a.setTitle("Velora Launcher");
        return a.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }
}
