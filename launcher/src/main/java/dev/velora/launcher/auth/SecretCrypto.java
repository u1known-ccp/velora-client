package dev.velora.launcher.auth;

import com.sun.jna.platform.win32.Crypt32Util;
import dev.velora.launcher.LauncherPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

/**
 * Encrypts tokens at rest.
 * <ul>
 *   <li>Windows: DPAPI (CryptProtectData) – bound to the Windows user account, no key on disk.</li>
 *   <li>Other systems: AES-256-GCM with a random key stored in a file only readable by the owner.</li>
 * </ul>
 * Ciphertexts carry a prefix ("dpapi:" / "aes:") so the format can evolve.
 */
public final class SecretCrypto {
    private static final Logger LOGGER = LoggerFactory.getLogger("Velora/Crypto");
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    private static final byte[] ENTROPY = "velora-launcher-v1".getBytes(StandardCharsets.UTF_8);
    private static final SecureRandom RANDOM = new SecureRandom();

    private SecretCrypto() { }

    public static String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) return "";
        byte[] data = plain.getBytes(StandardCharsets.UTF_8);
        try {
            if (WINDOWS) {
                byte[] enc = Crypt32Util.cryptProtectData(data, ENTROPY, 0, "Velora", null);
                return "dpapi:" + Base64.getEncoder().encodeToString(enc);
            }
            SecretKey key = fileKey();
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] enc = c.doFinal(data);
            ByteBuffer buf = ByteBuffer.allocate(iv.length + enc.length).put(iv).put(enc);
            return "aes:" + Base64.getEncoder().encodeToString(buf.array());
        } catch (GeneralSecurityException | IOException | RuntimeException e) {
            // never fall back to plain text
            throw new IllegalStateException("Token-Verschlüsselung fehlgeschlagen", e);
        }
    }

    public static String decrypt(String stored) {
        if (stored == null || stored.isEmpty()) return "";
        try {
            if (stored.startsWith("dpapi:")) {
                byte[] enc = Base64.getDecoder().decode(stored.substring(6));
                return new String(Crypt32Util.cryptUnprotectData(enc, ENTROPY, 0, null), StandardCharsets.UTF_8);
            }
            if (stored.startsWith("aes:")) {
                byte[] all = Base64.getDecoder().decode(stored.substring(4));
                Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
                c.init(Cipher.DECRYPT_MODE, fileKey(), new GCMParameterSpec(128, all, 0, 12));
                return new String(c.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
            }
        } catch (GeneralSecurityException | IOException | RuntimeException e) {
            LOGGER.warn("Could not decrypt stored secret (different user/machine?) – login required");
        }
        return "";
    }

    private static SecretKey fileKey() throws IOException, GeneralSecurityException {
        Path file = LauncherPaths.keyFile();
        if (Files.isRegularFile(file)) {
            byte[] raw = Base64.getDecoder().decode(Files.readString(file).trim());
            return new SecretKeySpec(raw, "AES");
        }
        KeyGenerator gen = KeyGenerator.getInstance("AES");
        gen.init(256, RANDOM);
        SecretKey key = gen.generateKey();
        Files.createDirectories(file.getParent());
        Files.writeString(file, Base64.getEncoder().encodeToString(key.getEncoded()));
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // non-POSIX file system
        }
        return key;
    }
}
