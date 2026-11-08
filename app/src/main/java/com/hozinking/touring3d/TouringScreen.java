package com.hozinking.touring3d;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalShadowLight;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

import java.util.ArrayList;
import java.util.List;

/**
 * Layar utama Touring 3D v2.4: MENU -> COUNTDOWN -> RIDING -> FINISHED.
 * Kontrol v2.4 (mode "Santai" ala Traffic Rider): auto-gas ON default
 * (motor jalan sendiri, pemain hanya setir + REM), setir joystick (follow-mode)
 * atau tilt (accelerometer + kalibrasi), deadzone + expo + smoothing + batas
 * laju setir (anti zig-zag). Kamera chase otomatis + roll mengikuti lean.
 * Drag area kanan untuk orbit kamera (kamera balik sendiri ke belakang motor).
 * Checkpoint BERURUTAN: POM -> AIR TERJUN -> PANTAI -> WARUNG KOPI -> PASAR -> VIEW POINT.
 * Mode: TUR (paspor stempel), TIME TRIAL (best time), ANTAR PAKET (delivery).
 * Bensin berkurang saat jalan, isi ulang di POM. Koin dari misi -> warna/varian motor.
 * Semua UI digambar manual (ShapeRenderer + BitmapFont).
 * Catatan: BitmapFont bawaan tak punya glyph emoji -> teks polos saja.
 */
public class TouringScreen implements Screen, InputProcessor {

    private static final String VERSION_LABEL = "v2.4 (11)";

    private enum State {MENU, COUNTDOWN, RIDING, PAUSED, FINISHED}

    private enum MenuTab {MAIN, MOTOR, SETTINGS, HELP, CREDITS}

    private enum Mode {TOUR, TRIAL, DELIVERY}

    private final TouringGame game;
    private State state = State.MENU;
    private MenuTab menuTab = MenuTab.MAIN;
    private State pausedFrom = State.RIDING; // fix v2.2: ingat state sebelum jeda
    private Mode mode = Mode.TOUR;

    private ModelBatch modelBatch;
    private SpriteBatch spriteBatch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    private BitmapFont bigFont;
    private BitmapFont speedFont;
    private final GlyphLayout layout = new GlyphLayout();

    private PerspectiveCamera camera;
    private Environment environment;
    private GameWorld world;
    private Bike bike;
    private Preferences prefs;
    private AudioMan audio;
    private final Vector3 shadowCenter = new Vector3(); // pusat shadow map mengikuti motor
    // Siklus siang-sore: referensi matahari + ambient untuk diubah warnanya.
    private DirectionalShadowLight sun;
    private ColorAttribute ambientAttr;

    private final boolean[] visited = new boolean[GameWorld.SPOT_COUNT];
    private int nextCp = 0;
    private float rideTime, countdownT, timeAcc;
    private String lastCountdown = "";
    private String menuBest = "";
    private String finishTime = "", finishBest = "";
    private boolean finishIsBest;
    private String finishTitle = "SELESAI!";
    private String finishNote = "";
    private String toastMsg = "";
    private float toastT;

    // bensin, koin, paspor
    private float fuel = 100f;
    private int coins = 0;
    private boolean fuelWarned = false;
    private boolean refilling = false;
    // antar paket
    private GameWorld.Spot deliveryTarget;
    private float deliveryTimeLeft = 0f;
    private boolean deliveryDone = false;
    // motor lain (lalu lintas di jalan ring)
    private final Bike[] traffic = new Bike[2];
    // siklus siang-sore: 0 = pagi, 1 = senja (golden hour)
    private float dayPhase = 0f;
    private boolean goldenToastShown = false;

    // kontrol
    private float steer;
    private boolean gas, brake;
    private int joyPointer = -1, lookPointer = -1, gasPointer = -1, brakePointer = -1;
    private float joyBaseX, joyDX;
    private float orbitYaw, orbitPitch, lookLastX, lookLastY;
    private float camX, camY, camZ;
    private float orbitA;
    // v2.4: mode "Santai" — auto-gas + pilihan skema setir (joystick/tilt)
    private boolean autoGas = true; // default ON: motor jalan sendiri
    private int steerMode = 0; // 0 = joystick, 1 = tilt (accelerometer)
    private float tiltSens = 0.5f; // 0.40..0.60, baseline 50%
    private float tiltZero = 0f; // posisi netral accelerometer Y (dari Kalibrasi)
    private boolean tiltInvert = false; // balik arah tilt (bila tanda di HP terbalik)
    // v2.4: tuning setir — deadzone, smoothing, batas laju (anti zig-zag)
    private static final float STEER_DEADZONE = 0.12f;
    private static final float STEER_SMOOTH = 10f; // /dtk, lerp menuju target
    private static final float STEER_MAX_RATE = 3f; // /dtk, laju maks perubahan setir
    private static final float[] TILT_SENS_STEPS = {0.40f, 0.45f, 0.50f, 0.55f, 0.60f};

    // game feel: FOV kick + camera shake
    private float fov = 45f;
    private float shake = 0f;
    private float crashCd = 0f;

    private int W, H;
    // metrik UI (koordinat y-up, px)
    private float joyCX, joyCY, joyR;
    private float gasCX, gasCY, gasR, brakeCX, brakeCY, brakeR;
    private float tourBX, tourBY, tourBW, tourBH;
    private float trialBX, trialBY, trialBW, trialBH;
    private float deliverBX, deliverBY, deliverBW, deliverBH;
    private float motorBX, motorBY, motorBW, motorBH;
    private float setBX, setBY, setBW, setBH;
    private float helpBX, helpBY, helpBW, helpBH;
    private float credBX, credBY, credBW, credBH;
    private float backBX, backBY, backBW, backBH;
    // tombol overlay jeda (LANJUT / MENU)
    private float pauseBW, pauseBH, pauseLanBX, pauseLanBY, pauseMenuBX, pauseMenuBY;
    // tombol respawn saat bensin habis
    private float respBX, respBY, respBW, respBH;
    // tab MOTOR: 2 varian + 3 warna
    private float var0BX, var0BY, var0BW, var0BH, var1BX, var1BY, var1BW, var1BH;
    private float col0BX, col0BY, col1BX, col1BY, col2BX, col2BY, colBW, colBH;
    // tab PENGATURAN: 2 toggle
    private float togMusBX, togMusBY, togSndBX, togSndBY, togBW, togBH;
    // v2.4: tab PENGATURAN — kontrol (auto-gas, skema setir, sensitivitas,
    // kalibrasi tilt, balik tilt) + tombol KEMBALI khusus tab ini
    private float togGasBX, togGasBY, togSteerBX, togSteerBY, togSensBX, togSensBY;
    private float togCalBX, togCalBY, togInvBX, togInvBY;
    private float setBackBX, setBackBY, setBackBW, setBackBH;

    private static final String[] HELP_LINES = {
            "AUTO GAS aktif: motor jalan sendiri, cukup setir + REM.",
            "Joystick kiri: belok. Atau mode TILT: miringkan HP.",
            "Mode Manual: tombol GAS (hijau) / REM (merah) - bisa bareng belok",
            "Tahan REM saat berhenti: mundur",
            "Tombol BACK: jeda (lanjut / menu)",
            "Geser area kanan layar: putar kamera (balik sendiri)",
            "Pengaturan: ganti Auto Gas / Joystick / Tilt / sensitivitas.",
            "Tilt terasa miring? Tekan KALIBRASI TILT di Pengaturan.",
            "TUR: kunjungi 6 spot berurutan, kumpulin stempel paspor.",
            "TIME TRIAL: 6 spot secepat mungkin, best time tersimpan.",
            "ANTAR PAKET: ambil di PASAR, antar ke rumah kampung (60 dtk).",
            "Bensin berkurang saat jalan - isi ulang di POM.",
            "Koin dari misi bisa buat buka warna & varian motor.",
            "Tabrakan bikin melambat. Hati-hati!"
    };

    private static final String[] CREDIT_LINES = {
            "SUARA: Mixkit (mixkit.co) - Mixkit License",
            "- Mesin: Motorcycle engine working",
            "- Klik: Classic click",
            "- Tabrakan: Factory metal hard hit",
            "- Hitung mundur: Positive interface beep",
            "- Checkpoint: Game success alert",
            "- Angin & koin: dibuat sendiri (sintesis)",
            "MUSIK: Carefree - Kevin MacLeod",
            "incompetech.com (CC BY 4.0)",
            "TEKSTUR: ambientcg.com (CC0)",
            "- Asphalt007, Grass005, Ground054 (pasir)",
            "- Plaster001, Bricks104, CorrugatedSteel009",
            "- WoodFloor051, Bark016, Grass004, Rock063",
            "- Concrete048, PavingStones151, Ground103",
            "- Metal049A, Fabric085 + air prosedural",
            "RENDER: pola ModelCache & bayangan",
            "dari GdxDemo3D/jsjolund (Apache 2.0),",
            "organisasi scene ala Mundus (Apache 2.0)"
    };

