# Clean Your Headunit — 0.2 prototype

Aplikasi Android untuk memeriksa dan menonaktifkan aplikasi opsional di head unit. Ada mode langsung di perangkat dan mode pengendali dari HP, ditambah launcher DOT DRIVE. Semua memakai UI native; tanpa WebView untuk menampilkan antarmuka.

## Pasang langsung di head unit

Build modul `headunit`, lalu pasang `headunit-debug.apk` di head unit Android 8+. Aplikasi **Clean Your Headunit** bisa melakukan debloat lokal melalui Shizuku yang sudah berjalan dan diizinkan, atau root yang diberikan pengguna. Tidak perlu terminal emulator atau HP untuk menjalankan debloat setelah akses tersebut tersedia.

- Halaman utama menunjukkan model, versi Android yang dilaporkan sistem, API dan akses yang dipilih.
- **Gunakan Shizuku** meminta izin resmi dan menjalankan service shell melalui API Shizuku. **Gunakan akses root** meminta izin melalui `su`; root tidak dibuat oleh aplikasi.
- **Coba aktifkan ADB Wi-Fi** mencoba menyalakan ADB TCP port 8555. Root paling mungkin mengizinkan perubahan; shell Shizuku tetap mengikuti izin properti/SELinux firmware. Hasil perintah saja tidak dianggap bukti berhasil: aplikasi memeriksa balasan protokol ADB lokal.
- Listener lokal merespons bukan bukti koneksi HP berhasil: Wi-Fi, firewall dan otorisasi kunci ADB masih harus diuji dari HP.
- **Matikan ADB jaringan** mencoba menutup ADB TCP dan memeriksa port 5555/8555; USB debugging tidak diubah oleh tindakan ini.
- Tanpa root atau Shizuku aktif, aplikasi menyediakan pemeriksaan listener dan pintasan pengaturan; tidak menerobos izin sistem atau memasang komponen debugging yang tidak tersedia pada firmware. Tidak ada jaminan tetap aktif setelah reboot.
- Daftar kandidat dan proteksi dibagikan dengan aplikasi HP. Launcher dan keyboard aktif diperiksa lagi sebelum mutasi. Status hasil diperiksa, catatan disimpan sebelum menonaktifkan, dan ada **Aktifkan lagi**.
- Binary ini prototipe debug; akses root/Shizuku, aktivasi ADB dan UI belum diuji pada head unit fisik.

## DOT DRIVE 1.1

Build modul `launcher`, lalu pasang `launcher-debug.apk`. Package `com.dotdrive.launcher` dipertahankan agar bisa memperbarui DOT DRIVE sebelumnya dengan tanda tangan yang sama. Pilih sebagai aplikasi Home melalui pengaturan sistem.

Desain mengikuti [Vibe Nothing UI](https://github.com/wangbh030722/vibe-nothing-ui-design): permukaan monokrom, jam Doto dot-matrix, teks Geist, label Geist Mono, bidang titik jarang di belakang kartu, radius kartu 8dp, tombol hitam-putih dan tanpa bayangan. Font lokal beserta lisensi OFL disertakan. Ini adaptasi native, bukan pemuatan HTML/CSS.

Tahan kartu favorit untuk mengganti aplikasinya. Pilihan tersimpan pada posisi yang dipilih; aplikasi yang hilang memakai saran otomatis. Tombol **Perangkat** membuka Clean Your Headunit lokal bila terpasang. Launcher tidak meminta izin root, Shizuku atau jaringan.

## Mode pengendali dari HP (0.1)

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
.\gradlew.bat :headunit:assembleDebug :launcher:assembleDebug :headunit:testDebugUnitTest :launcher:testDebugUnitTest
```

Hasil di `<modul>/build/outputs/apk/debug/`. Modul `headunit` adalah aplikasi langsung di head unit, `app` adalah pengendali HP, `helper` adalah pendamping pengendali HP, dan `launcher` adalah DOT DRIVE.

## Sumber dan lisensi

- Clean Your TV app contributors: `LICENSE` (MIT).
- Transport ADB turunan dadb: `ADB-NOTICE`, komentar lisensi Apache 2.0 di source dan `APACHE-2.0.txt`.
- Shizuku API/provider 13.1.5: https://github.com/RikkaApps/Shizuku-API (Apache 2.0).
- Panduan aktivasi: https://shizuku.rikka.app/guide/setup/.

Pengujian unit mencakup daftar proteksi, batas profil vendor, perintah yang diizinkan, penolakan injeksi perintah, autentikasi ADB dan penempatan favorit. Build/lint/unit test tidak menggantikan pengujian fisik root, Shizuku, jaringan, CAN bus, audio atau Home.
