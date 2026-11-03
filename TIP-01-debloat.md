# TIP-01 — Debloat Android Head Unit (aman & bisa dibalik)

> **TIP** = Technical Implementation Plan. **Belum dikerjakan** (menyusul setelah [TIP-02](TIP-02-launcher.md)).
> Status: **DRAFT v1**. Dokumen ini sengaja lebih ringkas dari TIP-02; detail akan ditambah setelah fakta perangkat tersedia.

---

## 0. Aturan keras & label (sama dengan TIP-02)

| Label | Arti |
|---|---|
| ✅ V | Terverifikasi dari sumber/mesin pada 2026-11-02 |
| ⚠️ U | Belum terverifikasi; wajib diuji sebelum dipakai |
| 🔶 D | Keputusan desain |

1. **Jangan hapus apa pun berdasarkan nama paket dari internet/ingatan.** Setiap paket harus dikaitkan dengan bukti (apa yang dikerjakannya) sebelum dinonaktifkan.
2. **Hanya aksi yang bisa dibalik:** `pm disable-user` / `pm uninstall -k --user 0`. **Dilarang:** `rm` di `/system`, remount, flash, root.
3. **Satu paket per langkah**, uji, baru lanjut.
4. **Backup state sebelum menyentuh apa pun.**
5. Dikerjakan saat mobil **diam/parkir**.

> ⚠️ **Koreksi dari percakapan sebelumnya:** daftar nama paket "kritis" yang saya sebut (`com.tw.service`, `com.syu.ms`, `com.ts.main`, `com.car.canbus`, `com.ts.launcher`) berasal dari ingatan umum dan **tidak terverifikasi untuk perangkatmu**. Satu-satunya nama paket yang ✅ terlihat di sumber adalah dari manifest Fortuner (`com.tw.eq`, `com.dofun.carsetting`) — itu untuk TS10/Dofun, **belum tentu sama di unitmu**. Daftar final harus lahir dari inventaris perangkatmu sendiri (fase 1–2).

---

## 1. Fase 0 — Prasyarat (read-only, aman dijalankan kapan saja)

Sama dengan **Gate G0 di TIP-02 bagian 3**. Sekali jalan menghasilkan data untuk kedua TIP. Hasil di `docs/evidence/G0/`.

| Cek | Perintah | Ekspektasi |
|---|---|---|
| ADB tersambung | `adb devices -l` | status `device` (bukan `unauthorized`/`offline`) |
| Hak shell | `adb shell id` | catat uid (`shell` / `root`) |
| Bisa nonaktifkan paket? | lihat Fase 3 langkah uji | ⚠️ A-03 |

Jalur menyambung (✅ V dari tutorial Fortuner, untuk TS10; unitmu mungkin berbeda): aktifkan Opsi Developer (ketuk Build number 7×) → USB debugging (sebagian unit meminta kode pabrik) → ADB lewat Wi-Fi port 5555 → `adb connect <ip>:5555`. **Catatan:** port 5555 biasanya tertutup lagi setelah restart penuh.

---

## 2. Fase 1 — Inventaris & snapshot (read-only)

```bash
mkdir -p docs/evidence/D1 && cd docs/evidence/D1
adb shell pm list packages -f -u            > all_with_paths_incl_uninstalled.txt
adb shell pm list packages -s               > system.txt
adb shell pm list packages -3               > third_party.txt
adb shell pm list packages -d               > already_disabled.txt
adb shell dumpsys package packages          > dumpsys_packages.txt     # besar; simpan
adb shell dumpsys activity services         > services_running.txt
adb shell dumpsys meminfo                   > meminfo_baseline.txt
adb shell top -b -n 1                       > top_baseline.txt          # ⚠️ flag top beda antar Android
adb shell getprop                           > getprop.txt
```

**Snapshot "tahu rusak/tidak" sebelum perubahan** (baseline fungsi — wajib, lihat Fase 4 matriks): catat video/foto perilaku normal tiap fungsi.

---

## 3. Fase 2 — Klasifikasi (butuh keputusan manusia)

