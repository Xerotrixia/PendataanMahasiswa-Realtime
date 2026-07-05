# Pendataan Mahasiswa App

Aplikasi desktop untuk pendataan mahasiswa yang dikemas untuk penggunaan di Windows.

## Tentang Aplikasi

Folder ini berisi sumber daya dan skrip untuk membangun serta menjalankan aplikasi pendataan mahasiswa. Karena ada aturan pengabaian pada file artefak build dan konfigurasi sensitif, beberapa file hasil build tidak disertakan di repository.

## Struktur Folder

- app/ — berisi skrip pembuat aplikasi, skrip jalankan langsung, dan konfigurasi paket build
- runtime/ — runtime Java yang diperlukan oleh aplikasi
- db.properties — file konfigurasi database lokal yang tidak disertakan ke repository
- PendataanMahasiswa.exe — artefak hasil build yang biasanya dibuat secara lokal
- PendataanMahasiswa.jar — artefak hasil build yang biasanya dibuat secara lokal

## Cara Menjalankan

### 1. Menjalankan aplikasi yang sudah tersedia lokal

Jika file aplikasi sudah ada di folder ini, jalankan langsung melalui:

- PendataanMahasiswa.exe

### 2. Menjalankan melalui Java

Jika Java sudah terinstal di komputer, jalankan:

- app/2-Jalankan-Langsung.bat

### 3. Membuat paket aplikasi sendiri

Untuk membuat versi aplikasi yang mandiri, jalankan:

- app/1-BUAT-APLIKASI-EXE.bat

Persyaratan:

- JDK 17 atau lebih tinggi
- Alat jpackage tersedia di PATH sistem

## Catatan Penting

- File db.properties bersifat lokal dan tidak boleh disertakan ke repository.
- Pastikan konfigurasi database sudah benar sebelum menjalankan aplikasi.
- Jika aplikasi tidak berjalan, cek koneksi database dan keberadaan runtime Java yang sesuai.
- Hasil build seperti .exe dan .jar sebaiknya dibuat di mesin lokal dan tidak dikomit ke repository.
