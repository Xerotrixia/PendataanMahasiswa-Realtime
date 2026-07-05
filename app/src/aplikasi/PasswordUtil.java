/*
 * Aplikasi Pendataan Mahasiswa
 * Universitas Bina Sarana Informatika
 */
package aplikasi;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.io.UnsupportedEncodingException;

/**
 * Utility class untuk hashing password menggunakan SHA-256.
 * Password tidak pernah disimpan dalam bentuk teks biasa di database.
 */
public class PasswordUtil {

    /**
     * Mengubah password teks biasa menjadi hash SHA-256 (hex string).
     */
    public static String hash(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(password.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            throw new RuntimeException("Gagal melakukan hashing password", e);
        }
    }

    /**
     * Membandingkan password teks biasa dengan hash yang tersimpan.
     */
    public static boolean verify(String plainPassword, String storedHash) {
        return hash(plainPassword).equals(storedHash);
    }
}
