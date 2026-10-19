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
 * Layar utama Touring 3D: state MENU -> COUNTDOWN -> RIDING -> FINISHED.
 * Kontrol: joystick kiri (belok), tombol GAS/REM kanan bawah,
 * drag area kanan (selain tombol) untuk orbit kamera.
 * Semua UI digambar manual (ShapeRenderer + BitmapFont), full multitouch.
 */
public class TouringScreen implements Screen, InputProcessor {

    private static final String VERSION_LABEL = "v2.0 (7)";

    private enum State {MENU, COUNTDOWN, RIDING, FINISHED}

    private final TouringGame game;
    private State state = State.MENU;

    private ModelBatch modelBatch;
    private SpriteBatch spriteBatch;
    private ShapeRenderer shapes;
    private BitmapFont font;
    private BitmapFont bigFont;
    private final GlyphLayout layout = new GlyphLayout();

    private PerspectiveCamera camera;
    private Environment environment;
    private GameWorld world;
    private Bike bike;
    private Preferences prefs;

    private final boolean[] visited = new boolean[3];
    private float rideTime, countdownT;
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
    private float camX, camZ;
    private float orbitA;

    private int W, H;
    // metrik UI (koordinat y-up, px)
    private float joyCX, joyCY, joyR;
    private float gasCX, gasCY, gasR, brakeCX, brakeCY, brakeR;
    private float tourBX, tourBY, tourBW, tourBH;
    private float trialBX, trialBY, trialBW, trialBH;

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

