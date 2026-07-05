@echo off
REM =========================================================================
REM  Script ini membuat APLIKASI WINDOWS (.exe) yang berdiri sendiri.
REM  Hasilnya bisa dibagikan ke siapapun - mereka TIDAK PERLU install Java
REM  atau apapun lagi, tinggal double-klik .exe di dalam folder yang dihasilkan.
REM
REM  CARA PAKAI (cukup dijalankan SEKALI, di komputer INI saja):
REM    1. Pastikan JDK 17 ke atas sudah terinstall di komputer ini
REM       (unduh gratis: https://adoptium.net -> pilih JDK, bukan JRE)
REM    2. Double-klik file .bat ini
REM    3. Tunggu sampai selesai -> akan muncul folder "PendataanMahasiswa-App"
REM    4. Zip folder "PendataanMahasiswa-App" itu, lalu bagikan ke pengguna
REM    5. Pengguna tinggal extract zip-nya lalu double-klik
REM       "PendataanMahasiswa.exe" di dalamnya. Selesai, tanpa install apapun.
REM =========================================================================

where jpackage >nul 2>nul
if errorlevel 1 (
    echo.
    echo [GAGAL] "jpackage" tidak ditemukan.
    echo Pastikan JDK 17+ sudah terinstall dan folder "bin"-nya ada di PATH.
    echo Unduh gratis di: https://adoptium.net
    pause
    exit /b 1
)

if exist "PendataanMahasiswa-App" rmdir /s /q "PendataanMahasiswa-App"

jpackage ^
  --type app-image ^
  --input . ^
  --main-jar PendataanMahasiswa.jar ^
  --main-class aplikasi.LoginForm ^
  --name PendataanMahasiswa ^
  --dest . ^
  --app-version 3.0 ^
  --vendor "Universitas Bina Sarana Informatika"

if errorlevel 1 (
    echo.
    echo [GAGAL] jpackage mengalami error. Lihat pesan di atas.
    pause
    exit /b 1
)

echo.
echo Menyalin db.properties ke dalam folder aplikasi...
copy /y db.properties "PendataanMahasiswa\db.properties" >nul

ren "PendataanMahasiswa" "PendataanMahasiswa-App"

echo.
echo =========================================================================
echo  SELESAI! Folder "PendataanMahasiswa-App" sudah siap dibagikan.
echo  Zip folder itu, kirim ke pengguna, mereka tinggal extract lalu
echo  double-klik PendataanMahasiswa.exe di dalamnya - tanpa install apapun.
echo =========================================================================
pause
