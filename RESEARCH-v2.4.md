# Riset Referensi — Touring 3D v2.4 (libGDX 1.12.1, Android)

Tanggal: 8 Nov 2026. Tujuan: memperbaiki Touring 3D setelah user tes v2.3 dan bilang
grafik jelek banget + kontrol susah banget. Hanya riset — tidak ada file game yang diubah.

## 0. Keadaan Touring 3D v2.3 saat ini (dicek dari kode)

- **Kontrol** (`TouringScreen.java`, custom `InputProcessor`, multitouch):
  - Setengah kiri layar = joystick mengambang (titik sentuh pertama jadi pusat joystick,
    `joyBaseX = x` saat `touchDown`) — ini sudah pola "follow mode" yang benar.
  - Kanan bawah = tombol GAS / REM (lingkaran), sisa kanan = drag orbit kamera.
  - Masalah yang terlihat dari kode: `steer = -joyDX` **mentah** — tanpa deadzone,
    tanpa smoothing/lerp, tanpa kurva expo. `Bike.update` sudah punya `lowAuth`
    (belok lemah saat pelan) dan `highDamp` (belok diredam saat cepat), dan
    `leanVisual` di-lerp 8/dtk — tapi input setirnya sendiri tidak dihaluskan.
  - Tidak ada: tilt/gyro, auto-gas, pilihan skema kontrol, pengaturan sensitivitas.
  - Akibatnya pemain butuh 3 jari sekaligus (setir + gas + kamera) dan setir terasa
    "digital"/mendadak — ini yang paling mungkin terasa "susah banget".
- **Grafis** (`GameWorld.java`, `Bike.java`): sudah ada fondasi bagus — `ModelBatch`,
  `Environment`, `DirectionalShadowLight` 1024 + `DepthShaderProvider` (pola GdxDemo3D),
  `ModelCache`, tekstur 512px, FOV kick 45→62 sesuai kecepatan, camera shake.
  Yang **tidak ada** (dari grep kode): fog, skybox/langit, normal map, partikel,
  animasi tekstur (air terjun/lamunan air statis). Dunia dibangun dari box
  `ModelBuilder` — geometri kotak + tekstur realistis = kesan "template" tetap kuat.

---

## 1. Game 3D libGDX kompleks yang bisa dijadikan referensi

### 1.1 Delver — referensi grafis & art direction (PALING PENTING)
- **Apa:** first-person roguelike dungeon crawler, rilis Steam 2018, dibuat dengan libGDX.
- **Repo (open source, lisensi zlib):** https://github.com/Interrupt/delverengine
  (~820 star; engine + editor, tanpa data game). Steam: https://store.steampowered.com/app/249630/Delver/
- **Kenapa bagus — dan yang bisa ditiru:**
  - **Art direction konsisten mengalahkan realisme.** Delver sengaja bergaya pixel
    chunky ala SNES ("seandainya SNES bisa 3D") — tekstur low-res yang konsisten di
    semua objek terlihat jauh lebih bagus daripada tekstur realistis yang ditempel
    di geometri kotak. Pelajaran untuk Touring: pilih satu gaya (mis. stylized
    warna hangat/saturasi tinggi) dan samakan di semua aset, jangan campur.
  - **Lighting dinamis + kegelapan/fog.** Obor dan cahaya dinamis, area gelap,
    partikel debu dan fireflies — suasana datang dari cahaya, bukan dari jumlah
    poligon. Delver punya `Dynamic lights`, FXAA, head bob.
  - **Kontrol sentuh Android yang "pretty good"** (menurut ulasan RetroHandhelds)
    — Delver versi Android sempat punya kontrol sentuh yang layak.
  - **Modular/engine terbuka:** kode rendering, lighting, dan partikelnya bisa dibaca
    langsung di repo di atas — contoh nyata `ModelBatch` + `Environment` + cahaya
    dinamis dalam skala game komersial.
- **Batasan:** Delver dungeon (ruang tertutup), bukan dunia terbuka — jadi teknik
  lighting/fog-nya yang ditiru, bukan layout dunianya.