        prefs = Gdx.app.getPreferences("touring3d");
        Gdx.input.setInputProcessor(this);
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        showMenuBest();
    }

    // ------------------------------------------------------------------ loop

    @Override
    public void render(float delta) {
        float dt = Math.min(delta, 0.05f);
        update(dt);

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
        switch (state) {
            case MENU:
                orbitA += dt * 0.08f;
                camera.position.set(
                        (float) Math.cos(orbitA) * 170f, 70f,
                        (float) Math.sin(orbitA) * 170f);
                camera.lookAt(0f, 0f, 0f);
                camera.up.set(Vector3.Y);
                camera.update();
                break;
            case COUNTDOWN:
                countdownT += dt;
                String label = countdownT < 1f ? "3"
                        : countdownT < 2f ? "2"
                        : countdownT < 3f ? "1" : "GAS!";
                lastCountdown = label;
                updateCamera(dt);
                if (countdownT >= 3.4f) {
                    state = State.RIDING;
                    lastCountdown = "";
                }
                break;
            case RIDING:
                rideTime += dt;
                bike.update(dt, steer, gas, brake, world.colliders);
                checkSpots();
                if (visited[0] && visited[1] && visited[2]) finish();
                updateCamera(dt);
                break;
            case FINISHED:
                updateCamera(dt);
                break;
        }
    }

    private void updateCamera(float dt) {
        float fx = (float) Math.sin(bike.heading);
        float fz = (float) Math.cos(bike.heading);
        float tx = bike.x - fx * 8f;
        float tz = bike.z - fz * 8f;
        float k = Math.min(1f, 5f * dt);
        camX += (tx - camX) * k;
        camZ += (tz - camZ) * k;
        // orbit dari drag jari kanan
        float ox = camX - bike.x, oz = camZ - bike.z;
        float co = (float) Math.cos(orbitYaw), si = (float) Math.sin(orbitYaw);
        float rx = ox * co + oz * si;
        float rz = -ox * si + oz * co;
        camera.position.set(bike.x + rx, 3.6f + orbitPitch * 14f, bike.z + rz);
        camera.lookAt(bike.x + fx * 5f, 1.4f, bike.z + fz * 5f);
        camera.up.set(Vector3.Y);
        camera.update();
    }

    private void checkSpots() {
        for (int i = 0; i < 3; i++) {
            if (visited[i]) continue;
            GameWorld.Spot s = world.spots.get(i);
            float dx = bike.x - s.x, dz = bike.z - s.z;
            if (dx * dx + dz * dz < 81f) { // radius 9
                visited[i] = true;
                world.setRingVisited(i);
                toast(s.name + " dikunjungi! (" + countVisited() + "/3)");
            }
        }
    }

    private int countVisited() {
        int n = 0;
        for (boolean v : visited) if (v) n++;
        return n;
    }

    private void finish() {
        state = State.FINISHED;
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
        isTrial = trial;
        bike.reset(world.startX, world.startZ, world.startHeading);
        for (int i = 0; i < 3; i++) visited[i] = false;
        world.resetRings();
        float fx = (float) Math.sin(world.startHeading);
        float fz = (float) Math.cos(world.startHeading);
        camX = world.startX - fx * 8f;
        camZ = world.startZ - fz * 8f;
        rideTime = 0f;
        countdownT = 0f;
        lastCountdown = "";
        orbitYaw = 0f;
        orbitPitch = 0f;
        clearInputs();
        state = State.COUNTDOWN;
    }

    public void backToMenu() {
        clearInputs();
        state = State.MENU;
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
        shapes.setColor(0.78f, 0.16f, 0.16f, 1f);
        shapes.circle(brakeCX, brakeCY, brakeR);
        shapes.setColor(0.18f, 0.49f, 0.20f, 1f);
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
        font.draw(spriteBatch, bike.speedKmh() + " km/h", 24f, H - 30f);
        String cp = "CP " + countVisited() + "/3";
        layout.setText(font, cp);
        font.draw(spriteBatch, cp, W - layout.width - 24f, H - 30f);
        String t = fmtTime(rideTime);
        layout.setText(font, t);
        font.draw(spriteBatch, t, W / 2f - layout.width / 2f, H - 30f);
        spriteBatch.end();
    }

    private void drawMenu() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.18f, 0.49f, 0.20f, 1f);
        shapes.rect(tourBX, tourBY, tourBW, tourBH);
        shapes.setColor(0.13f, 0.35f, 0.65f, 1f);
        shapes.rect(trialBX, trialBY, trialBW, trialBH);
        shapes.end();

        spriteBatch.begin();
        layout.setText(bigFont, "TOURING 3D");
        bigFont.draw(spriteBatch, layout, W / 2f - layout.width / 2f, H * 0.74f);
        if (!menuBest.isEmpty()) {
            layout.setText(font, menuBest);
            font.draw(spriteBatch, layout, W / 2f - layout.width / 2f, H * 0.60f);
        }
        layout.setText(font, "MULAI TOUR");
        font.draw(spriteBatch, layout, W / 2f - layout.width / 2f, tourBY + tourBH / 2f + layout.height / 2f);
        layout.setText(font, "TIME TRIAL");
        font.draw(spriteBatch, layout, W / 2f - layout.width / 2f, trialBY + trialBH / 2f + layout.height / 2f);
        layout.setText(font, VERSION_LABEL);
        font.draw(spriteBatch, layout, W - layout.width - 16f, 40f);
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
            if (inRect(x, y, tourBX, tourBY, tourBW, tourBH)) startGame(false);
            else if (inRect(x, y, trialBX, trialBY, trialBW, trialBH)) startGame(true);
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
        tourBY = H * 0.42f - tourBH / 2f;
        trialBW = 440f;
        trialBH = 110f;
        trialBX = W / 2f - trialBW / 2f;
        trialBY = H * 0.27f - trialBH / 2f;
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void hide() {
    }

    @Override
    public void dispose() {
        if (modelBatch != null) modelBatch.dispose();
        if (spriteBatch != null) spriteBatch.dispose();
        if (shapes != null) shapes.dispose();
        if (font != null) font.dispose();
        if (bigFont != null) bigFont.dispose();
        if (world != null) world.dispose();
        if (bike != null) bike.dispose();
    }
}
