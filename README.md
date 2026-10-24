# Clean Your Headunit — 0.1 prototype

Aplikasi HP Android untuk memeriksa dan menonaktifkan aplikasi opsional di head unit melalui ADB Wi-Fi. Memakai transport ADB dari source Clean Your TV lokal, dengan UI native ringan, bahasa Indonesia dan warna gelap/amber. Ini proyek terpisah; source Clean Your TV yang sedang dikerjakan tidak diubah.

## Dua APK

- **Clean Your Headunit** (`app`): pasang di HP Android 9+. Masukkan IP dan port ADB TCP head unit.
- **Headunit Helper** (`helper`): dipasang dari aplikasi HP melalui tombol **Pasang / perbarui Helper di head unit**, setelah ADB tersambung. APK Helper sudah dibundel di aplikasi HP. Bisa juga dipasang manual di head unit Android 8+. Memeriksa Shizuku lokal melalui API resminya.
- **Shizuku**: pasang dan jalankan di head unit, lalu berikan izin kepada Headunit Helper. Unduh dari https://shizuku.rikka.app/download/.

Alur: siapkan Shizuku dan koneksi ADB Wi-Fi head unit → hubungkan dari HP dan izinkan kunci ADB → pasang/buka Helper dari HP → berikan izin Shizuku di head unit → pemeriksaan Shizuku → pilih aplikasi → matikan. Tidak perlu memasang Shizuku di HP.

Shizuku di HP tidak memberikan akses ke head unit. Shizuku berjalan juga tidak membuktikan bahwa port ADB Wi-Fi terbuka. Aplikasi memeriksa handshake ADB, UID shell/root, identitas target dan status live Shizuku/izin Helper secara terpisah. Helper hanya menyediakan probe status baca-saja untuk UID shell/root/aplikasinya sendiri; bukan server shell terbuka.

## Batas versi pertama

- Mendukung ADB TCP klasik pada IP privat lokal, termasuk port 8555. Otorisasi menggunakan kunci RSA; lalu lintas ADB TCP klasik tidak memakai TLS. Belum mendukung pairing TLS Android 11+, USB OTG bootstrap, mDNS atau membuka port secara otomatis.
- Android 8–10 tanpa root memerlukan ADB yang sudah tersedia untuk aktivasi awal Shizuku. Pemasangan Shizuku saja tidak menyediakan jalur akses awal. Setelah reboot Shizuku mungkin perlu dijalankan lagi.
- Daftar vendor dibatasi keluarga 8227 dan berasal dari paket yang ditemukan pada unit pengguna. Ini bukan jaminan aman untuk setiap firmware 8227; pilihan awal kosong dan efek tiap paket dijelaskan. Perangkat lain hanya mendapat kandidat aplikasi umum yang terpasang.
- Tidak menyediakan diagnosis malware atau klaim peningkatan performa tanpa pengukuran.
- Google Play Services/GSF dilindungi karena microG juga dapat menggunakan nama paket tersebut. WebView/Chrome dilindungi agar sistem tidak kehilangan mesin web.
- Radio, CAN, MCU, Bluetooth, Shizuku, microG dan launcher/keyboard aktif dilindungi. Aplikasi tak dikenal tidak otomatis dimatikan.
- Tidak memasang launcher, mengganti Home, menghapus data, menghapus paket atau mengubah firmware.
- Aplikasi menonaktifkan dengan `pm disable-user --user 0` dan memeriksa hasil. Catatan disimpan sebelum perintah, terikat identitas perangkat. Jika koneksi terputus, hasil ditandai belum pasti; mutasi tidak diulang otomatis. Halaman **Aktifkan lagi** memakai catatan tersebut dan memeriksa status aktual.
- Kunci ADB HP disimpan di Android Keystore. Tidak ada akun, iklan atau telemetri aplikasi.
- APK prototipe memakai debug signing; belum rilis publik. Pemeriksaan unit/build tidak membuktikan koneksi HP–head unit fisik.

## Build

JDK 17 atau 21, Android SDK 34. Pada PowerShell:

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:assembleDebug :helper:assembleDebug :app:testDebugUnitTest
```

Hasil di `app/build/outputs/apk/debug/` dan `helper/build/outputs/apk/debug/`.

## Sumber dan lisensi

- Clean Your TV app contributors: `LICENSE` (MIT).
- Transport ADB turunan dadb: `ADB-NOTICE`, komentar lisensi Apache 2.0 di source dan `APACHE-2.0.txt`.
- Shizuku API/provider 13.1.5: https://github.com/RikkaApps/Shizuku-API (Apache 2.0).
- Panduan aktivasi: https://shizuku.rikka.app/guide/setup/.

UI belum identik dengan semua layar Clean Your TV; versi awal menyiapkan koneksi, gerbang Shizuku, daftar debloat dan pengaktifan kembali dalam tiga langkah.