### 1.2 Motoman — referensi LANGSUNG: game motor libGDX di Android
- **Apa:** game balap motor Android murni libGDX, proyek solo 2 bulan (2013),
  GPLv3. Ini satu-satunya game motor libGDX open source yang ketemu.
- **Repo:** https://github.com/mammalwong/motoman
  Dokumentasi: https://deepwiki.com/mammalwong/motoman
  Port Kotlin Multiplatform: https://github.com/ber4444/motoman-kmp
- **Yang bisa ditiru (sangat spesifik ke Touring):**
  - **Model dari Blender → OBJ + MTL.** Motor, rider, bangunan, skybox semua dibuat
    di Blender dan diekspor OBJ — bukan rakitan box. Ini pembeda visual terbesar.
  - **Track prosedural dari seed** (lintasan tak terbatas) + **rally-style corner
    callout** (pemberitahuan jenis tikungan di depan, seperti co-driver reli).
  - **Active camera:** kamera dinamis yang otomatis mengarah ke apex tikungan di
    depan — pemain selalu melihat ke mana harus belok. Ini jauh lebih membantu
    daripada orbit kamera manual.
  - **Dual steering:** counter-steering + leaning yang bisa dikombinasikan.
  - **Physical Strength System:** efektivitas setir berkurang kalau pemain
    mengoreksi terlalu cepat/berulang — mencegah zig-zag tidak realistis.
    (Ide yang sama bisa dipakai: batasi laju perubahan `steer` per detik.)
  - **GLSL shader effects** (efek shader kustom) + **ETC1 texture compression**
    untuk performa Android + dukungan tilt sensor (accelerometer) untuk leaning.
  - Ada APK prebuilt + video demo di README repo.
- **Batasan:** kode dari 2013 (libGDX jadul, Eclipse ADT) — arsitektur dan idenya
  yang ditiru, bukan kodenya mentah-mentah.

### 1.3 Yang TIDAK masuk hitungan
- **Moto Road Rash 3D** (github.com/Eudoxie-Studio/Moto-Road-Rash-3D) — game motor
  open source, tapi engine kustom sendiri dan hanya PC. Bukan libGDX, jadi tidak
  bisa ditiru kodenya.
- Game libGDX terkenal lain (Slay the Spire, Mindustry, Unciv, Shattered Pixel
  Dungeon) kebanyakan 2D — tidak relevan untuk grafis 3D. Daftar showcase resmi:
  https://libgdx.com/showcase/
- **Temuan jujur:** game 3D libGDX yang kompleks dan bagus itu LANGKA. Delver
  praktis satu-satunya contoh flagship 3D. Artinya ekspektasi visual harus
  realistis: libGDX 3D tidak punya post-processing, fog, atau skybox bawaan —
  semua itu kode kustom kecil (lihat §3).

---

## 2. Skema kontrol mobile game balap/motor yang terasa enak

### 2.1 Traffic Rider — raja genre (500 jt+ unduhan, SKGames)
Sumber: https://trafficridermod.org/ dan https://thetrafficriderrapk.com/
- **Steering (3 pilihan + gamepad):** Tilt (miring HP, default — paling natural,
  paling presisi untuk koreksi kecil di lalu lintas padat), Button (tombol kiri/
  kanan di layar — paling konsisten), Handlebar (drag gambar setang virtual).
- **Throttle:** **Auto** (gas penuh terus, pemain hanya mengerem — untuk pemula)
  atau Manual. **Gear: Auto** (default) atau Manual.
- **Kalibrasi:** kalau tilt terasa miring, tekan Calibrate sambil memegang HP di
  posisi berkendara normal — posisi itu jadi nol.
- **Pelajaran inti:** default-nya **mengurangi jumlah input simultan** — pemain
  hanya fokus ke SATU hal (setir), sisanya otomatis. Inilah yang membuat Traffic
  Rider terasa gampang dipelajari dalam 10 detik.

### 2.2 Prinsip umum kontrol balap mobile (dari panduan Velocity Unleashed)
Sumber: https://www.velocityunleashed.ca/blog/touch-and-tilt-controls-guide
- **Tilt:** sensitif 45–55% sebagai baseline; max lock tercapai dengan putaran
  pergelangan ~35° (jangan sampai HP miring total sampai layar tak terlihat).
  Sensitivitas maksimal justru bikin goyang — tremor tangan jadi belokan liar.
