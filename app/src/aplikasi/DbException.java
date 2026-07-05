/*
 * Aplikasi Pendataan Mahasiswa
 * Universitas Bina Sarana Informatika
 */
package aplikasi;

/**
 * Exception khusus untuk error yang berasal dari komunikasi ke Supabase.
 * Dipakai supaya kode pemanggil (LoginForm, MainForm, AdminForm) tidak perlu
 * tahu detail HTTP/JSON di baliknya - cukup tangkap DbException seperti dulu
 * menangkap SQLException.
 */
public class DbException extends Exception {

    private final boolean duplicate;

    public DbException(String message, boolean duplicate) {
        super(message);
        this.duplicate = duplicate;
    }

    /**
     * True kalau error ini karena data duplikat (mis. NIM/username yang
     * sudah ada), supaya pesan ke user bisa lebih spesifik.
     */
    public boolean isDuplicate() {
        return duplicate;
    }
}
