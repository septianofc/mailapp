# Panduan Build & Pengujian — Mail Septian Support (`MSFR_RATNA_2245`)

## 1. Identitas Aplikasi
- **Nama Aplikasi:** Mail Septian Support
- **Versi / Code:** `MSFR_RATNA_2245` (`versionCode = 2245`)
- **Target WebView:** `https://septianfr.web.id/mail/support/login.php`
- **Minimum SDK / Target SDK:** Android 7.0 (API 24) / Android 16 (API 36)

---

## 2. Mode Release & Build Otomatis (GitHub Actions)
Workflow CI pada `.github/workflows/build-apk.yml` kini langsung membuild dan menandatangani **APK Release** (`:app:assembleRelease`) dengan R8 minification, resource shrinking, obfuscation, dan penghapusan log (`ENABLE_LOGS = false`):

1. Push repository ke GitHub (branch `main` atau `master`), atau jalankan manual dari tab **Actions > Build Mail Septian Support APK > Run workflow**.
2. Runner CI akan otomatis:
   - Menjalankan seluruh unit test & Robolectric UI test (`:app:testDebugUnitTest`).
   - Menyiapkan keystore penandatanganan Release secara otomatis:
     - Jika Anda sudah mengisi GitHub Secrets (`KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD` dengan `keyAlias = upload`), CI menggunakan keystore permanen tersebut agar update APK berikutnya bisa menimpa versi sebelumnya.
     - Jika GitHub Secrets belum diisi, CI otomatis membuat keystore PKCS12 sementara di runner (`$RUNNER_TEMP`) menggunakan `keytool` + password acak sehingga Anda tidak perlu membuat keystore manual.
   - Membuild dan menandatangani `app/build/outputs/apk/release/app-release.apk`.
3. Unduh artifact **`Mail-Septian-Support-MSFR_RATNA_2245-release-apk`** dari hasil run Actions.
4. **Penting saat instalasi di HP:**
   - GitHub Actions membungkus artifact dalam file `.zip` — **ekstrak `.zip` terlebih dahulu** lalu instal file `app-release.apk` di dalamnya.
   - Jika sebelumnya pernah menginstal versi **Debug**, **uninstall versi Debug terlebih dahulu** sebelum menginstal APK Release agar tidak terjadi konflik tanda tangan aplikasi.

---

## 3. Build & Uji Lokal (Gradle CLI)
- **Menjalankan Unit & Robolectric Tests:**
  ```bash
  gradle :app:testDebugUnitTest
  ```
- **Membuat APK Release Tertandatangani:**
  ```bash
  KEYSTORE_PATH=/path/to/keystore.jks STORE_PASSWORD=... KEY_PASSWORD=... gradle :app:assembleRelease
  ```
  Lokasi APK: `app/build/outputs/apk/release/app-release.apk`

---

## 4. Ringkasan Arsitektur Keamanan
- **Anti Screenshot (`FLAG_SECURE`):** Tersedia di panel Pengaturan (pojok kanan bawah), disimpan persisten menggunakan Jetpack DataStore.
- **Kunci Sidik Jari / PIN (`BiometricPrompt`):** Mendukung toggle ON/OFF di panel Pengaturan dengan nilai bawaan **mengikuti pengaturan kunci layar sistem**. Menggunakan autentikasi biometrik & `DEVICE_CREDENTIAL` tanpa menyimpan PIN plaintext. Membatalkan prompt atau menekan Keluar akan menutup aplikasi.
- **Blokir Otomatis Integritas Perangkat (Tanpa Toggle):** Memeriksa indikator root/Magisk/superuser, Developer Options, dan USB Debugging secara otomatis. Jika aktif, aplikasi menampilkan halaman blokir penuh (*Akses Aplikasi Diblokir*). Deteksi sisi klien bersifat heuristik dan **tidak mengklaim akurasi 100%**.
- **WebView Tanpa Jejak Browser:** Allowlist ketat host `septianfr.web.id` via HTTPS, memblokir HTTP, menonaktifkan `allowFileAccess`/`allowContentAccess`/mixed content, menolak SSL error (`handler.cancel()`), tanpa JavaScript bridge, cookie pihak pertama saja, tanpa progress bar atas, dan seluruh error jaringan/HTTP/server/SSL/Cloudflare ditangani overlay native tanpa mengekspos URL mentah.
- **Proteksi APK Release:** R8 shrinking, minification, obfuscation, serta penghapusan log debug (`-assumenosideeffects`). Langkah ini menyulitkan analisis statis, namun **tidak membuat APK mustahil untuk didecompile**.