- **Deadzone mikro di tengah:** mencegah motor "mengembara" sendiri di jalan lurus,
  tapi tetap mengizinkan koreksi kecil.
- **Kalibrasi posisi netral** di postur bermain yang sebenarnya (bukan di atas meja).
- **Touch vs tilt:** touch = presisi & konsisten di segala posisi; tilt = imersif
  tapi melelahkan dan tergantung posisi duduk. **Sediakan keduanya + biarkan
  pemain memilih** — jangan memaksakan satu skema.

### 2.3 Pola joystick di libGDX
- **Widget bawaan:** `com.badlogic.gdx.scenes.scene2d.ui.Touchpad` — joystick
  layar dengan `deadzoneRadius` di konstruktor dan `getKnobPercent()` (0–1) untuk
  dibaca tiap frame. Butuh `Stage` sebagai input processor.
  Dok: https://libgdx.com/wiki/graphics/2d/scene2d/scene2d-ui
- **Follow mode (ala Brawl Stars):** joystick muncul di tempat jempol pertama
  menyentuh, bukan di posisi tetap. Sudah dibahas dan dicontohkan di issue
  libGDX #6688 (kode contoh `TouchpadTest`): https://github.com/libgdx/libgdx/issues/6688
  — Touring v2.3 SUDAH memakai pola ini (joyBaseX = posisi sentuh). Yang kurang
  tinggal deadzone + smoothing + kurva respons.
- **Aturan praktis tombol:** jempol diam di oval nyaman di sudut bawah layar;
  tombol rem/gas besar; jarak antar tombol pendek (mengurangi travel jempol).

### 2.4 Kenapa kontrol Touring v2.3 terasa susah (diagnosis dari kode)
1. Tiga jari sekaligus: setir kiri + GAS kanan (tahan terus) + kamera kanan.
   Tidak ada auto-gas → jempol kanan terkunci di GAS, kamera praktis tak terpakai
   saat jalan, dan pemain kelelahan.
2. `steer` mentah dari posisi joystick (linear, tanpa deadzone/smoothing/expo) →
   belokan terasa mendadak dan sulit dikoreksi halus; gampang zig-zag.
3. Tidak ada tilt — padahal untuk game motor, tilt adalah skema default yang
   diharapkan pemain (Traffic Rider, semua game motor populer).
4. Tidak ada bantuan setir (steering assist) — di jalan lurus motor harusnya
   cenderung lurus sendiri; saat ini koreksi kecil pun langsung membelokkan.

---

## 3. Teknik grafis libGDX 3D yang dipakai game bagus

Tutorial dasar (Xoppa, penulis API 3D libGDX): http://xoppa.github.io/blog/basic-3d-using-libgdx/
Tutorial shadow: https://codingtechroom.com/question/how-to-implement-3d-shadows-in-libgdx

| Teknik | Status di libGDX | Catatan untuk Touring |
|---|---|---|
| `ModelBatch` + `Environment` + `DirectionalLight` + `AmbientLight` | Bawaan, sudah dipakai | Naikkan kualitas dengan warna cahaya hangat (golden hour) + ambient biru langit, bukan putih netral |
| `DirectionalShadowLight` + `DepthShaderProvider` (shadow map) | Bawaan, sudah dipakai (1024) | 1024 sudah pas untuk Mali; jangan naikkan, yang diperbaiki adalah *coverage* area shadow mengikuti motor |
| Fog | **Tidak ada bawaan** — butuh shader kustom (`ShaderProvider` sendiri) | Fog jarak jauh adalah peningkat visual terbesar untuk dunia terbuka (menyembunyikan ujung dunia + memberi depth). Implementasi: shader kustom sederhana berbasis jarak, atau trik murah: warna clear + objek jauh di-tint kabut |
| Skybox / langit | **Tidak ada bawaan** | Cara umum: dome/besar dengan tekstur gradien, atau sphere terbalik dengan cubemap. Paling murah & efektif: langit gradien prosedural (vertex color) + matahari sprite + awan billboard |
| Normal map | Didukung shader bawaan (`TextureAttribute.createNormal`) | Aspal/bata/batu dengan normal map terlihat jauh lebih "dalam" tanpa tambah poligon |
| Mipmap + anisotropic filtering | Bawaan (`TextureFilter.MipMapLinearLinear`, `Gdx.graphics` anisotropy) | Wajib untuk tekstur jalan/tanah — menghilangkan shimmer saat jalan menjauh |
| Texture atlas / ETC1 | Bawaan (`TextureAtlas`, ETC1 via `ETC1Compressor`) | Motoman memakai ETC1 untuk hemat memori Android |
| Post-processing (bloom, FXAA, vignette) | **Tidak ada bawaan** di core libGDX | Jangan kejar bloom — mahal di Mali. Vignette murah bisa via overlay Scene2D (tekstur radial transparan) |
| Partikel | Bawaan (`ParticleEffect`, 3D via `ParticleEffect` + billboard) | Debu jalan, daun jatuh, asap warung — gerakan kecil = dunia terasa hidup |
| LOD / culling | Manual (frustum culling + `ModelCache`, sudah dipakai) | Tambah: sembunyikan detail kecil di kejauhan, ganti pohon jauh dengan billboard |
| Animasi tekstur | Manual (offset UV per frame) | Air terjun + air laut: scroll offset tekstur — murah dan langsung kelihatan |

