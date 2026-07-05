/*
 * Aplikasi Pendataan Mahasiswa
 * Universitas Bina Sarana Informatika
 */
package aplikasi;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Titik akses utama ke data aplikasi. Dulu class ini bicara langsung ke
 * SQLite/PostgreSQL lewat JDBC; sekarang di baliknya sudah diganti memakai
 * SupabaseClient (REST API + anon key, lihat SupabaseClient.java) supaya
 * aplikasi tidak pernah memegang password database mentah. Method-method di
 * sini sengaja dibuat setara dengan kebutuhan form (LoginForm, MainForm,
 * AdminForm) supaya perubahan di balik layar ini tidak mengubah cara form
 * memakainya.
 */
public class DatabaseHelper {

    /**
     * Memastikan akun default admin/user ada. Tabelnya sendiri (mahasiswa,
     * users, activity_log) HARUS sudah dibuat manual sekali lewat SQL Editor
     * di dashboard Supabase (lihat SETUP-SUPABASE.txt), karena REST API
     * tidak diizinkan membuat tabel baru (hanya CRUD data).
     */
    public static void initDatabase() {
        try {
            List<Map<String, Object>> existing = SupabaseClient.select("users", null);
            if (existing.isEmpty()) {
                Map<String, Object> admin = new LinkedHashMap<>();
                admin.put("username", "admin");
                admin.put("password", PasswordUtil.hash("admin123"));
                admin.put("role", "admin");
                admin.put("nama_lengkap", "Administrator");
                SupabaseClient.insert("users", admin);

                Map<String, Object> user = new LinkedHashMap<>();
                user.put("username", "user");
                user.put("password", PasswordUtil.hash("user123"));
                user.put("role", "user");
                user.put("nama_lengkap", "User Biasa");
                SupabaseClient.insert("users", user);
            }
        } catch (DbException e) {
            javax.swing.JOptionPane.showMessageDialog(null,
                    "Gagal menyiapkan database:\n" + e.getMessage()
                    + "\n\nPastikan tabel 'users' sudah dibuat lewat SQL Editor Supabase "
                    + "(lihat SETUP-SUPABASE.txt).",
                    "Error Database",
                    javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Mencatat aktivitas user/admin ke tabel activity_log.
     */
    public static void logActivity(String username, String aktivitas) {
        try {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("username", username);
            row.put("aktivitas", aktivitas);
            row.put("waktu", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            SupabaseClient.insert("activity_log", row);
        } catch (DbException e) {
            e.printStackTrace();
        }
    }

    // ==================================================================
    // LOGIN
    // ==================================================================

    /**
     * Cari user berdasarkan username. Return null kalau tidak ketemu.
     * Map hasil berisi key: username, password (hash), role, nama_lengkap.
     */
    public static Map<String, Object> findUserByUsername(String username) throws DbException {
        List<Map<String, Object>> rows = SupabaseClient.select("users", null, "username", username, -1);
        return rows.isEmpty() ? null : rows.get(0);
    }

    // ==================================================================
    // MAHASISWA
    // ==================================================================

    public static List<Map<String, Object>> getAllMahasiswa() throws DbException {
        return SupabaseClient.select("mahasiswa", "nama.asc");
    }

    public static void insertMahasiswa(String nim, String nama, String prodi) throws DbException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("nim", nim);
        row.put("nama", nama);
        row.put("prodi", prodi);
        SupabaseClient.insert("mahasiswa", row);
    }

    public static void updateMahasiswa(String originalNim, String nim, String nama, String prodi) throws DbException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("nim", nim);
        row.put("nama", nama);
        row.put("prodi", prodi);
        SupabaseClient.update("mahasiswa", "nim", originalNim, row);
    }

    public static void deleteMahasiswa(String nim) throws DbException {
        SupabaseClient.delete("mahasiswa", "nim", nim);
    }

    // ==================================================================
    // USERS (untuk tab "Kelola User" di AdminForm)
    // ==================================================================

    public static List<Map<String, Object>> getAllUsers() throws DbException {
        return SupabaseClient.select("users", "username.asc");
    }

    public static void insertUser(String username, String passwordPlain, String role, String namaLengkap) throws DbException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("username", username);
        row.put("password", PasswordUtil.hash(passwordPlain));
        row.put("role", role);
        row.put("nama_lengkap", namaLengkap);
        SupabaseClient.insert("users", row);
    }

    /**
     * Update user. Kalau passwordPlain kosong, password lama tidak diubah.
     */
    public static void updateUser(String originalUsername, String username, String namaLengkap,
            String role, String passwordPlain) throws DbException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("username", username);
        row.put("nama_lengkap", namaLengkap);
        row.put("role", role);
        if (passwordPlain != null && !passwordPlain.isEmpty()) {
            row.put("password", PasswordUtil.hash(passwordPlain));
        }
        SupabaseClient.update("users", "username", originalUsername, row);
    }

    public static void deleteUser(String username) throws DbException {
        SupabaseClient.delete("users", "username", username);
    }

    // ==================================================================
    // ACTIVITY LOG (untuk tab "Log Aktivitas" di AdminForm)
    // ==================================================================

    public static List<Map<String, Object>> getRecentLogs(int limit) throws DbException {
        return SupabaseClient.select("activity_log", "id.desc", null, null, limit);
    }

    // ==================================================================
    // STATISTIK (untuk tab "Statistik" di AdminForm)
    // ==================================================================

    /**
     * Hitung jumlah mahasiswa per program studi, diurutkan dari yang
     * terbanyak. Perhitungan dilakukan di sisi aplikasi (bukan lewat SQL
     * GROUP BY) karena lewat REST API kita hanya boleh SELECT data mentah.
     * Untuk jumlah data skala pendataan mahasiswa, ini tetap ringan.
     */
    public static Map<String, Integer> getStatistikPerProdi() throws DbException {
        List<Map<String, Object>> semua = getAllMahasiswa();
        Map<String, Integer> hasil = new TreeMap<>();
        for (Map<String, Object> row : semua) {
            String prodi = String.valueOf(row.get("prodi"));
            hasil.merge(prodi, 1, Integer::sum);
        }
        // urutkan dari yang terbanyak
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(hasil.entrySet());
        entries.sort((a, b) -> b.getValue() - a.getValue());
        Map<String, Integer> urut = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : entries) {
            urut.put(e.getKey(), e.getValue());
        }
        return urut;
    }
}
