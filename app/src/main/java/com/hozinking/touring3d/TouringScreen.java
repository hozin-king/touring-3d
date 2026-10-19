package com.hozinking.touring3d;

import com.badlogic.gdx.Gdx;
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
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

/**
 * Layar utama Touring 3D v2.1: MENU -> COUNTDOWN -> RIDING -> FINISHED.
 * Kontrol: joystick kiri (belok), tombol GAS/REM kanan bawah (multitouch),
 * drag area kanan (selain tombol) untuk orbit kamera.
 * Checkpoint BERURUTAN: POM -> WARUNG -> VIEW POINT.
 * Semua UI digambar manual (ShapeRenderer + BitmapFont).
 * Catatan: BitmapFont bawaan tak punya glyph emoji -> teks polos saja.
 */
public class TouringScreen implements Screen, InputProcessor {

    private static final String VERSION_LABEL = "v2.1 (8)";

    private enum State {MENU, COUNTDOWN, RIDING, FINISHED}

    private enum MenuTab {MAIN, HELP, CREDITS}

    private final TouringGame game;
    private State state = State.MENU;
    private MenuTab menuTab = MenuTab.MAIN;

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

    private final boolean[] visited = new boolean[3];
    private int nextCp = 0;
    private float rideTime, countdownT, timeAcc;
    private String lastCountdown = "";
    private boolean isTrial;
    private String menuBest = "";
    private String finishTime = "", finishBest = "";
    private boolean finishIsBest;
    private String toastMsg = "";
    private float toastT;

    // kontrol
    private float steer;
    private boolean gas, brake;
    private int joyPointer = -1, lookPointer = -1, gasPointer = -1, brakePointer = -1;
    private float joyBaseX, joyDX;
    private float orbitYaw, orbitPitch, lookLastX, lookLastY;
    private float camX, camY, camZ;
    private float orbitA;

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
    private float helpBX, helpBY, helpBW, helpBH;
    private float credBX, credBY, credBW, credBH;
    private float backBX, backBY, backBW, backBH;

    private static final String[] HELP_LINES = {
            "Joystick kiri: belok",
            "Tombol GAS (hijau) / REM (merah) - bisa bareng belok",
            "Geser area kanan layar: putar kamera",
            "Kunjungi POM -> WARUNG -> VIEW POINT berurutan,",
            "secepat mungkin! Ring target berdenyut kuning.",
            "Tabrakan bikin melambat. Hati-hati!"
    };

