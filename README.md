# Touring 3D

Mini open world **touring motor keliling pulau Indonesia** 3D untuk Android — full offline, ringan.

- Render: **libGDX 1.12.1** (Java) — satu module app
- Pulau prosedural ala Sumatra/Jambi: jalan melingkar bertekstur, pantai, sawah,
  kebun sawit, kampung (rumah panggung + masjid), bukit, air terjun, pasar
- 6 spot berurutan: POM → AIR TERJUN → PANTAI → WARUNG KOPI → PASAR → VIEW POINT
- Mode: Tur Santai (paspor stempel) & Time Trial (best time) & Antar Paket (60 dtk)
- Bensin berkurang saat jalan, isi ulang di POM; habis = dorong pelan / respawn di POM
- Koin dari misi → buka warna motor (biru 20, kuning 30) & varian trail
- Motor: bodi metal bertekstur, roda berputar (jari-jari kelihatan), lampu + sorot cahaya
- Suasana hidup: 2 motor lain melintas, burung, awan bergerak, siklus siang → golden hour
- Kontrol: joystick virtual (setir) + tombol GAS / REM (multitouch) + drag kamera
- Game feel: FOV melebar seiring kecepatan, camera shake saat tabrakan, getar
- Audio: loop mesin (pitch ikut kecepatan), suara angin saat kencang, musik latar,
  SFX tabrakan/checkpoint/hitung mundur/klik/koin; musik & suara bisa dimatikan
- HUD: speedometer, bar bensin, koin, target + jarak, minimap, tracker misi

## Build

```bash
./gradlew assembleDebug
```

Native `libgdx.so` diambil dari `gdx-platform` via task `copyAndroidNatives`
(pola resmi libGDX) — tanpa ini APK crash `UnsatisfiedLinkError`.

## Kredit Aset (ditampilkan juga di layar Kredit dalam game)

- **Suara**: [Mixkit](https://mixkit.co) — Mixkit License (gratis komersial)
  - Mesin: "Motorcycle engine working"
  - Klik UI: "Classic click"
  - Tabrakan: "Factory metal hard hit"
  - Hitung mundur: "Positive interface beep"
  - Checkpoint: "Game success alert"
  - Angin & koin: dibuat sendiri (sintesis, tanpa lisensi pihak ketiga)
- **Musik**: "Carefree" — Kevin MacLeod, [incompetech.com](https://incompetech.com) — CC BY 4.0
- **Tekstur**: [ambientcg.com](https://ambientcg.com) — CC0
  (Asphalt007, Grass005, Ground054 pasir, Plaster001, Bricks104,
  CorrugatedSteel009 seng, WoodFloor051, Bark016, Grass004 daun, Rock063,
  Concrete048, PavingStones151, Ground103 tanah, Metal049A, Fabric085;
  air laut dibuat prosedural)
- **Teknik render**: `ModelCache` + `DirectionalShadowLight` (pola [jsjolund/GdxDemo3D](https://github.com/jsjolund/GdxDemo3D) — Apache 2.0); pemisahan geometri statis/dinamis ala [mbrlabs/Mundus](https://github.com/mbrlabs/Mundus) — Apache 2.0
