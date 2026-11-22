package dev.velora.core.cosmetics;

public enum CosmeticType {
    CAPE("Capes"),
    WINGS("Wings"),
    HAT("Hats"),
    EMOTE("Emotes"),
    CUSTOM("Custom");

    private final String displayName;

    CosmeticType(String displayName) { this.displayName = displayName; }

    public String getDisplayName() { return displayName; }
}
