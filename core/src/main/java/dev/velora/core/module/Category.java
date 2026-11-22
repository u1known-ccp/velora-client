package dev.velora.core.module;

public enum Category {
    HUD("HUD", "Anzeigen auf dem Bildschirm"),
    PVP("PvP", "Komfort-Funktionen für Kämpfe"),
    VISUAL("Visuals", "Optische Anpassungen"),
    PERFORMANCE("Performance", "Optimierungen für mehr FPS"),
    UTILITY("Utility", "Nützliche Werkzeuge");

    private final String displayName;
    private final String description;

    Category(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}
