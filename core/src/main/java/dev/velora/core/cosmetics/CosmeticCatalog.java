package dev.velora.core.cosmetics;

import java.util.List;
import java.util.Optional;

/** Built-in cosmetics. All textures are generated procedurally by the client – no external assets. */
public final class CosmeticCatalog {
    private static final List<Cosmetic> BUILTIN = List.of(
            new Cosmetic("velora_classic", "Velora Classic", CosmeticType.CAPE, "Das Standard-Cape in Velora-Violett", true, 0xFF7C5CFF, 0xFF2B1F66, "gradient"),
            new Cosmetic("midnight", "Midnight", CosmeticType.CAPE, "Tiefblau mit Sternen", true, 0xFF0B1E3F, 0xFFE8F0FF, "stars"),
            new Cosmetic("ember", "Ember", CosmeticType.CAPE, "Glühender Farbverlauf", true, 0xFFFF6A2B, 0xFF5A0D0D, "gradient"),
            new Cosmetic("ocean", "Ocean", CosmeticType.CAPE, "Wellenmuster", true, 0xFF1FB6C9, 0xFF0A3D62, "waves"),
            new Cosmetic("forest", "Forest", CosmeticType.CAPE, "Diagonale Streifen in Grün", true, 0xFF2ECC71, 0xFF14532D, "stripes"),
            new Cosmetic("founder", "Founder", CosmeticType.CAPE, "Nur für Unterstützer der ersten Stunde", false, 0xFFFFD166, 0xFF3A2A00, "stars"),
            new Cosmetic("aurora", "Aurora", CosmeticType.CAPE, "Polarlicht-Verlauf (Premium)", false, 0xFF50FFB0, 0xFF6A00FF, "waves")
    );

    private CosmeticCatalog() { }

    public static List<Cosmetic> all() { return BUILTIN; }

    public static List<Cosmetic> ofType(CosmeticType type) {
        return BUILTIN.stream().filter(c -> c.type() == type).toList();
    }

    public static Optional<Cosmetic> byId(String id) {
        return BUILTIN.stream().filter(c -> c.id().equals(id)).findFirst();
    }
}
