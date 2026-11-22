package dev.velora.core.backend;

/** Latest available release of a component ("client-1.21.4", "launcher"). */
public record UpdateInfo(String component, String version, String url, String sha256, String changelog) {

    /** Compares dotted numeric versions ("0.1.10" > "0.1.9"). Non-numeric parts compare as 0. */
    public static int compareVersions(String a, String b) {
        String[] pa = a.split("[.\\-+]");
        String[] pb = b.split("[.\\-+]");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int x = i < pa.length ? parse(pa[i]) : 0;
            int y = i < pb.length ? parse(pb[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
