# Haraya Dock

Custom automotive Android head unit launcher for Indonesian drivers, built with Kotlin and pure Android Views (no Compose) using a **Tactile Silk & Liquid Glass** visual identity.

Inspired by [`agungpmbd/goldendash`](https://github.com/agungpmbd/goldendash) (MIT) with features tailored for Indonesian road trips and prayer schedules.

---

## Fitur Utama

- **Latar Belakang Kain Sutra 3D (*Silk Background*)**:
  Render kain sutra bergelombang Bézier multi-layer (*Warm Ivory Silk* untuk mode Terang dan *Midnight Indigo Silk* untuk mode Gelap), di-cache ke Bitmap offscreen sekali saat tema berubah (0% beban CPU saat idle).
- **Auto Dark / Light Mode via Matahari (*Solar Calc*)**:
  Deteksi otomatis siklus siang dan malam berdasarkan koordinat lokasi GPS dan kalkulasi astronomi matahari lokal (*SolarCalc* dengan histeresis 15 menit). Dapat juga dialihkan secara manual (*Otomatis / Gelap / Terang*).
- **Jadwal Sholat 5 Waktu Offline & Hitung Mundur**:
  Perhitungan jadwal sholat astronomis akurat (*Subuh*, *Dzuhur*, *Ashar*, *Maghrib*, *Isya*) berbasis koordinat lokasi lokal pengemudi tanpa memerlukan koneksi internet, lengkap dengan hitung mundur sholat berikutnya dan kutipan ayat Al-Qur'an/Hadits harian.
- **Peta Kartografi & Bagikan Lokasi Live**:
  Kanvas navigasi kartografi perkotaan dengan *overlay* info kota/kecamatan otomatis (*Reverse Geocoder*) dan tombol sekali-sentuh untuk membuka *Google Maps Live Location Sharing*.
- **Dashboard Telemetri OBD-II Khusus**:
  Layar instrumen kendaraan mandiri yang dilengkapi *Twin-Dial Cluster* (Radial 270° Speedometer & Tachometer RPM 0–8.000 RPM dengan batas redline), 6 kapsul sensor suhu mesin *coolant*, voltase aki, beban mesin, dan pemindai status kerusakan mesin (DTC).
- **Glass App Drawer Grid**:
  Peluncur aplikasi bergaya *liquid glass* 6 kolom dengan pencarian instan (*real-time search*), ikon aplikasi asli beresolusi tinggi, dan efek sentuh *tactile ripple*.
- **Kontrol Perangkat Keras Taktil**:
  Slider sentuh responsif untuk *Volume Media* (`AudioManager.STREAM_MUSIC`) dan *Kecerahan Layar*.

---

## Struktur Proyek

```text
haraya-dock/
├── app/
│   ├── src/main/
│   │   ├── java/com/haraya/dock/
│   │   │   ├── MainActivity.kt          # Kokpit utama, sidebar, dan state navigasi
│   │   │   ├── calc/                    # Perhitungan astronomis matahari & sholat offline
│   │   │   │   ├── SolarCalc.kt
│   │   │   │   └── PrayerCalc.kt
│   │   │   ├── data/
│   │   │   │   └── QuoteRepository.kt   # Koleksi kutipan Al-Qur'an & Hadits
│   │   │   ├── theme/
│   │   │   │   ├── ThemeMode.kt
│   │   │   │   └── ThemeResolver.kt
│   │   │   └── ui/
│   │   │       ├── MapCanvasView.kt     # Kanvas peta kartografi
│   │   │       ├── RpmGaugeView.kt      # Tachometer putaran mesin 270°
│   │   │       ├── SpeedGaugeView.kt    # Speedometer instrumen 270°
│   │   │       ├── TactileSliderCard.kt # Slider volume & kecerahan taktil
│   │   │       ├── VectorIconView.kt    # Ikon vektor murni (tanpa emoji)
│   │   │       └── glass/               # Palet & builder material Liquid Glass
│   │   │           ├── GlassCardView.kt
│   │   │           ├── GlassPalette.kt
│   │   │           ├── GlassTheme.kt
│   │   │           └── SilkBackgroundView.kt
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── DESIGN.md                            # Panduan desain & hierarki visual
├── TIP-01-debloat.md                    # Panduan debloat head unit aman
├── TIP-02-launcher.md                   # Rencana teknis launcher
└── README.md
```

---

## Cara Build & Install

### Prasyarat
- Java JDK 17
- Android SDK (API 29+)

### Build APK
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
./gradlew assembleDebug
```
File APK akan dibuat di:
`app/build/outputs/apk/debug/app-debug.apk`

### Pasang ke Perangkat via ADB
```bash
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
```

---

## Lisensi

Proyek ini dibangun secara mandiri di bawah lisensi MIT.
Komponen latar belakang kain sutra dan logika instrumen terinspirasi dari arsitektur [`agungpmbd/goldendash`](https://github.com/agungpmbd/goldendash) (MIT).