**Pelajaran Delver untuk grafis:** suasana datang dari **cahaya + fog + partikel**,
bukan jumlah poligon. Satu directional light hangat + ambient biru + fog + debu
jalan akan mengubah kesan jauh lebih besar daripada menambah 10 bangunan baru.

---

## 4. Rekomendasi prioritas untuk Touring 3D (urut: dampak ÷ usaha)

### P1 — Mode kontrol "Santai" ala Traffic Rider (kontrol — dampak TERBESAR)
- **Auto-gas ON sebagai default** (motor jalan sendiri; pemain hanya setir + rem).
  Satu jempol bebas → kamera bisa dipakai, kelelahan hilang.
- **Tambah tilt steering** (`Gdx.input.getAccelerometerX/Y` + tombol Calibrate
  untuk posisi netral) sebagai pilihan di Pengaturan, bersama mode Joystick dan
  Tombol Kiri/Kanan.
- **Perbaiki respons setir:** deadzone ~0,12 di tengah joystick; smoothing
  (`steer` di-lerp menuju target ~10/dtk); kurva expo (`steer^3` dicampur linear)
  supaya koreksi kecil halus tapi belokan penuh tetap bisa; batasi laju perubahan
  setir per detik (ide "strength system" Motoman) supaya tidak zig-zag.
- **Steering assist ringan:** saat tidak ada input, motor perlahan kembali lurus
  mengikuti arah jalan (atau minimal: heading tidak berubah sendiri).
- Usaha: sedang (semua di `TouringScreen` + `Bike`, tanpa aset baru).

### P2 — Kamera chase otomatis dengan lean (kontrol + feel — dampak besar, usaha kecil)
- Kamera mengikuti dari belakang dengan **lag halus** (lerp posisi + lookAt),
  bukan menempel kaku; **roll kamera mengikuti lean motor** beberapa derajat —
  ini yang membuat rasa berkendara "nempel" dan kecepatan terasa.
- Orbit drag tetap ada, tapi sebagai penyesuaian sudut sesekali, bukan kebutuhan.
- FOV kick sudah ada — pertahankan.
- Usaha: kecil (ubah `updateCamera` saja).

### P3 — Langit + fog + cahaya golden hour (visual — dampak besar, usaha sedang)
- **Skybox gradien** (dome dengan vertex color / tekstur gradien: biru → oranye
  di horizon saat golden hour) + sprite matahari + awan billboard yang bergerak.
- **Fog jarak jauh** (shader kustom sederhana berbasis jarak; atau trik murah:
  tint objek jauh ke warna langit). Ini menyembunyikan "ujung dunia" dan memberi
  depth — penyebab utama kesan kosong/flat.
- **Cahaya hangat:** directional light oranye keemasan + ambient biru keunguan saat
  sore; bayangan memanjang. (Shadow map 1024 sudah ada — tinggal warnanya.)
- Usaha: sedang (satu shader kustom untuk fog + satu model dome langit).

