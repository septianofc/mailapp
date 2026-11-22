# Panduan Build & Pengujian — Mail Septian Support (`MSFR_RATNA_2245`)

## 1. Identitas Aplikasi
- **Nama Aplikasi:** Mail Septian Support
- **Versi / Code:** `MSFR_RATNA_2245` (`versionCode = 2245`)
- **Target WebView:** `https://septianfr.web.id/mail/support/login.php`
- **Minimum SDK / Target SDK:** Android 7.0 (API 24) / Android 16 (API 36)

---

## 2. Build via Android Studio
1. Buka folder project ini di **Android Studio** (JDK 17 direkomendasikan).
2. Tunggu Gradle Sync selesai.
3. Pilih build variant:
   - **`debug`** untuk pengujian harian (logging debug aktif hanya di build debug).
   - **`release`** untuk produksi (R8 minification, resource shrinking, obfuscation, dan penghapusan log aktif).
4. Untuk membuat APK Debug: **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
   Output berada di: `app/build/outputs/apk/debug/app-debug.apk`.

---

## 3. Build & Uji via Terminal (Gradle CLI)
- **Menjalankan Unit & Robolectric Tests:**
  ```bash
  gradle :app:testDebugUnitTest
  ```
- **Membuat APK Debug:**
  ```bash
  gradle :app:assembleDebug
  ```
  Lokasi APK: `app/build/outputs/apk/debug/app-debug.apk`
- **Membuat APK Release (memerlukan environment keystore):**
  ```bash
  KEYSTORE_PATH=/path/to/keystore.jks STORE_PASSWORD=... KEY_PASSWORD=... gradle :app:assembleRelease
  ```
  Lokasi APK: `app/build/outputs/apk/release/app-release.apk`

---

## 4. Build Otomatis di GitHub Workspace / GitHub Actions
Workflow CI sudah disiapkan pada `.github/workflows/build-apk.yml`.
1. Push seluruh project ke repository GitHub Anda (branch `main` atau `master`).
2. Buka tab **Actions** di GitHub, lalu pilih workflow **Build Mail Septian Support APK** (dapat juga dijalankan manual lewat **Run workflow**).
3. Setelah selesai, unduh file **Mail-Septian-Support-MSFR_RATNA_2245-debug-apk** dari bagian **Artifacts**.

---

## 5. Catatan Arsitektur Keamanan (Transparan)
- **Anti Screenshot (`FLAG_SECURE`):** Satu-satunya opsi yang dapat diubah pengguna pada panel Pengaturan di pojok kiri atas, disimpan persisten menggunakan Jetpack DataStore.
- **Deteksi Perangkat (Otomatis, Tanpa Toggle):** Memeriksa indikator root/Magisk/superuser, Developer Options, dan USB Debugging secara otomatis. Deteksi sisi klien ini bersifat heuristik dan **tidak menjamin akurasi 100%**.
- **App Lock:** Menggunakan AndroidX `BiometricPrompt` dengan dukungan biometrik dan `DEVICE_CREDENTIAL` (PIN/pola/password layar kunci OS) tanpa menyimpan PIN plaintext di aplikasi.
- **WebView Security:** Allowlist ketat host `septianfr.web.id` via HTTPS, memblokir HTTP, menonaktifkan `allowFileAccess`/`allowContentAccess`/mixed content, menolak SSL error (`handler.cancel()`), tanpa JavaScript bridge, dan cookie pihak pertama saja.
- **Proteksi APK:** Build release menggunakan R8 shrinking, minification, obfuscation, serta penghapusan log debug (`-assumenosideeffects`). Langkah ini menyulitkan analisis statis, namun **tidak membuat APK mustahil untuk didecompile**.