Buat `docs/evidence/D1/CLASSIFICATION.csv` dengan kolom:
`package, label_di_ui, versi, ukuran, berjalan_di_background(Y/N), fungsi(terbukti), kategori, keputusan, alasan, bukti`.

### 3.1 Kategori (🔶 D)

| Kategori | Arti | Keputusan default |
|---|---|---|
| **K — Kritis kendaraan** | radio, Bluetooth/telepon, audio/EQ/DSP, kamera mundur, tombol setir, CANBUS/pengaturan mobil, layanan MCU | **JANGAN DISENTUH** |
| **P — Platform Android** | `com.android.*`, `com.google.android.gms`, webview, penyedia setelan | **JANGAN DISENTUH** |
| **L — Launcher bawaan** | tetap sampai TIP-02 G6 lulus | **Tunda** |
| **B — Bloat jelas** | game, browser pihak ketiga, pembersih memori, aplikasi iklan | kandidat nonaktif |
| **?** — Tidak jelas | | **Selidiki dulu, default: biarkan** |

### 3.2 Cara membuktikan fungsi sebuah paket (⚠️ jangan menebak dari nama)
1. `adb shell dumpsys package <pkg>` → lihat komponen, izin, receiver `BOOT_COMPLETED`, service.
2. `adb shell pm path <pkg>` → `adb pull` APK.
3. Pakai skill `reverse-engineer-anything` (REA): `inspect_android_package` → manifest, izin, komponen. Untuk analisis kode butuh JADX JAR 0.7.1 + Java ≥ 17 yang disediakan sendiri (✅ V `references/android-applications.md`). Hasil = **observasi statis**, bukan bukti perilaku runtime.
4. **Uji sebab-akibat:** nonaktifkan satu kandidat (Fase 4), jalankan seluruh matriks uji. Kalau ada yang rusak → kembalikan (Fase 5), tandai paket sebagai K.
5. Catat kebergantungan: paket yang dibuka launcher kita (TIP-02 R-09) tidak boleh dihapus → `docs/DEPENDENCIES.md`.

---

## 4. Fase 3 — Uji kemampuan (A-03) pada target paling aman

Sebelum menyentuh paket sistem, uji bahwa mekanisme kerja **dan bisa dibalik** memakai paket yang jelas tidak penting. Contoh: pasang APK uji kecil buatan sendiri (atau salah satu aplikasi 3rd-party yang jelas tidak dipakai), lalu:

```bash
adb shell pm disable-user --user 0 <pkg>      # ekspektasi: "new state: disabled-user"
adb shell pm list packages -d | grep <pkg>    # ekspektasi: muncul
adb shell pm enable <pkg>                     # ekspektasi: "new state: enabled"
adb shell pm list packages -d | grep <pkg>    # ekspektasi: tidak muncul
```
Jika `Security exception` / `Permission Denial` → shell terbatas → rencana diubah (jalur UI Settings; **tidak** memaksa lewat cara lain). Catat di `DECISIONS.md`.

`pm uninstall -k --user 0 <pkg>` (menyisakan data, bisa dipulihkan dengan `pm install-existing <pkg>`) hanya dipakai bila `disable-user` tidak cukup — ⚠️ uji sama seperti di atas.

---

## 5. Fase 4 — Eksekusi bertahap + matriks uji

### 5.1 Prosedur per paket
1. Pastikan baseline (Fase 2) ada dan ADB stabil.
2. `adb shell pm disable-user --user 0 <pkg>` — **satu paket**.
3. Jalankan **Matriks Uji** (di bawah). **Restart head unit sekali** untuk paket yang punya receiver boot, lalu ulangi matriks.
4. Lulus semua → catat di `CHANGELOG-DEBLOAT.csv` (`waktu, paket, hasil, bukti`). Gagal → langsung **Fase 5**.
5. Maksimal **5 paket per sesi** (🔶 D) agar penyebab kerusakan mudah dilacak.

### 5.2 Matriks uji fungsi (isi ✅/❌ + bukti foto/video)

