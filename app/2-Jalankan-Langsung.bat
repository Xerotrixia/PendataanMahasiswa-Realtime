@echo off
REM Jalankan aplikasi langsung - HANYA berfungsi kalau Java 11+ sudah
REM terinstall di komputer ini. Kalau belum, gunakan cara di
REM "1-BUAT-APLIKASI-EXE.bat" untuk membuat versi .exe yang tidak butuh Java.

where java >nul 2>nul
if errorlevel 1 (
    echo.
    echo [INFO] Java belum terinstall di komputer ini.
    echo Silakan pakai "1-BUAT-APLIKASI-EXE.bat" ^(sekali saja^) untuk membuat
    echo versi .exe yang tidak butuh Java sama sekali, lalu bagikan hasilnya.
    echo.
    pause
    exit /b 1
)

start "" javaw -jar "PendataanMahasiswa.jar"
