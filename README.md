# Touring 3D

Mini open world **touring motor** 3D untuk Android — full offline, ringan.

- Render: **Filament** (mesin 3D Google) + Kotlin native
- Pulau prosedural low-poly: jalan melingkar, pantai, bukit, kampung
- 3 spot: POM, WARUNG, VIEW POINT (kunjungi ketiganya untuk finish)
- Mode: Tur Santai & Time Trial (rekor tersimpan lokal)
- Kontrol: joystick virtual (setir) + tombol GAS / REM
- Kamera TPS ngikut dari belakang motor

## Build

```bash
./gradlew assembleDebug
```

Material Filament dikompilasi dari `materials/*.mat` memakai `matc`,
hasilnya di `app/src/main/assets/materials/*.filamat`.