| Fungsi | Cara uji | Sebelum | Sesudah |
|---|---|---|---|
| Radio AM/FM | putar stasiun, ganti stasiun | _ | _ |
| Bluetooth | pairing, panggilan masuk/keluar, audio musik | _ | _ |
| Audio/EQ/volume | putar musik, ubah EQ, knob volume fisik | _ | _ |
| Tombol setir | next/prev/volume/mute | _ | _ |
| Kamera mundur | masuk gigi R (mobil diam, rem tangan) | _ | _ |
| AC/pengaturan mobil (jika terintegrasi) | buka panel, ubah nilai | _ | _ |
| GPS | buka Maps, fix lokasi | _ | _ |
| Wi-Fi/hotspot | konek & internet | _ | _ |
| USB/media eksternal | colok flashdisk | _ | _ |
| Boot & sleep | cold boot, ACC off→on | _ | _ |
| Launcher | tombol Home | _ | _ |

---

## 6. Fase 5 — Pemulihan

| Situasi | Tindakan |
|---|---|
| 1 paket bermasalah | `adb shell pm enable <pkg>` atau `adb shell pm install-existing <pkg>` lalu restart |
| Banyak masalah | jalankan ulang dari `CHANGELOG-DEBLOAT.csv` secara terbalik, satu per satu |
| ADB tidak ada | Pengaturan → Aplikasi → tampilkan "dinonaktifkan" → Aktifkan (jalur UI) |
| Terakhir | pemulihan pabrik unit (**jaring terakhir**, bukan rencana; catat prosedur dari manual unit **sebelum** mulai) |

Skrip pemulihan massal dibuat otomatis dari changelog (`tools/restore_all.sh`) dan **diuji** pada paket uji (Fase 3) sebelum dipakai.

---

## 7. Gate verifikasi

| Gate | Cek | Ekspektasi |
|---|---|---|
| D0 | G0 selesai | tabel fakta terisi |
| D1 | Fase 1 bukti ada | semua file di `docs/evidence/D1/` tidak kosong |
| D2 | `CLASSIFICATION.csv` | tiap baris punya keputusan + bukti; **tidak ada** paket K/P/L berstatus "nonaktifkan" |
| D3 | Fase 3 | disable→enable berhasil pada paket uji |
| D4 | Matriks 5.2 per sesi | semua ✅, atau paket dikembalikan |
| D5 | Rehearsal pemulihan | `restore_all.sh` terbukti mengembalikan paket uji |
| D6 | Pengukuran akhir | `meminfo`/`top` dibandingkan baseline; **jangan klaim perbaikan tanpa angka** |

---

## 8. Risiko

| ID | Risiko | Mitigasi |
|---|---|---|
| X-01 | Menonaktifkan paket MCU/CANBUS → tombol setir/kamera/AC mati | Kategori K dilarang; uji bertahap; pemulihan siap |
| X-02 | Paket "bloat" ternyata dependensi paket lain (cascade) | uji matriks lengkap + boot ulang per sesi |
| X-03 | ADB tertutup setelah reboot (✅ V di tutorial Fortuner) | siapkan cara membuka ulang & uji **sebelum** memulai |
| X-04 | Update OTA/factory-reset mengembalikan aplikasi | catat; changelog memungkinkan penerapan ulang |
| X-05 | Peringatan garansi/ketentuan penjual unit | cek sebelum mulai (tanggung jawab pemilik) |
| X-06 | Menghapus aplikasi yang dibuka launcher kustom | R-09, `DEPENDENCIES.md` |

---

## 9. Pertanyaan terbuka

1. Model/SoC/Android head unit? (dari G0)
2. Apakah ADB sudah pernah tersambung & lewat jalur apa (Wi-Fi/USB/Termux)?
3. Ada aplikasi tertentu yang paling mengganggu (daftar nama dari UI)?
4. Siap membuka prosedur pemulihan pabrik dari manual unit?

---

## 10. Hubungan dengan TIP-02
- G0 dipakai bersama; jalankan sekali.
- Launcher bawaan **tidak** dinonaktifkan sebelum TIP-02 G6 lulus.
- `docs/DEPENDENCIES.md` dari TIP-02 membatasi apa yang boleh dihapus di sini.