    private static final String[] CREDIT_LINES = {
            "SUARA: Mixkit (mixkit.co) - Mixkit License",
            "- Mesin: Motorcycle engine working",
            "- Klik: Classic click",
            "- Tabrakan: Factory metal hard hit",
            "- Hitung mundur: Positive interface beep",
            "- Checkpoint: Game success alert",
            "MUSIK: Carefree - Kevin MacLeod",
            "incompetech.com (CC BY 4.0)",
            "TEKSTUR: ambientcg.com (CC0)",
            "- Asphalt007, Grass005"
    };

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
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.55f, 0.55f, 0.55f, 1f));
        environment.add(new DirectionalLight().set(Color.WHITE, new Vector3(-0.4f, -1f, -0.25f)));

        world = new GameWorld();
        world.build();
        bike = new Bike();
        bike.buildModel();
        bike.reset(world.startX, world.startZ, world.startHeading);

        audio = new AudioMan();
        audio.load();
        audio.musicPlay();
        audio.engineStart(); // idle di menu

        prefs = Gdx.app.getPreferences("touring3d");
        Gdx.input.setInputProcessor(this);
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
        Gdx.gl.glClearColor(0.52f, 0.80f, 0.96f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        modelBatch.begin(camera);
        world.render(modelBatch, environment);
        bike.render(modelBatch, environment);
        modelBatch.end();

        drawUI();
    }

    private void update(float dt) {
        if (toastT > 0) toastT -= dt;
        if (crashCd > 0) crashCd -= dt;
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
                updateCamera(dt);
                if (countdownT >= 3.4f) {
                    state = State.RIDING;
                    lastCountdown = "";
                }
                break;
            case RIDING:
                rideTime += dt;
                bike.update(dt, steer, gas, brake, world.colliders);
                // Feedback tabrakan: suara + getar + camera shake.
                if (bike.impact > 0.25f && crashCd <= 0f) {
                    audio.crash(dt);
                    Gdx.input.vibrate(70);
                    shake = Math.min(1f, bike.impact);
                    crashCd = 0.5f;
                }
                audio.engineUpdate(bike.speed01(), gas);
                audio.musicRide(true);
                checkSpots();
                if (nextCp >= 3) finish();
                updateCamera(dt);
                break;
            case FINISHED:
                audio.engineUpdate(0f, false);
                audio.musicRide(false);
                updateCamera(dt);
                break;
        }
    }

    private void updateCamera(float dt) {
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
        camera.up.set(Vector3.Y);
        // FOV melebar halus seiring kecepatan
        float fovT = 45f + s01 * 17f;
        fov += (fovT - fov) * Math.min(1f, 2.8f * dt);
        camera.fieldOfView = fov;
        camera.update();
    }

    /** Checkpoint BERURUTAN: hanya target berikut yang bisa dikunjungi. */
    private void checkSpots() {
        if (nextCp >= 3) return;
        GameWorld.Spot s = world.spots.get(nextCp);
        float dx = bike.x - s.x, dz = bike.z - s.z;
        if (dx * dx + dz * dz < 81f) { // radius 9
            visited[nextCp] = true;
            world.setRingVisited(nextCp);
            audio.checkpoint();
            toast(s.name + " dikunjungi! (" + (nextCp + 1) + "/3)");
            nextCp++;
            world.setTargetRing(nextCp < 3 ? nextCp : -1);
        }
    }

    private void finish() {
        state = State.FINISHED;
        audio.checkpoint();
        String key = isTrial ? "best_trial" : "best_tour";
        float prev = prefs.getFloat(key, -1f);
        finishIsBest = prev < 0 || rideTime < prev;
        if (finishIsBest) {
            prefs.putFloat(key, rideTime);
            prefs.flush();
        }
        finishTime = fmtTime(rideTime);
        finishBest = fmtTime(finishIsBest ? rideTime : prev);
        clearInputs();
    }

    private void toast(String msg) {
        toastMsg = msg;
        toastT = 2.5f;
    }

    // ------------------------------------------------------------------ API

    public void startGame(boolean trial) {
        audio.click();
        isTrial = trial;
        bike.reset(world.startX, world.startZ, world.startHeading);
        for (int i = 0; i < 3; i++) visited[i] = false;
        nextCp = 0;
        world.resetRings();
        world.setTargetRing(0);
        float fx = (float) Math.sin(world.startHeading);
        float fz = (float) Math.cos(world.startHeading);
        camX = world.startX - fx * 8f;
        camZ = world.startZ - fz * 8f;
        camY = 3.6f;
        fov = 45f;
        shake = 0f;
        rideTime = 0f;
        countdownT = 0f;
        lastCountdown = "";
        orbitYaw = 0f;
        orbitPitch = 0f;
        clearInputs();
        state = State.COUNTDOWN;
    }

    public void backToMenu() {
        audio.click();
        clearInputs();
        state = State.MENU;
        menuTab = MenuTab.MAIN;
        showMenuBest();
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
        if (toastT > 0 && !toastMsg.isEmpty()) {
            spriteBatch.begin();
            layout.setText(font, toastMsg);
            font.draw(spriteBatch, layout, W / 2f - layout.width / 2f, H - 120f);
            spriteBatch.end();
        }
    }

    private void drawControls() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        // joystick kiri
        shapes.setColor(0.25f, 0.25f, 0.25f, 1f);
        shapes.circle(joyCX, joyCY, joyR);
        shapes.setColor(0.7f, 0.7f, 0.7f, 1f);
        shapes.circle(joyCX + joyDX * joyR * 0.55f, joyCY, 45f);
        // tombol REM & GAS kanan bawah
        shapes.setColor(gas ? 0.95f : 0.78f, 0.16f, 0.16f, 1f);
        shapes.circle(brakeCX, brakeCY, brakeR);
        shapes.setColor(0.18f, brake ? 0.75f : 0.49f, 0.20f, 1f);
        shapes.circle(gasCX, gasCY, gasR);
        shapes.end();

        spriteBatch.begin();
        layout.setText(font, "REM");
        font.draw(spriteBatch, layout, brakeCX - layout.width / 2f, brakeCY + layout.height / 2f);
        layout.setText(font, "GAS");
        font.draw(spriteBatch, layout, gasCX - layout.width / 2f, gasCY + layout.height / 2f);
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
        // target checkpoint di bawah timer
        if (nextCp < 3) {
            String tg = "TARGET: " + world.spots.get(nextCp).name + " (" + (nextCp + 1) + "/3)";
            layout.setText(font, tg);
            font.draw(spriteBatch, tg, W / 2f - layout.width / 2f, H - 80f);
        } else {
            layout.setText(font, "FINISH!");
            font.draw(spriteBatch, layout, W / 2f - layout.width / 2f, H - 80f);
        }
        spriteBatch.end();
    }

    /** Minimap lingkaran: jalan, checkpoint, posisi + arah motor. */
    private void drawMinimap() {
        float mx = W - 135f, my = H - 185f, mr = 105f;
        float sc = mr / 175f; // dunia +-175 -> radius peta
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
        for (int i = 0; i < 3; i++) {
            GameWorld.Spot s = world.spots.get(i);
            float sx = mx + s.x * sc, sy = my + s.z * sc;
            if (visited[i]) shapes.setColor(0.2f, 0.85f, 0.3f, 1f);
            else if (i == nextCp) {
                float p = 0.65f + 0.35f * (float) Math.sin(timeAcc * 5.2f);
                shapes.setColor(1f, 0.72f * p + 0.28f, 0.15f, 1f);
            } else shapes.setColor(0.45f, 0.45f, 0.48f, 1f);
            shapes.circle(sx, sy, 9f);
        }
        // start putih
        shapes.setColor(0.95f, 0.95f, 0.95f, 1f);
        shapes.circle(mx + world.startX * sc, my + world.startZ * sc, 6f);
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
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.18f, 0.49f, 0.20f, 1f);
        shapes.rect(tourBX, tourBY, tourBW, tourBH);
        shapes.setColor(0.13f, 0.35f, 0.65f, 1f);
        shapes.rect(trialBX, trialBY, trialBW, trialBH);
        shapes.setColor(0.30f, 0.30f, 0.34f, 1f);
        shapes.rect(helpBX, helpBY, helpBW, helpBH);
        shapes.rect(credBX, credBY, credBW, credBH);
        shapes.end();

        spriteBatch.begin();
        layout.setText(bigFont, "TOURING 3D");
        bigFont.draw(spriteBatch, layout, W / 2f - layout.width / 2f, H * 0.80f);
        layout.setText(font, "Touring motor keliling pulau - full offline");
        font.draw(spriteBatch, layout, W / 2f - layout.width / 2f, H * 0.70f);
        if (!menuBest.isEmpty()) {
            layout.setText(font, menuBest);
            font.draw(spriteBatch, layout, W / 2f - layout.width / 2f, H * 0.63f);
        }
        drawButtonLabel("MULAI TOUR", tourBX, tourBY, tourBW, tourBH);
        drawButtonLabel("TIME TRIAL", trialBX, trialBY, trialBW, trialBH);
        drawButtonLabel("CARA MAIN", helpBX, helpBY, helpBW, helpBH);
        drawButtonLabel("KREDIT", credBX, credBY, credBW, credBH);
        layout.setText(font, VERSION_LABEL);
        font.draw(spriteBatch, layout, W - layout.width - 16f, 40f);
        spriteBatch.end();
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

    private void drawFinish() {
        float pw = 620f, ph = 340f;
        float px = W / 2f - pw / 2f, py = H / 2f - ph / 2f;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.05f, 0.05f, 0.08f, 1f);
        shapes.rect(px, py, pw, ph);
        shapes.end();

        spriteBatch.begin();
        layout.setText(bigFont, "SELESAI!");
        bigFont.draw(spriteBatch, layout, W / 2f - layout.width / 2f, py + ph - 60f);
        layout.setText(font, "Waktu: " + finishTime);
        font.draw(spriteBatch, layout, W / 2f - layout.width / 2f, py + ph - 160f);
        layout.setText(font, finishIsBest ? "Rekor baru! Tercepat: " + finishBest : "Tercepat: " + finishBest);
        font.draw(spriteBatch, layout, W / 2f - layout.width / 2f, py + ph - 220f);
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
            if (menuTab != MenuTab.MAIN) {
                if (inRect(x, y, backBX, backBY, backBW, backBH)) {
                    menuTab = MenuTab.MAIN;
                    audio.click();
                }
                return true;
            }
            if (inRect(x, y, tourBX, tourBY, tourBW, tourBH)) startGame(false);
            else if (inRect(x, y, trialBX, trialBY, trialBW, trialBH)) startGame(true);
            else if (inRect(x, y, helpBX, helpBY, helpBW, helpBH)) {
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
        if (state != State.RIDING) return false;
        if (dist(x, y, gasCX, gasCY) < gasR && gasPointer == -1) {
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
            joyDX = clamp((x - joyBaseX) / joyR, -1f, 1f);
            steer = joyDX;
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
            steer = 0f;
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
        return false;
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
        tourBW = 440f;
        tourBH = 110f;
        tourBX = W / 2f - tourBW / 2f;
        tourBY = H * 0.44f - tourBH / 2f;
        trialBW = 440f;
        trialBH = 110f;
        trialBX = W / 2f - trialBW / 2f;
        trialBY = H * 0.29f - trialBH / 2f;
        helpBW = 300f;
        helpBH = 80f;
        helpBX = W / 2f - helpBW - 15f;
        helpBY = H * 0.13f - helpBH / 2f;
        credBW = 300f;
        credBH = 80f;
        credBX = W / 2f + 15f;
        credBY = H * 0.13f - credBH / 2f;
        backBW = 360f;
        backBH = 90f;
        backBX = W / 2f - backBW / 2f;
        backBY = H / 2f - 330f;
    }

    @Override
    public void pause() {
        if (audio != null) {
            audio.musicPause();
            audio.engineStop();
        }
    }

    @Override
    public void resume() {
        if (audio != null) {
            audio.musicPlay();
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
    }
}
