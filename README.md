# Touring 3D

Mini open world **touring motor** 3D untuk Android — full offline, ringan.

- Render: **libGDX 1.12.1** (Java) — satu module app
- Pulau prosedural low-poly: jalan melingkar bertekstur, pantai, bukit, kampung
- 3 spot berurutan: POM → WARUNG → VIEW POINT (kunjungi ketiganya untuk finish)
- Mode: Tur Santai & Time Trial (rekor tersimpan lokal)
- Kontrol: joystick virtual (setir) + tombol GAS / REM (multitouch) + drag kamera
- Game feel: FOV melebar seiring kecepatan, camera shake saat tabrakan, getar
- Audio: loop mesin (pitch ikut kecepatan), musik latar, SFX tabrakan/checkpoint/hitung mundur/klik
- HUD: speedometer, minimap, target checkpoint, timer

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
- **Musik**: "Carefree" — Kevin MacLeod, [incompetech.com](https://incompetech.com) — CC BY 4.0
- **Tekstur**: [ambientcg.com](https://ambientcg.com) — CC0 (Asphalt007, Grass005)
