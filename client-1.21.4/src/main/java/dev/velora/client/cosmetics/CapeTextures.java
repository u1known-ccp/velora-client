package dev.velora.client.cosmetics;

import dev.velora.core.cosmetics.Cosmetic;
import dev.velora.core.cosmetics.CosmeticCatalog;
import dev.velora.core.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Generates cape textures procedurally from a cosmetic's colors and pattern (64x32 vanilla cape layout).
 * No image assets are shipped; textures are created lazily once and cached.
 */
public final class CapeTextures {
    private static final Map<String, Identifier> CACHE = new HashMap<>();

    private CapeTextures() { }

    public static Identifier get(String cosmeticId) {
        Identifier cached = CACHE.get(cosmeticId);
        if (cached != null) return cached;
        Cosmetic cosmetic = CosmeticCatalog.byId(cosmeticId).orElse(null);
        if (cosmetic == null) return null;
        NativeImage image = new NativeImage(64, 32, true);
        paint(image, cosmetic);
        Identifier id = Identifier.of("velora", "cosmetics/cape/" + cosmeticId);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(image));
        CACHE.put(cosmeticId, id);
        return id;
    }

    /** Vanilla cape UV: front face at (1,1) 10x16, back (12,1), sides and edges around it. Elytra uses (22..46, 0..22). */
    private static void paint(NativeImage img, Cosmetic c) {
        int a = c.primaryColor();
        int b = c.secondaryColor();
        Random random = new Random(c.id().hashCode());
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 64; x++) {
                float fy = (y % 17) / 16f;
                int color = switch (c.pattern()) {
                    case "stripes" -> (((x + y) / 3) % 2 == 0) ? a : b;
                    case "waves" -> {
                        double wave = Math.sin((x * 0.6) + (y * 0.35)) * 0.5 + 0.5;
                        yield ColorUtil.lerp(a, b, (float) (wave * 0.75 + fy * 0.25));
                    }
                    case "stars" -> ColorUtil.lerp(a, ColorUtil.darken(a, 0.35f), fy);
                    default -> ColorUtil.lerp(a, b, fy);
                };
                img.setColorArgb(x, y, color | 0xFF000000);
            }
        }
        if (c.pattern().equals("stars")) {
            for (int i = 0; i < 26; i++) {
                int x = random.nextInt(64);
                int y = random.nextInt(32);
                img.setColorArgb(x, y, b | 0xFF000000);
            }
        }
        // emblem on the outer face (u 1..10, v 1..16 – the side visible from behind the player): a "V" chevron
        int emblem = ColorUtil.brighten(b, 0.6f) | 0xFF000000;
        int[][] chevron = {{0, 0}, {1, 1}, {2, 2}, {3, 3}, {4, 3}, {5, 2}, {6, 1}, {7, 0}, {0, 1}, {7, 1}, {1, 2}, {6, 2}};
        for (int[] p : chevron) {
            img.setColorArgb(2 + p[0], 5 + p[1], emblem);
        }
        // darker top and bottom edge on the outer face
        int border = ColorUtil.darken(a, 0.5f) | 0xFF000000;
        for (int x = 1; x < 11; x++) {
            img.setColorArgb(x, 1, border);
            img.setColorArgb(x, 16, border);
        }
    }

    /** UV of the outer cape face for GUI previews. */
    public static final int PREVIEW_U = 1;
    public static final int PREVIEW_V = 1;
    public static final int PREVIEW_W = 10;
    public static final int PREVIEW_H = 16;
}