### P4 — Jalan yang meyakinkan (visual — dampak besar, usaha kecil)
- **Marka jalan:** garis tengah putus-putus + garis tepi putih/kuning (strip
  tipis di atas aspal). Jalan adalah objek yang paling lama dilihat pemain.
- **Tiling tekstur yang benar:** aspal diulang dengan skala yang masuk akal
  (jangan di-stretch selebar jalan) + **mipmap + anisotropic filtering** supaya
  tidak shimmer/berkilau saat dilihat dari jauh.
- Bahu jalan tanah + rumput di tepi; tikungan terlihat dari jauh (jalan
  membelok, bukan lurus datar).
- Usaha: kecil (tekstur + UV repeat + filter).

### P5 — Model motor dari Blender (visual — dampak besar, usaha sedang-besar)
- Ganti rakitan box dengan **model .g3db/.obj motor bebek ala Beat yang
  proporsional + pengendara duduk** (dibuat di Blender, seperti Motoman).
  Ini satu-satunya perubahan yang benar-benar menghilangkan kesan "template".
- Kalau belum sempat full model: minimal perbaiki proporsi (roda lebih besar,
  bodi lebih rendah/panjang, setang + spion + lampu yang jelas) dan tambah
  pengendara sederhana.
- Usaha: sedang-besar (butuh modeling; bisa dicicil: motor dulu, pengendara belakangan).

### P6 — Detail dunia yang bergerak (visual — dampak sedang, usaha kecil)
- **Partikel debu** di belakang motor saat di jalan tanah + **daun jatuh** di
  kebun + **asap** dari warung kopi + **air terjun & laut beranimasi** (scroll
  offset tekstur per frame — 5 baris kode).
- Burung mengepak (dua frame sayap bergantian) — sudah ada burung statis, tinggal
  animasi.
- Usaha: kecil (efek-efek ini murah di libGDX).

### P7 — Kualitas tekstur & performa (visual — dampak sedang, usaha kecil)
- **Mipmap + anisotropic** untuk semua tekstur tanah/jalan; **normal map** untuk
  aspal/bata/batu (didukung shader bawaan libGDX).
- **Texture atlas** untuk objek kecil (rambu, kios, pagar) supaya draw call
  sedikit; pertahankan shadow map 1024 (jangan naikkan — Mali).
- **Satu gaya visual yang konsisten** (pelajaran Delver): samakan saturasi dan
  tone semua tekstur ke arah stylized hangat, jangan campur tekstur fotorealistis
  dengan warna flat.
- Usaha: kecil (setting tekstur + ganti beberapa aset).

## Urutan eksekusi yang disarankan
**P1 → P2 → P4 → P3 → P6 → P7 → P5.**
Alasan: P1+P2 langsung menjawab keluhan "susah banget" (kontrol) dan bisa dites
user dalam satu ronde. P4+P3 menjawab "jelek banget" dengan usaha kecil-sedang.
P5 (model Blender) paling berdampak visual tapi paling lama — dikerjakan terakhir
atau paralel sambil user mengetes P1–P4.

## Sumber utama
- Delver engine (libGDX, zlib): https://github.com/Interrupt/delverengine
- Delver di Steam: https://store.steampowered.com/app/249630/Delver/
- Motoman (game motor libGDX, GPLv3): https://github.com/mammalwong/motoman
- Motoman docs: https://deepwiki.com/mammalwong/motoman
- Motoman KMP port: https://github.com/ber4444/motoman-kmp
- libGDX showcase: https://libgdx.com/showcase/
- Tutorial 3D dasar Xoppa: http://xoppa.github.io/blog/basic-3d-using-libgdx/
- Tutorial shadow libGDX: https://codingtechroom.com/question/how-to-implement-3d-shadows-in-libgdx
- Touchpad libGDX: https://libgdx.com/wiki/graphics/2d/scene2d/scene2d-ui
- Follow-mode joystick (issue #6688): https://github.com/libgdx/libgdx/issues/6688
- Panduan kontrol tilt (Velocity Unleashed): https://www.velocityunleashed.ca/blog/touch-and-tilt-controls-guide
- Kontrol Traffic Rider: https://trafficridermod.org/
