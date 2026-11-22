package dev.velora.core.cosmetics;

/**
 * Definition of a cosmetic. {@code free} cosmetics are always unlocked; all others must be granted by the backend.
 * {@code primaryColor}/{@code secondaryColor} drive the procedural texture generation in the client.
 */
public record Cosmetic(String id, String name, CosmeticType type, String description, boolean free,
                       int primaryColor, int secondaryColor, String pattern) {
}