    // Warna motor: 0 merah (gratis), 1 biru (20 koin), 2 kuning (30 koin)
    private static final Color[] BIKE_COLORS = {
            new Color(0.80f, 0.16f, 0.16f, 1f),
            new Color(0.15f, 0.35f, 0.85f, 1f),
            new Color(0.95f, 0.75f, 0.15f, 1f)
    };
    private static final String[] BIKE_COLOR_NAMES = {"MERAH", "BIRU", "KUNING"};
    private static final int[] BIKE_COLOR_PRICE = {0, 20, 30};

    public TouringScreen(TouringGame game) {
        this.game = game;
    }

    @Override
    public void show() {
        modelBatch = new ModelBatch();
        spriteBatch = new SpriteBatch();
        shapes = new ShapeRenderer();
        font = new BitmapFont();
        font.getData().setScale(2f);
        font.setColor(Color.WHITE);
        bigFont = new BitmapFont();
        bigFont.getData().setScale(5f);
        bigFont.setColor(Color.YELLOW);
        speedFont = new BitmapFont();
        speedFont.getData().setScale(4f);
        speedFont.setColor(Color.WHITE);

        camera = new PerspectiveCamera(45f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 0.5f;
        camera.far = 1600f;

        environment = new Environment();

        prefs = Gdx.app.getPreferences("touring3d");
        coins = prefs.getInteger("coins", 0);
        // v2.4: muat pengaturan kontrol (default: auto-gas ON, joystick, sens 50%)
        autoGas = prefs.getBoolean("auto_gas", true);
        steerMode = prefs.getInteger("steer_mode", 0);
        tiltSens = prefs.getFloat("tilt_sens", 0.5f);
        tiltZero = prefs.getFloat("tilt_zero", 0f);
        tiltInvert = prefs.getBoolean("tilt_invert", false);

        world = new GameWorld();
        world.build();
        // Matahari + bayangan real-time (pola GdxDemo3D/jsjolund, Apache 2.0)
        sun = world.getShadowLight();
        environment.add(sun);
        environment.shadowMap = sun;
        ambientAttr = new ColorAttribute(ColorAttribute.AmbientLight, 0.55f, 0.55f, 0.55f, 1f);
        environment.set(ambientAttr);

        bike = new Bike();
        applyBikePrefs(bike);
        bike.buildModel();
        bike.reset(world.startX, world.startZ, world.startHeading);

        // 2 motor lain melintas di jalan ring (lalu lintas)
        for (int i = 0; i < 2; i++) {
            Bike t = new Bike();
            t.bodyColor.set(i == 0 ? 0.15f : 0.9f, i == 0 ? 0.35f : 0.75f, i == 0 ? 0.85f : 0.2f, 1f);
            t.buildModel();
            float a = (float) (i * Math.PI + 0.7f);
            t.reset((float) Math.cos(a) * 94f, (float) Math.sin(a) * 94f, -a);
            t.speed = (i == 0) ? 11f : 14f;
            traffic[i] = t;
        }

        audio = new AudioMan();
        audio.load();
        audio.setEnabled(prefs.getBoolean("music_on", true), prefs.getBoolean("sound_on", true));
        audio.musicPlay();
        audio.engineStart(); // idle di menu
        audio.windStart();

        Gdx.input.setInputProcessor(this);
        // fix v2.2: tangkap tombol BACK sendiri -> overlay jeda, bukan langsung keluar game
        Gdx.input.setCatchKey(Input.Keys.BACK, true);
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        showMenuBest();
    }

    // ------------------------------------------------------------------ loop

    @Override
    public void render(float delta) {
        float dt = Math.min(delta, 0.05f);
        timeAcc += dt;
        update(dt);
        world.animate(dt, timeAcc);

        Gdx.gl.glViewport(0, 0, W, H);
        float[] sky = skyColor();
        Gdx.gl.glClearColor(sky[0], sky[1], sky[2], 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        // Depth pass bayangan dulu (pola GdxDemo3D), baru render utama
        bike.applyTransforms();
        List<ModelInstance> shadowCasters = bike.getInstances();
        for (Bike t : traffic) {
            t.applyTransforms();
            shadowCasters.addAll(t.getInstances());
        }
        shadowCenter.set(bike.x, 0f, bike.z);
        world.renderShadows(shadowCenter, camera.direction, shadowCasters);

        modelBatch.begin(camera);
        world.render(modelBatch, environment);
        bike.render(modelBatch, environment);
        for (Bike t : traffic) t.render(modelBatch, environment);
        modelBatch.end();

        drawUI();
    }

    private void update(float dt) {
        if (toastT > 0) toastT -= dt;
        if (crashCd > 0) crashCd -= dt;
        // Motor lain selalu melintas (kecuali jeda) — dunia terasa hidup.
        if (state != State.PAUSED) {
            for (Bike t : traffic) t.cruise(dt, t.speed, world.colliders);
        }
        switch (state) {
            case MENU:
                orbitA += dt * 0.08f;
                camera.position.set(
                        (float) Math.cos(orbitA) * 170f, 70f,
                        (float) Math.sin(orbitA) * 170f);
                camera.lookAt(0f, 0f, 0f);
                camera.up.set(Vector3.Y);
                camera.fieldOfView = 45f;
                camera.update();
                audio.engineUpdate(0f, false); // idle
                audio.windUpdate(0f);
                audio.musicRide(false);
                break;
            case COUNTDOWN:
                countdownT += dt;
                String label = countdownT < 1f ? "3"
                        : countdownT < 2f ? "2"
                        : countdownT < 3f ? "1" : "GAS!";
                if (!label.equals(lastCountdown)) {
                    lastCountdown = label;
                    audio.beep(label.equals("GAS!") ? 1.6f : 1.0f);
                }
                audio.engineUpdate(0.12f, true); // geber tipis
                audio.windUpdate(0f);
                updateCamera(dt);
                if (countdownT >= 3.4f) {
                    state = State.RIDING;
                    lastCountdown = "";
                }
                break;
            case RIDING:
                rideTime += dt;
                updateFuel(dt);
                // v2.4: setir dihaluskan tiap frame (deadzone + expo + lerp +
                // batas laju) — input mentah tidak pernah langsung ke motor.
                updateSteer(dt);
                // v2.4: auto-gas ON -> gas penuh terus, pemain hanya setir + rem.
                boolean gasEff = autoGas || gas;
                bike.update(dt, steer, gasEff, brake, world.colliders);
                // Feedback tabrakan: suara + getar + camera shake.
                if (bike.impact > 0.25f && crashCd <= 0f) {
                    audio.crash(dt);
                    Gdx.input.vibrate(70);
                    shake = Math.min(1f, bike.impact);
                    crashCd = 0.5f;
                }
                audio.engineUpdate(bike.speed01(), gasEff);
                audio.windUpdate(bike.speed01());
                audio.musicRide(true);
                updateDayCycle();
                if (mode == Mode.DELIVERY) {
                    deliveryTimeLeft -= dt;
                    if (deliveryTimeLeft <= 0f) finishDelivery(false);
                    else checkDelivery();
                } else {
                    checkSpots();
                    if (nextCp >= GameWorld.SPOT_COUNT) finish();
                }
                updateCamera(dt);
                break;
            case FINISHED:
                audio.engineUpdate(0f, false);
                audio.windUpdate(0f);
                audio.musicRide(false);
                updateCamera(dt);
                break;
            case PAUSED:
                // fix v2.2: motor & waktu dibekukan, hanya render overlay
                break;
        }
    }

    /** Bensin: berkurang saat jalan, isi otomatis di POM, habis = dorong pelan. */
    private void updateFuel(float dt) {
        if (fuel > 0f) {
            fuel -= (0.35f + bike.speed01() * 0.7f) * dt;
            if (fuel <= 0f) {
                fuel = 0f;
                toast("Bensin habis! Dorong pelan ke POM / ketuk RESPAWN");
                audio.beep(0.7f);
            } else if (fuel < 20f && !fuelWarned) {
                fuelWarned = true;
                toast("Bensin menipis! Mampir ke POM");
                audio.beep(0.8f);
            }
        }
        bike.speedCap = (fuel <= 0f) ? 2f : Bike.MAX_SPEED;
        // Isi ulang otomatis bila diam di area POM
        GameWorld.Spot pom = world.spots.get(0);
        float dx = bike.x - pom.x, dz = bike.z - pom.z;
        if (dx * dx + dz * dz < 144f && fuel < 100f) {
            if (!refilling) {
                refilling = true;
                toast("Mengisi bensin di POM...");
            }
            fuel = Math.min(100f, fuel + 45f * dt);
            if (fuel >= 100f && refilling) {
                refilling = false;
                fuelWarned = false;
                toast("Tangki penuh!");
                audio.beep(1.2f);
            }
        } else {
            refilling = false;
        }
    }

    /**
     * v2.4 — target setir -1..1 dari skema aktif, sudah lewat deadzone + expo.
     * Joystick: -joyDX (kanan = belok kanan). Tilt: miring HP — sumbu Y
     * accelerometer (landscape, putaran setang) terhadap posisi netral hasil
     * kalibrasi; jangkauan ~35 derajat pergelangan = belok penuh (sens 50%).
     * Tanda tilt bisa dibalik lewat pengaturan bila terbalik di HP.
     */
    private float steerTarget() {
        float raw;
        if (steerMode == 1 && Gdx.input.isPeripheralAvailable(Input.Peripheral.Accelerometer)) {
            float range = 0.35f + 0.5f * (1f - tiltSens);
            raw = (Gdx.input.getAccelerometerY() - tiltZero) / (9.81f * range);
            if (tiltInvert) raw = -raw;
        } else {
            raw = -joyDX;
        }
        raw = clamp(raw, -1f, 1f);
        float a = Math.abs(raw);
        if (a < STEER_DEADZONE) return 0f;
        float d = (a - STEER_DEADZONE) / (1f - STEER_DEADZONE);
        // expo: koreksi kecil halus (mendekati linear), belok penuh tetap penuh
        float e = d * (0.5f + 0.5f * d * d);
        return Math.signum(raw) * e;
    }

    /**
     * v2.4 — haluskan setir menuju target: lerp ~10/dtk, lalu batasi laju
     * perubahan maks 3/dtk (strength system ala Motoman — koreksi berulang
     * yang terlalu cepat kehilangan efektivitas, anti zig-zag). Saat input
     * dilepas target = 0, jadi setir kembali lurus dengan sendirinya
     * (steering assist ringan).
     */
    private void updateSteer(float dt) {
        float target = steerTarget();
        float next = steer + (target - steer) * Math.min(1f, STEER_SMOOTH * dt);
        steer += clamp(next - steer, -STEER_MAX_RATE * dt, STEER_MAX_RATE * dt);
    }

    /** Siklus siang -> sore (golden hour): matahari & langit menghangat. */
    private void updateDayCycle() {
        dayPhase = Math.min(1f, rideTime / 240f);
        float p = dayPhase;
        // ambient: siang terang -> sore hangat redup
        ambientAttr.color.set(0.55f - 0.10f * p, 0.55f - 0.13f * p, 0.55f - 0.08f * p, 1f);
        // matahari: putih -> oranye keemasan
        if (sun != null) {
            sun.color.set(0.85f, 0.85f - 0.22f * p, 0.85f - 0.45f * p, 1f);
        }
        if (p > 0.75f && !goldenToastShown) {
            goldenToastShown = true;
            toast("Golden hour! Pemandangan lagi bagus-bagusnya");
        }
    }

    /** Warna langit mengikuti siklus siang-sore (dipakai clear color). */
    private float[] skyColor() {
        float p = dayPhase;
        return new float[]{0.52f + 0.46f * p, 0.80f - 0.18f * p, 0.96f - 0.61f * p};
    }

    private void updateCamera(float dt) {
        // v2.4: orbit drag hanya penyesuaian sesekali — bila tidak sedang
        // di-drag, kamera melayang balik ke belakang motor dengan sendirinya.
        if (lookPointer == -1) {
            orbitYaw += (0f - orbitYaw) * Math.min(1f, 0.6f * dt);
            orbitPitch += (0f - orbitPitch) * Math.min(1f, 0.6f * dt);
        }
        float s01 = bike.speed01();
        float fx = (float) Math.sin(bike.heading);
        float fz = (float) Math.cos(bike.heading);
        // Kamera mundur sedikit saat kencang (sense of speed).
        float back = 8f + s01 * 2.5f;
        float tx = bike.x - fx * back;
        float tz = bike.z - fz * back;
        float k = Math.min(1f, 4.5f * dt);
        camX += (tx - camX) * k;
        camZ += (tz - camZ) * k;
        float ty = 3.6f + orbitPitch * 14f + s01 * 1.1f;
        camY += (ty - camY) * Math.min(1f, 3.5f * dt);
        // orbit dari drag jari kanan
        float ox = camX - bike.x, oz = camZ - bike.z;
        float co = (float) Math.cos(orbitYaw), si = (float) Math.sin(orbitYaw);
        float rx = ox * co + oz * si;
        float rz = -ox * si + oz * co;
        // camera shake (tabrakan)
        shake = Math.max(0f, shake - dt * 2.4f);
        float shx = (float) (Math.random() - 0.5) * shake * 0.9f;
        float shy = (float) (Math.random() - 0.5) * shake * 0.7f;
        camera.position.set(bike.x + rx + shx, camY + shy, bike.z + rz);
        camera.lookAt(bike.x + fx * 5f, 1.4f, bike.z + fz * 5f);
        // v2.4: roll kamera mengikuti lean motor (maks ~5 derajat) — rasa
        // berkendara "nempel". Tanda negatif: lean kiri -> kamera miring kiri.
        camera.up.set(Vector3.Y).rotate(camera.direction, -bike.lean() * 5f);
        // FOV melebar halus seiring kecepatan
        float fovT = 45f + s01 * 17f;
        fov += (fovT - fov) * Math.min(1f, 2.8f * dt);
        camera.fieldOfView = fov;
        camera.update();
    }

    /** Checkpoint BERURUTAN: hanya target berikut yang bisa dikunjungi. */
    private void checkSpots() {
        if (nextCp >= GameWorld.SPOT_COUNT) return;
        GameWorld.Spot s = world.spots.get(nextCp);
        float dx = bike.x - s.x, dz = bike.z - s.z;
        if (dx * dx + dz * dz < 81f) { // radius 9
            visited[nextCp] = true;
            world.setRingVisited(nextCp);
            audio.checkpoint();
            // Stempel paspor (tersimpan) + koin tiap checkpoint
            String key = "stamp_" + s.name;
            boolean isNew = !prefs.getBoolean(key, false);
            if (isNew) {
                prefs.putBoolean(key, true);
                prefs.flush();
            }
            addCoins(2);
            toast(s.name + " dikunjungi! (" + (nextCp + 1) + "/" + GameWorld.SPOT_COUNT + ")"
                    + (isNew ? " Stempel baru!" : "") + " +2 koin");
            nextCp++;
            world.setTargetRing(nextCp < GameWorld.SPOT_COUNT ? nextCp : -1);
        }
    }

    /** Antar Paket: sudah sampai rumah target? */
    private void checkDelivery() {
        if (deliveryTarget == null || deliveryDone) return;
        float dx = bike.x - deliveryTarget.x, dz = bike.z - deliveryTarget.z;
        if (dx * dx + dz * dz < 81f) {
            deliveryDone = true;
            finishDelivery(true);
        }
    }

    private void finishDelivery(boolean success) {
        state = State.FINISHED;
        audio.checkpoint();
        clearInputs();
        if (success) {
            addCoins(15);
            finishTitle = "PAKET TERKIRIM!";
            finishTime = fmtTime(rideTime);
            finishNote = "Sisa waktu: " + fmtTime(Math.max(0f, deliveryTimeLeft)) + "  |  +15 koin";
            finishBest = "Koin: " + coins;
            finishIsBest = false;
        } else {
            finishTitle = "WAKTU HABIS";
            finishTime = fmtTime(rideTime);
            finishNote = "Paket belum sampai. Coba lagi!";
            finishBest = "Koin: " + coins;
            finishIsBest = false;
        }
    }

    private void finish() {
        state = State.FINISHED;
        audio.checkpoint();
        String key = (mode == Mode.TRIAL) ? "best_trial" : "best_tour";
        float prev = prefs.getFloat(key, -1f);
        finishIsBest = prev < 0 || rideTime < prev;
        if (finishIsBest) {
            prefs.putFloat(key, rideTime);
            prefs.flush();
        }
        addCoins(10); // hadiah finish: tur & trial sama
        finishTitle = "SELESAI!";
        finishTime = fmtTime(rideTime);
        finishBest = fmtTime(finishIsBest ? rideTime : prev);
        finishNote = (mode == Mode.TRIAL ? "Time Trial" : "Tur Santai")
                + "  |  +10 koin  |  Paspor: " + stampCount() + "/6";
        clearInputs();
    }

    /** Tambah koin (bunyi + simpan). */
    private void addCoins(int n) {
        coins += n;
        prefs.putInteger("coins", coins);
        prefs.flush();
        audio.coin();
    }

    /** Jumlah stempel paspor yang sudah dikumpulkan. */
    private int stampCount() {
        int n = 0;
        for (GameWorld.Spot s : world.spots) {
            if (prefs.getBoolean("stamp_" + s.name, false)) n++;
        }
        return n;
    }

    private void toast(String msg) {
        toastMsg = msg;
        toastT = 2.5f;
    }

    // ------------------------------------------------------------------ API

    public void startGame(Mode m) {
        audio.click();
        mode = m;
        // Posisi awal: delivery mulai dari PASAR
        if (m == Mode.DELIVERY) {
            GameWorld.Spot pasar = world.spots.get(4);
            bike.reset(pasar.x + 16f, pasar.z, (float) Math.PI / 2);
            int idx = (int) (Math.random() * world.deliveryTargets.size());
            deliveryTarget = world.deliveryTargets.get(idx);
            deliveryTimeLeft = 60f;
            deliveryDone = false;
            toast("Antar paket ke " + deliveryTarget.name + " (60 detik)!");
        } else {
            bike.reset(world.startX, world.startZ, world.startHeading);
            deliveryTarget = null;
        }
        for (int i = 0; i < GameWorld.SPOT_COUNT; i++) visited[i] = false;
        nextCp = 0;
        world.resetRings();
        world.setTargetRing(m == Mode.DELIVERY ? -1 : 0);
        float fx = (float) Math.sin(bike.heading);
        float fz = (float) Math.cos(bike.heading);
        camX = bike.x - fx * 8f;
        camZ = bike.z - fz * 8f;
        camY = 3.6f;
        fov = 45f;
        shake = 0f;
        rideTime = 0f;
        countdownT = 0f;
        lastCountdown = "";
        orbitYaw = 0f;
        orbitPitch = 0f;
        fuel = 100f;
        fuelWarned = false;
        refilling = false;
        dayPhase = 0f;
        goldenToastShown = false;
        clearInputs();
        state = State.COUNTDOWN;
    }

    /** Terapkan varian + warna motor dari pengaturan ke sebuah Bike. */
    private void applyBikePrefs(Bike b) {
        b.variant = prefs.getInteger("bike_variant", 0);
        int ci = prefs.getInteger("bike_color", 0);
        b.bodyColor.set(BIKE_COLORS[ci]);
    }

    public void backToMenu() {
        audio.click();
        clearInputs();
        state = State.MENU;
        menuTab = MenuTab.MAIN;
        showMenuBest();
    }

    /** Pilih varian motor (bebek/trail) — rebuild model agar langsung kelihatan. */
    private void selectVariant(int v) {
        prefs.putInteger("bike_variant", v);
        prefs.flush();
        bike.variant = v;
        rebuildBikeModel();
        audio.click();
    }

    /** Pilih/beli warna motor. */
    private void selectColor(int idx) {
        if (!isColorUnlocked(idx)) {
            int price = BIKE_COLOR_PRICE[idx];
            if (coins < price) {
                toast("Koin kurang! Butuh " + price + " koin (punya " + coins + ")");
                audio.beep(0.7f);
                return;
            }
            coins -= price;
            prefs.putInteger("coins", coins);
            prefs.putBoolean("color_unlocked_" + idx, true);
            prefs.flush();
            toast(BIKE_COLOR_NAMES[idx] + " dibuka! -" + price + " koin");
        }
        prefs.putInteger("bike_color", idx);
        prefs.flush();
        bike.bodyColor.set(BIKE_COLORS[idx]);
        rebuildBikeModel();
        audio.click();
    }

    /** Bangun ulang model motor (varian/warna baru). */
    private void rebuildBikeModel() {
        bike.dispose();
        bike = new Bike();
        applyBikePrefs(bike);
        bike.buildModel();
        bike.reset(world.startX, world.startZ, world.startHeading);
    }

    private void clearInputs() {
        joyPointer = lookPointer = gasPointer = brakePointer = -1;
        steer = 0f;
        joyDX = 0f;
        gas = false;
        brake = false;
    }

    private void showMenuBest() {
        float bt = prefs.getFloat("best_tour", -1f);
        float br = prefs.getFloat("best_trial", -1f);
        StringBuilder s = new StringBuilder();
        s.append("Paspor: ").append(stampCount()).append("/6 stempel   Koin: ").append(coins);
        if (bt > 0 || br > 0) s.append("\n");
        if (bt > 0) s.append("Tur tercepat: ").append(fmtTime(bt)).append("   ");
        if (br > 0) s.append("Trial tercepat: ").append(fmtTime(br));
        menuBest = s.toString();
    }

    private String fmtTime(float sec) {
        int s = (int) sec;
        return String.format("%02d:%02d", s / 60, s % 60);
    }

    // ------------------------------------------------------------------ UI

    private void drawUI() {
        Matrix4 ortho = new Matrix4().setToOrtho(0, W, 0, H, -1, 1);
        shapes.setProjectionMatrix(ortho);
        spriteBatch.setProjectionMatrix(ortho);

        if (state == State.RIDING || state == State.COUNTDOWN) {
            drawControls();
            drawHud();
            drawMinimap();
        }
        if (state == State.COUNTDOWN && !lastCountdown.isEmpty()) {
            spriteBatch.begin();
            layout.setText(bigFont, lastCountdown);
            bigFont.draw(spriteBatch, layout, W / 2f - layout.width / 2f, H / 2f + layout.height / 2f);
            spriteBatch.end();
        }
        if (state == State.MENU) drawMenu();
        if (state == State.FINISHED) drawFinish();
        if (state == State.PAUSED) drawPause();
        if (toastT > 0 && !toastMsg.isEmpty()) {
            spriteBatch.begin();
            layout.setText(font, toastMsg);
            font.draw(spriteBatch, layout, W / 2f - layout.width / 2f, H - 120f);
            spriteBatch.end();
        }
    }

    private void drawControls() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        // joystick kiri — hanya di mode joystick (mode tilt: setir = miring HP)
        if (steerMode == 0) {
            shapes.setColor(0.25f, 0.25f, 0.25f, 1f);
            shapes.circle(joyCX, joyCY, joyR);
            shapes.setColor(0.7f, 0.7f, 0.7f, 1f);
            shapes.circle(joyCX + joyDX * joyR * 0.55f, joyCY, 45f);
        }
        // tombol REM selalu ada; GAS hanya di mode manual (auto-gas: motor jalan sendiri)
        shapes.setColor(gas ? 0.95f : 0.78f, 0.16f, 0.16f, 1f);
        shapes.circle(brakeCX, brakeCY, brakeR);
        if (!autoGas) {
            shapes.setColor(0.18f, brake ? 0.75f : 0.49f, 0.20f, 1f);
            shapes.circle(gasCX, gasCY, gasR);
        }
        shapes.end();

        spriteBatch.begin();
        layout.setText(font, "REM");
        font.draw(spriteBatch, layout, brakeCX - layout.width / 2f, brakeCY + layout.height / 2f);
        if (autoGas) {
            layout.setText(font, "AUTO");
            font.draw(spriteBatch, layout, gasCX - layout.width / 2f, gasCY + layout.height / 2f);
        } else {
            layout.setText(font, "GAS");
            font.draw(spriteBatch, layout, gasCX - layout.width / 2f, gasCY + layout.height / 2f);
        }
        if (steerMode == 1) {
            layout.setText(font, "TILT");
            font.draw(spriteBatch, layout, joyCX - layout.width / 2f, joyCY + layout.height / 2f);
        }
        spriteBatch.end();
    }

    private void drawHud() {
        spriteBatch.begin();
        // speedometer besar kiri atas
        String spd = String.valueOf(bike.speedKmh());
        layout.setText(speedFont, spd);
        speedFont.draw(spriteBatch, layout, 24f, H - 40f);
        layout.setText(font, "km/h");
        font.draw(spriteBatch, layout, 24f + layout.width + 70f, H - 48f);
        // timer tengah atas
        String t = fmtTime(rideTime);
        layout.setText(font, t);
        font.draw(spriteBatch, t, W / 2f - layout.width / 2f, H - 30f);
        // target checkpoint + jarak di bawah timer
        if (mode == Mode.DELIVERY && deliveryTarget != null) {
            int dist = (int) Math.hypot(bike.x - deliveryTarget.x, bike.z - deliveryTarget.z);
            String tg = "ANTAR KE " + deliveryTarget.name + " - " + dist + " m - "
                    + fmtTime(Math.max(0f, deliveryTimeLeft));
            layout.setText(font, tg);
            font.draw(spriteBatch, tg, W / 2f - layout.width / 2f, H - 80f);
        } else if (nextCp < GameWorld.SPOT_COUNT) {
            GameWorld.Spot s = world.spots.get(nextCp);
            int dist = (int) Math.hypot(bike.x - s.x, bike.z - s.z);
            String tg = "TARGET: " + s.name + " (" + (nextCp + 1) + "/"
                    + GameWorld.SPOT_COUNT + ") - " + dist + " m";
            layout.setText(font, tg);
            font.draw(spriteBatch, tg, W / 2f - layout.width / 2f, H - 80f);
        } else {
            layout.setText(font, "FINISH!");
            font.draw(spriteBatch, layout, W / 2f - layout.width / 2f, H - 80f);
        }
        // koin kanan atas (di bawah minimap)
        String cn = "Koin: " + coins;
        layout.setText(font, cn);
        font.draw(spriteBatch, layout, W - layout.width - 24f, H - 320f);
        spriteBatch.end();

        // bar bensin kiri atas (di bawah speedometer)
        float bx = 24f, by = H - 170f, bw = 300f, bh = 34f;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.05f, 0.05f, 0.08f, 0.8f);
        shapes.rect(bx - 6f, by - 6f, bw + 12f, bh + 12f);
        shapes.setColor(0.2f, 0.2f, 0.22f, 1f);
        shapes.rect(bx, by, bw, bh);
        if (fuel > 50f) shapes.setColor(0.2f, 0.8f, 0.3f, 1f);
        else if (fuel > 20f) shapes.setColor(0.95f, 0.75f, 0.15f, 1f);
        else shapes.setColor(0.9f, 0.2f, 0.2f, 1f);
        shapes.rect(bx, by, bw * fuel / 100f, bh);
        shapes.end();
        spriteBatch.begin();
        layout.setText(font, "BENSIN " + (int) fuel + "%");
        font.draw(spriteBatch, layout, bx, by + bh + 44f);
        spriteBatch.end();

        // tombol respawn saat bensin habis
        if (fuel <= 0f) {
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(0.85f, 0.2f, 0.2f, 1f);
            shapes.rect(respBX, respBY, respBW, respBH);
            shapes.end();
            spriteBatch.begin();
            drawButtonLabel("RESPAWN DI POM", respBX, respBY, respBW, respBH);
            spriteBatch.end();
        }
    }

