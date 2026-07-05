-- =========================================================================
-- UPDATE KEAMANAN: perketat akses langsung anon key ke tabel.
-- Jalankan SETELAH Edge Function "app-api" berhasil di-deploy (lihat
-- app-api-edge-function.ts), supaya aplikasi tidak "putus" di tengah jalan.
--
-- CARA JALANKAN: dashboard Supabase -> SQL Editor -> New query -> paste
-- semua ini -> Run.
-- =========================================================================

-- Hapus policy lama yang mengizinkan SEMUA orang CRUD penuh
DROP POLICY IF EXISTS "app akses users" ON users;
DROP POLICY IF EXISTS "app akses mahasiswa" ON mahasiswa;
DROP POLICY IF EXISTS "app akses activity_log" ON activity_log;

-- ---------------------------------------------------------------------
-- TABEL users: publik TIDAK BOLEH baca/tulis apapun langsung.
-- Semua akses (login, kelola user) wajib lewat Edge Function "app-api",
-- yang pakai service_role key (otomatis bisa lewat RLS, tidak perlu
-- policy tambahan untuk service_role).
-- ---------------------------------------------------------------------
-- (tidak ada policy dibuat untuk anon -> otomatis semua akses anon ditolak)

-- ---------------------------------------------------------------------
-- TABEL mahasiswa: publik BOLEH baca (supaya aplikasi tetap bisa
-- menampilkan daftar mahasiswa), tapi TIDAK BOLEH tulis langsung -
-- tambah/ubah/hapus wajib lewat Edge Function.
-- ---------------------------------------------------------------------
CREATE POLICY "publik boleh baca mahasiswa" ON mahasiswa
    FOR SELECT USING (true);

-- ---------------------------------------------------------------------
-- TABEL activity_log: publik BOLEH baca (supaya tab "Log Aktivitas" di
-- AdminForm tetap bisa menampilkan riwayat), tapi TIDAK BOLEH tulis
-- langsung - hanya Edge Function yang boleh menambah baris log.
-- ---------------------------------------------------------------------
CREATE POLICY "publik boleh baca activity_log" ON activity_log
    FOR SELECT USING (true);

-- =========================================================================
-- SELESAI. Sekarang:
--   - Anon key publik cuma bisa BACA data mahasiswa & log aktivitas.
--   - Anon key publik TIDAK BISA baca/tulis apapun ke tabel users
--     (termasuk tidak bisa lihat hash password siapapun).
--   - Semua tambah/ubah/hapus data (mahasiswa maupun user) HANYA bisa
--     lewat Edge Function "app-api", yang mewajibkan login valid dulu.
--   - Kalaupun anon key ini bocor/di-ekstrak dari file .exe/.jar, orang
--     yang pegang itu PALING BANTER cuma bisa BACA data mahasiswa &
--     log aktivitas - tidak bisa menghapus, mengubah, atau membuat
--     akun admin baru untuk dirinya sendiri.
-- =========================================================================