    /** Minimap lingkaran: jalan, checkpoint, posisi + arah motor. */
    private void drawMinimap() {
        float mx = W - 135f, my = H - 185f, mr = 105f;
        float sc = mr / 210f; // dunia +-210 -> radius peta
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.04f, 0.06f, 0.10f, 0.80f);
        shapes.circle(mx, my, mr);
        // jalan melingkar
        shapes.setColor(0.42f, 0.42f, 0.46f, 1f);
        int n = 48;
        float px = 0f, py = 0f;
        for (int i = 0; i <= n; i++) {
            float a = (float) (i / (float) n * 2f * Math.PI);
            float x = mx + (float) Math.cos(a) * 94f * sc;
            float y = my + (float) Math.sin(a) * 94f * sc;
            if (i > 0) shapes.rectLine(px, py, x, y, 7f);
            px = x;
            py = y;
        }
        // checkpoint: hijau = sudah, kuning denyut = target, abu = nanti
        for (int i = 0; i < GameWorld.SPOT_COUNT; i++) {
            GameWorld.Spot s = world.spots.get(i);
            float sx = mx + s.x * sc, sy = my + s.z * sc;
            if (visited[i]) shapes.setColor(0.2f, 0.85f, 0.3f, 1f);
            else if (i == nextCp && mode != Mode.DELIVERY) {
                float p = 0.65f + 0.35f * (float) Math.sin(timeAcc * 5.2f);
                shapes.setColor(1f, 0.72f * p + 0.28f, 0.15f, 1f);
            } else shapes.setColor(0.45f, 0.45f, 0.48f, 1f);
            shapes.circle(sx, sy, 9f);
        }
        // target antar paket (ungu)
        if (mode == Mode.DELIVERY && deliveryTarget != null && !deliveryDone) {
            shapes.setColor(0.7f, 0.3f, 0.95f, 1f);
            shapes.circle(mx + deliveryTarget.x * sc, my + deliveryTarget.z * sc, 10f);
        }
        // start putih
        shapes.setColor(0.95f, 0.95f, 0.95f, 1f);
        shapes.circle(mx + world.startX * sc, my + world.startZ * sc, 6f);
        // motor lain (oranye)
        shapes.setColor(1f, 0.55f, 0.15f, 1f);
        for (Bike t : traffic) {
            shapes.circle(mx + t.x * sc, my + t.z * sc, 5f);
        }
        // motor: titik + garis arah
        float bx = mx + bike.x * sc, by = my + bike.z * sc;
        float hx = (float) Math.sin(bike.heading), hz = (float) Math.cos(bike.heading);
        shapes.setColor(1f, 0.25f, 0.25f, 1f);
        shapes.rectLine(bx, by, bx + hx * 20f, by + hz * 20f, 4f);
        shapes.setColor(1f, 1f, 1f, 1f);
        shapes.circle(bx, by, 7f);
        shapes.end();
    }

    private void drawMenu() {
        if (menuTab == MenuTab.HELP) {
            drawOverlay("CARA MAIN", HELP_LINES);
            return;
        }
        if (menuTab == MenuTab.CREDITS) {
            drawOverlay("KREDIT ASET", CREDIT_LINES);
            return;
        }
        if (menuTab == MenuTab.MOTOR) {
            drawMotorTab();
            return;
        }
        if (menuTab == MenuTab.SETTINGS) {
            drawSettingsTab();
            return;
        }
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.18f, 0.49f, 0.20f, 1f);
        shapes.rect(tourBX, tourBY, tourBW, tourBH);
        shapes.setColor(0.13f, 0.35f, 0.65f, 1f);
        shapes.rect(trialBX, trialBY, trialBW, trialBH);
        shapes.setColor(0.85f, 0.45f, 0.10f, 1f);
        shapes.rect(deliverBX, deliverBY, deliverBW, deliverBH);
        shapes.setColor(0.30f, 0.30f, 0.34f, 1f);
        shapes.rect(motorBX, motorBY, motorBW, motorBH);
        shapes.rect(setBX, setBY, setBW, setBH);
        shapes.rect(helpBX, helpBY, helpBW, helpBH);
        shapes.rect(credBX, credBY, credBW, credBH);
        shapes.end();

        spriteBatch.begin();
        layout.setText(bigFont, "TOURING 3D");
        bigFont.draw(spriteBatch, layout, W / 2f - layout.width / 2f, H * 0.86f);
        layout.setText(font, "Touring motor keliling pulau Indonesia - full offline");
        font.draw(spriteBatch, layout, W / 2f - layout.width / 2f, H * 0.78f);
        if (!menuBest.isEmpty()) {
            layout.setText(font, menuBest);
            font.draw(spriteBatch, layout, W / 2f - layout.width / 2f, H * 0.70f);
        }
        drawButtonLabel("MULAI TOUR", tourBX, tourBY, tourBW, tourBH);
        drawButtonLabel("TIME TRIAL", trialBX, trialBY, trialBW, trialBH);
        drawButtonLabel("ANTAR PAKET", deliverBX, deliverBY, deliverBW, deliverBH);
        drawButtonLabel("MOTOR", motorBX, motorBY, motorBW, motorBH);
        drawButtonLabel("PENGATURAN", setBX, setBY, setBW, setBH);
        drawButtonLabel("CARA MAIN", helpBX, helpBY, helpBW, helpBH);
        drawButtonLabel("KREDIT", credBX, credBY, credBW, credBH);
        layout.setText(font, VERSION_LABEL);
        font.draw(spriteBatch, layout, W - layout.width - 16f, 40f);
        spriteBatch.end();
    }

    /** Tab MOTOR: pilih varian (bebek/trail) + warna (koin). */
    private void drawMotorTab() {
        float pw = 1100f, ph = 640f;
        float px = W / 2f - pw / 2f, py = H / 2f - ph / 2f;
        int variant = prefs.getInteger("bike_variant", 0);
        int color = prefs.getInteger("bike_color", 0);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.55f);
        shapes.rect(0, 0, W, H);
        shapes.setColor(0.07f, 0.09f, 0.13f, 1f);
        shapes.rect(px, py, pw, ph);
        // varian: hijau bila terpilih
        shapes.setColor(variant == 0 ? 0.18f : 0.30f, variant == 0 ? 0.49f : 0.30f, 0.20f, 1f);
        shapes.rect(var0BX, var0BY, var0BW, var0BH);
        shapes.setColor(variant == 1 ? 0.18f : 0.30f, variant == 1 ? 0.49f : 0.30f, 0.20f, 1f);
        shapes.rect(var1BX, var1BY, var1BW, var1BH);
        // warna: warna motornya langsung
        for (int i = 0; i < 3; i++) {
            float bx = (i == 0) ? col0BX : (i == 1) ? col1BX : col2BX;
            Color c = BIKE_COLORS[i];
            shapes.setColor(c.r, c.g, c.b, 1f);
            shapes.rect(bx, col0BY, colBW, colBH);
            if (color == i) {
                shapes.setColor(1f, 1f, 1f, 1f);
                shapes.rect(bx - 6f, col0BY - 6f, colBW + 12f, 8f);
                shapes.rect(bx - 6f, col0BY + colBH - 2f, colBW + 12f, 8f);
            }
        }
        shapes.setColor(0.30f, 0.30f, 0.34f, 1f);
        shapes.rect(backBX, backBY, backBW, backBH);
        shapes.end();

        spriteBatch.begin();
        layout.setText(bigFont, "MOTOR");
        bigFont.draw(spriteBatch, layout, W / 2f - layout.width / 2f, py + ph - 60f);
        layout.setText(font, "Koin: " + coins);
        font.draw(spriteBatch, layout, W / 2f - layout.width / 2f, py + ph - 140f);
        drawButtonLabel("BEBEK (Beat)", var0BX, var0BY, var0BW, var0BH);
        drawButtonLabel("TRAIL", var1BX, var1BY, var1BW, var1BH);
        for (int i = 0; i < 3; i++) {
            float bx = (i == 0) ? col0BX : (i == 1) ? col1BX : col2BX;
            String lbl = BIKE_COLOR_NAMES[i]
                    + (color == i ? " (dipakai)" : isColorUnlocked(i) ? "" : " - " + BIKE_COLOR_PRICE[i] + " koin");
            drawButtonLabel(lbl, bx, col0BY, colBW, colBH);
        }
        layout.setText(font, "Trail: roda besar, bodi tinggi. Warna baru dibeli pakai koin.");
        font.draw(spriteBatch, layout, W / 2f - layout.width / 2f, py + 130f);
        drawButtonLabel("KEMBALI", backBX, backBY, backBW, backBH);
        spriteBatch.end();
    }

    /** Tab PENGATURAN: musik, suara, + kontrol v2.4 (auto-gas, skema setir,
     *  sensitivitas tilt, kalibrasi tilt, balik tilt). */
    private void drawSettingsTab() {
        float pw = 1000f, ph = 980f;
        float px = W / 2f - pw / 2f, py = H / 2f - ph / 2f;
        boolean musicOn = prefs.getBoolean("music_on", true);
        boolean soundOn = prefs.getBoolean("sound_on", true);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.55f);
        shapes.rect(0, 0, W, H);
        shapes.setColor(0.07f, 0.09f, 0.13f, 1f);
        shapes.rect(px, py, pw, ph);
        shapes.setColor(musicOn ? 0.18f : 0.30f, musicOn ? 0.49f : 0.30f, 0.20f, 1f);
        shapes.rect(togMusBX, togMusBY, togBW, togBH);
        shapes.setColor(soundOn ? 0.18f : 0.30f, soundOn ? 0.49f : 0.30f, 0.20f, 1f);
        shapes.rect(togSndBX, togSndBY, togBW, togBH);
        shapes.setColor(autoGas ? 0.18f : 0.30f, autoGas ? 0.49f : 0.30f, 0.20f, 1f);
        shapes.rect(togGasBX, togGasBY, togBW, togBH);
        shapes.setColor(0.13f, 0.35f, 0.65f, 1f);
        shapes.rect(togSteerBX, togSteerBY, togBW, togBH);
        shapes.setColor(0.13f, 0.35f, 0.65f, 1f);
        shapes.rect(togSensBX, togSensBY, togBW, togBH);
        shapes.setColor(0.85f, 0.45f, 0.10f, 1f);
        shapes.rect(togCalBX, togCalBY, togBW, togBH);
        shapes.setColor(tiltInvert ? 0.18f : 0.30f, tiltInvert ? 0.49f : 0.30f, 0.20f, 1f);
        shapes.rect(togInvBX, togInvBY, togBW, togBH);
        shapes.setColor(0.30f, 0.30f, 0.34f, 1f);
        shapes.rect(setBackBX, setBackBY, setBackBW, setBackBH);
        shapes.end();

        spriteBatch.begin();
        layout.setText(bigFont, "PENGATURAN");
        bigFont.draw(spriteBatch, layout, W / 2f - layout.width / 2f, py + ph - 60f);
        drawButtonLabel("MUSIK: " + (musicOn ? "ON" : "OFF"), togMusBX, togMusBY, togBW, togBH);
        drawButtonLabel("SUARA: " + (soundOn ? "ON" : "OFF"), togSndBX, togSndBY, togBW, togBH);
        drawButtonLabel("AUTO GAS: " + (autoGas ? "ON" : "OFF"), togGasBX, togGasBY, togBW, togBH);
        drawButtonLabel("SETIR: " + (steerMode == 1 ? "TILT" : "JOYSTICK"),
                togSteerBX, togSteerBY, togBW, togBH);
        drawButtonLabel("SENSITIVITAS TILT: " + (int) (tiltSens * 100f) + "%",
                togSensBX, togSensBY, togBW, togBH);
        drawButtonLabel("KALIBRASI TILT", togCalBX, togCalBY, togBW, togBH);
        drawButtonLabel("BALIK TILT: " + (tiltInvert ? "ON" : "OFF"),
                togInvBX, togInvBY, togBW, togBH);
        drawButtonLabel("KEMBALI", setBackBX, setBackBY, setBackBW, setBackBH);
        spriteBatch.end();
    }

    private boolean isColorUnlocked(int idx) {
        if (idx == 0) return true;
        return prefs.getBoolean("color_unlocked_" + idx, false);
    }

    private void drawButtonLabel(String text, float bx, float by, float bw, float bh) {
        layout.setText(font, text);
        font.draw(spriteBatch, layout, bx + bw / 2f - layout.width / 2f, by + bh / 2f + layout.height / 2f);
    }

    /** Panel overlay (cara main / kredit) + tombol KEMBALI. */
    private void drawOverlay(String title, String[] lines) {
        float pw = 1000f, ph = 120f + lines.length * 52f;
        float px = W / 2f - pw / 2f, py = H / 2f - ph / 2f;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.55f);
        shapes.rect(0, 0, W, H);
        shapes.setColor(0.07f, 0.09f, 0.13f, 1f);
        shapes.rect(px, py, pw, ph);
        shapes.setColor(0.30f, 0.30f, 0.34f, 1f);
        shapes.rect(backBX, backBY, backBW, backBH);
        shapes.end();

        spriteBatch.begin();
        layout.setText(bigFont, title);
        bigFont.draw(spriteBatch, layout, W / 2f - layout.width / 2f, py + ph - 60f);
        float y = py + ph - 150f;
        for (String line : lines) {
            layout.setText(font, line);
            font.draw(spriteBatch, layout, W / 2f - layout.width / 2f, y);
            y -= 52f;
        }
        drawButtonLabel("KEMBALI", backBX, backBY, backBW, backBH);
        spriteBatch.end();
    }

    /** Overlay jeda (fix v2.2): tombol BACK tidak lagi langsung keluar game. */
    private void drawPause() {
        float pw = 620f, ph = 420f;
        float px = W / 2f - pw / 2f, py = H / 2f - ph / 2f;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0f, 0f, 0f, 0.55f);
        shapes.rect(0, 0, W, H);
        shapes.setColor(0.07f, 0.09f, 0.13f, 1f);
        shapes.rect(px, py, pw, ph);
        shapes.setColor(0.18f, 0.49f, 0.20f, 1f);
        shapes.rect(pauseLanBX, pauseLanBY, pauseBW, pauseBH);
        shapes.setColor(0.30f, 0.30f, 0.34f, 1f);
        shapes.rect(pauseMenuBX, pauseMenuBY, pauseBW, pauseBH);
        shapes.end();

        spriteBatch.begin();
        layout.setText(bigFont, "JEDA");
        bigFont.draw(spriteBatch, layout, W / 2f - layout.width / 2f, py + ph - 60f);
        drawButtonLabel("LANJUT", pauseLanBX, pauseLanBY, pauseBW, pauseBH);
        drawButtonLabel("MENU", pauseMenuBX, pauseMenuBY, pauseBW, pauseBH);
        spriteBatch.end();
    }

    private void drawFinish() {
        float pw = 700f, ph = 380f;
        float px = W / 2f - pw / 2f, py = H / 2f - ph / 2f;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.05f, 0.05f, 0.08f, 1f);
        shapes.rect(px, py, pw, ph);
        shapes.end();

        spriteBatch.begin();
        layout.setText(bigFont, finishTitle);
        bigFont.draw(spriteBatch, layout, W / 2f - layout.width / 2f, py + ph - 60f);
        layout.setText(font, "Waktu: " + finishTime);
        font.draw(spriteBatch, layout, W / 2f - layout.width / 2f, py + ph - 160f);
        if (mode == Mode.DELIVERY) {
            layout.setText(font, finishNote);
            font.draw(spriteBatch, layout, W / 2f - layout.width / 2f, py + ph - 220f);
            layout.setText(font, finishBest);
            font.draw(spriteBatch, layout, W / 2f - layout.width / 2f, py + ph - 280f);
        } else {
            layout.setText(font, finishIsBest ? "Rekor baru! Tercepat: " + finishBest : "Tercepat: " + finishBest);
            font.draw(spriteBatch, layout, W / 2f - layout.width / 2f, py + ph - 220f);
            layout.setText(font, finishNote);
            font.draw(spriteBatch, layout, W / 2f - layout.width / 2f, py + ph - 280f);
        }
        layout.setText(font, "Ketuk untuk kembali");
        font.draw(spriteBatch, layout, W / 2f - layout.width / 2f, py + 50f);
        spriteBatch.end();
    }

    // ------------------------------------------------------------------ input

    private float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private boolean inRect(float x, float y, float rx, float ry, float rw, float rh) {
        return x >= rx && x <= rx + rw && y >= ry && y <= ry + rh;
    }

    private float dist(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2, dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        float x = screenX;
        float y = H - screenY; // ke y-up
        if (state == State.MENU) {
            if (menuTab == MenuTab.MOTOR) {
                if (inRect(x, y, backBX, backBY, backBW, backBH)) {
                    menuTab = MenuTab.MAIN;
                    audio.click();
                } else if (inRect(x, y, var0BX, var0BY, var0BW, var0BH)) {
                    selectVariant(0);
                } else if (inRect(x, y, var1BX, var1BY, var1BW, var1BH)) {
                    selectVariant(1);
                } else if (inRect(x, y, col0BX, col0BY, colBW, colBH)) {
                    selectColor(0);
                } else if (inRect(x, y, col1BX, col0BY, colBW, colBH)) {
                    selectColor(1);
                } else if (inRect(x, y, col2BX, col0BY, colBW, colBH)) {
                    selectColor(2);
                }
                return true;
            }
            if (menuTab == MenuTab.SETTINGS) {
                if (inRect(x, y, setBackBX, setBackBY, setBackBW, setBackBH)) {
                    menuTab = MenuTab.MAIN;
                    audio.click();
                } else if (inRect(x, y, togMusBX, togMusBY, togBW, togBH)) {
                    boolean on = !prefs.getBoolean("music_on", true);
                    prefs.putBoolean("music_on", on);
                    prefs.flush();
                    audio.setEnabled(on, prefs.getBoolean("sound_on", true));
                    audio.click();
                } else if (inRect(x, y, togSndBX, togSndBY, togBW, togBH)) {
                    boolean on = !prefs.getBoolean("sound_on", true);
                    prefs.putBoolean("sound_on", on);
                    prefs.flush();
                    audio.setEnabled(prefs.getBoolean("music_on", true), on);
                    audio.click();
                } else if (inRect(x, y, togGasBX, togGasBY, togBW, togBH)) {
                    autoGas = !autoGas;
                    prefs.putBoolean("auto_gas", autoGas);
                    prefs.flush();
                    toast(autoGas ? "Auto Gas ON - motor jalan sendiri"
                            : "Auto Gas OFF - pakai tombol GAS");
                    audio.click();
                } else if (inRect(x, y, togSteerBX, togSteerBY, togBW, togBH)) {
                    if (steerMode == 0) {
                        if (!Gdx.input.isPeripheralAvailable(Input.Peripheral.Accelerometer)) {
                            toast("Accelerometer tidak ada di perangkat ini");
                        } else {
                            steerMode = 1;
                            prefs.putInteger("steer_mode", 1);
                            prefs.flush();
                            toast("Setir TILT - miringkan HP. Kalibrasi dulu bila miring.");
                        }
                    } else {
                        steerMode = 0;
                        prefs.putInteger("steer_mode", 0);
                        prefs.flush();
                        toast("Setir JOYSTICK");
                    }
                    audio.click();
                } else if (inRect(x, y, togSensBX, togSensBY, togBW, togBH)) {
                    int idx = 0;
                    for (int i = 0; i < TILT_SENS_STEPS.length; i++) {
                        if (Math.abs(TILT_SENS_STEPS[i] - tiltSens) < 0.001f) idx = i;
                    }
                    tiltSens = TILT_SENS_STEPS[(idx + 1) % TILT_SENS_STEPS.length];
                    prefs.putFloat("tilt_sens", tiltSens);
                    prefs.flush();
                    toast("Sensitivitas tilt: " + (int) (tiltSens * 100f) + "%");
                    audio.click();
                } else if (inRect(x, y, togCalBX, togCalBY, togBW, togBH)) {
                    if (!Gdx.input.isPeripheralAvailable(Input.Peripheral.Accelerometer)) {
                        toast("Accelerometer tidak ada di perangkat ini");
                    } else {
                        tiltZero = Gdx.input.getAccelerometerY();
                        prefs.putFloat("tilt_zero", tiltZero);
                        prefs.flush();
                        toast("Tilt dikalibrasi - tahan HP di posisi main");
                    }
                    audio.click();
                } else if (inRect(x, y, togInvBX, togInvBY, togBW, togBH)) {
                    tiltInvert = !tiltInvert;
                    prefs.putBoolean("tilt_invert", tiltInvert);
                    prefs.flush();
                    toast(tiltInvert ? "Arah tilt dibalik" : "Arah tilt normal");
                    audio.click();
                }
                return true;
            }
            if (menuTab != MenuTab.MAIN) {
                if (inRect(x, y, backBX, backBY, backBW, backBH)) {
                    menuTab = MenuTab.MAIN;
                    audio.click();
                }
                return true;
            }
            if (inRect(x, y, tourBX, tourBY, tourBW, tourBH)) startGame(Mode.TOUR);
            else if (inRect(x, y, trialBX, trialBY, trialBW, trialBH)) startGame(Mode.TRIAL);
            else if (inRect(x, y, deliverBX, deliverBY, deliverBW, deliverBH)) startGame(Mode.DELIVERY);
            else if (inRect(x, y, motorBX, motorBY, motorBW, motorBH)) {
                menuTab = MenuTab.MOTOR;
                audio.click();
            } else if (inRect(x, y, setBX, setBY, setBW, setBH)) {
                menuTab = MenuTab.SETTINGS;
                audio.click();
            } else if (inRect(x, y, helpBX, helpBY, helpBW, helpBH)) {
                menuTab = MenuTab.HELP;
                audio.click();
            } else if (inRect(x, y, credBX, credBY, credBW, credBH)) {
                menuTab = MenuTab.CREDITS;
                audio.click();
            }
            return true;
        }
        if (state == State.FINISHED) {
            backToMenu();
            return true;
        }
        if (state == State.PAUSED) {
            // fix v2.2: overlay jeda
            if (inRect(x, y, pauseLanBX, pauseLanBY, pauseBW, pauseBH)) resumeGame();
            else if (inRect(x, y, pauseMenuBX, pauseMenuBY, pauseBW, pauseBH)) backToMenu();
            return true;
        }
        if (state != State.RIDING) return false;
        // Respawn di POM saat bensin habis
        if (fuel <= 0f && inRect(x, y, respBX, respBY, respBW, respBH)) {
            GameWorld.Spot pom = world.spots.get(0);
            bike.reset(pom.x - 10f, pom.z, 0f);
            fuel = 25f;
            fuelWarned = false;
            toast("Respawn di POM - bensin 25%");
            audio.click();
            return true;
        }
        // v2.4: tombol GAS hanya di mode manual — auto-gas: motor jalan sendiri.
        if (!autoGas && dist(x, y, gasCX, gasCY) < gasR && gasPointer == -1) {
            gasPointer = pointer;
            gas = true;
            return true;
        }
        if (dist(x, y, brakeCX, brakeCY) < brakeR && brakePointer == -1) {
            brakePointer = pointer;
            brake = true;
            return true;
        }
        if (x < W / 2f && joyPointer == -1) {
            joyPointer = pointer;
            joyBaseX = x;
            joyDX = 0f;
            steer = 0f;
            return true;
        }
        if (lookPointer == -1) {
            lookPointer = pointer;
            lookLastX = x;
            lookLastY = y;
            return true;
        }
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        float x = screenX;
        float y = H - screenY;
        if (pointer == joyPointer) {
            // v2.4: hanya simpan posisi joystick mentah — setir dihaluskan
            // tiap frame di updateSteer(), bukan langsung di sini.
            joyDX = clamp((x - joyBaseX) / joyR, -1f, 1f);
            return true;
        }
        if (pointer == lookPointer) {
            orbitYaw -= (x - lookLastX) * 0.006f;
            orbitPitch = clamp(orbitPitch + (y - lookLastY) * 0.004f, -0.15f, 1f);
            lookLastX = x;
            lookLastY = y;
            return true;
        }
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        releasePointer(pointer);
        return true;
    }

    @Override
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        releasePointer(pointer);
        return true;
    }

    private void releasePointer(int pointer) {
        if (pointer == joyPointer) {
            joyPointer = -1;
            // v2.4: setir TIDAK langsung nol — updateSteer() meluruhkannya
            // halus ke 0 (steering assist), jadi motor kembali lurus sendiri.
            joyDX = 0f;
        }
        if (pointer == lookPointer) lookPointer = -1;
        if (pointer == gasPointer) {
            gasPointer = -1;
            gas = false;
        }
        if (pointer == brakePointer) {
            brakePointer = -1;
            brake = false;
        }
    }

    @Override
    public boolean keyDown(int keycode) {
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        // fix v2.2: BACK -> overlay jeda (LANJUT/MENU), bukan keluar game
        if (keycode == Input.Keys.BACK || keycode == Input.Keys.ESCAPE) {
            if (state == State.RIDING || state == State.COUNTDOWN) {
                pauseGame();
                return true;
            }
            if (state == State.PAUSED) {
                resumeGame();
                return true;
            }
        }
        return false;
    }

    /** Masuk overlay jeda: bekukan motor & suara. */
    private void pauseGame() {
        audio.click();
        clearInputs();
        pausedFrom = state;
        state = State.PAUSED;
        if (audio != null) {
            audio.musicPause();
            audio.engineStop();
            audio.windStop();
        }
    }

    /** Lanjut dari overlay jeda. */
    private void resumeGame() {
        audio.click();
        clearInputs();
        state = pausedFrom; // kembali ke RIDING atau COUNTDOWN
        if (audio != null) {
            audio.musicPlay();
            audio.engineStart();
            audio.windStart();
        }
    }

    @Override
    public boolean keyTyped(char character) {
        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        return false;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        return false;
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void resize(int width, int height) {
        W = width;
        H = height;
        if (camera != null) {
            camera.viewportWidth = width;
            camera.viewportHeight = height;
            camera.update();
        }
        joyCX = 170f;
        joyCY = 170f;
        joyR = 120f;
        gasR = 95f;
        gasCX = W - 140f;
        gasCY = 150f;
        brakeR = 75f;
        brakeCX = W - 330f;
        brakeCY = 130f;
        // 3 tombol mode utama
        tourBW = 440f;
        tourBH = 100f;
        tourBX = W / 2f - tourBW / 2f;
        tourBY = H * 0.52f - tourBH / 2f;
        trialBW = 440f;
        trialBH = 100f;
        trialBX = W / 2f - trialBW / 2f;
        trialBY = H * 0.37f - trialBH / 2f;
        deliverBW = 440f;
        deliverBH = 100f;
        deliverBX = W / 2f - deliverBW / 2f;
        deliverBY = H * 0.22f - deliverBH / 2f;
        // 4 tombol kecil sebaris di bawah
        motorBW = 300f;
        motorBH = 80f;
        setBW = 300f;
        setBH = 80f;
        helpBW = 300f;
        helpBH = 80f;
        credBW = 300f;
        credBH = 80f;
        float smallY = H * 0.07f - 40f;
        motorBX = W / 2f - 630f;
        motorBY = smallY;
        setBX = W / 2f - 310f;
        setBY = smallY;
        helpBX = W / 2f + 10f;
        helpBY = smallY;
        credBX = W / 2f + 330f;
        credBY = smallY;
        backBW = 360f;
        backBH = 90f;
        backBX = W / 2f - backBW / 2f;
        backBY = H / 2f - 330f;
        // fix v2.2: tombol overlay jeda
        pauseBW = 440f;
        pauseBH = 110f;
        pauseLanBX = W / 2f - pauseBW / 2f;
        pauseLanBY = H / 2f + 10f;
        pauseMenuBX = W / 2f - pauseBW / 2f;
        pauseMenuBY = H / 2f - 140f;
        // tombol respawn bensin habis
        respBW = 440f;
        respBH = 100f;
        respBX = W / 2f - respBW / 2f;
        respBY = H * 0.30f - respBH / 2f;
        // tab MOTOR: 2 varian + 3 warna
        var0BW = 440f;
        var0BH = 100f;
        var0BX = W / 2f - 460f;
        var0BY = H / 2f + 60f;
        var1BW = 440f;
        var1BH = 100f;
        var1BX = W / 2f + 20f;
        var1BY = H / 2f + 60f;
        colBW = 300f;
        colBH = 100f;
        col0BX = W / 2f - 470f;
        col0BY = H / 2f - 80f;
        col1BX = W / 2f - 150f;
        col1BY = H / 2f - 80f;
        col2BX = W / 2f + 170f;
        col2BY = H / 2f - 80f;
        // tab PENGATURAN: 7 baris (musik, suara, auto-gas, setir, sensitivitas,
        // kalibrasi tilt, balik tilt) — panel 1000x980 di drawSettingsTab
        togBW = 500f;
        togBH = 90f;
        togMusBX = W / 2f - togBW / 2f;
        togMusBY = H / 2f + 230f;
        togSndBX = W / 2f - togBW / 2f;
        togSndBY = H / 2f + 130f;
        togGasBX = W / 2f - togBW / 2f;
        togGasBY = H / 2f + 30f;
        togSteerBX = W / 2f - togBW / 2f;
        togSteerBY = H / 2f - 70f;
        togSensBX = W / 2f - togBW / 2f;
        togSensBY = H / 2f - 170f;
        togCalBX = W / 2f - togBW / 2f;
        togCalBY = H / 2f - 270f;
        togInvBX = W / 2f - togBW / 2f;
        togInvBY = H / 2f - 370f;
        setBackBW = 360f;
        setBackBH = 80f;
        setBackBX = W / 2f - setBackBW / 2f;
        setBackBY = H / 2f - 466f;
    }

    @Override
    public void pause() {
        if (audio != null) {
            audio.musicPause();
            audio.engineStop();
            audio.windStop();
        }
    }

    @Override
    public void resume() {
        if (audio != null) {
            audio.musicPlay();
            audio.windStart();
            if (state != State.FINISHED) audio.engineStart();
        }
    }

    @Override
    public void hide() {
    }

    @Override
    public void dispose() {
        if (audio != null) audio.dispose();
        if (modelBatch != null) modelBatch.dispose();
        if (spriteBatch != null) spriteBatch.dispose();
        if (shapes != null) shapes.dispose();
        if (font != null) font.dispose();
        if (bigFont != null) bigFont.dispose();
        if (speedFont != null) speedFont.dispose();
        if (world != null) world.dispose();
        if (bike != null) bike.dispose();
        for (Bike t : traffic) {
            if (t != null) t.dispose();
        }
    }
}
