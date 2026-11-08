package com.hozinking.touring3d;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

import java.util.ArrayList;
import java.util.List;

/**
 * Motor + fisika arcade: gas/rem/belok, tabrakan lingkaran sederhana.
 * Model low-poly menghadap +Z, dibangun dari banyak part (satu ModelInstance
 * per part dengan offset lokal — tanpa node hierarchy agar simpel).
 * v2.3: bodi bertekstur metal (warna bisa dipilih), jok kain, roda berputar,
 * lampu depan + sorot cahaya, 2 varian (0=bebek, 1=trail).
 *
 * Tuning arcade (v2.1): akselerasi progresif (kencang di awal, melandai di
 * top speed), rem kuat, belok sensitif di pelan & stabil di kencang.
 */
public class Bike {
    public float x = 0f;
    public float z = 0f;
    public float heading = 0f;
    public float speed = 0f;
    /** 0..1 — kekuatan tabrakan terakhir, meluruh sendiri. Untuk shake+suara. */
    public float impact = 0f;

    public static final float MAX_SPEED = 33f; // ~119 km/h

    /** Batas kecepatan saat ini (bensin habis -> 2 m/s, dorong pelan). */
    public float speedCap = MAX_SPEED;
    /** Warna bodi (di-set sebelum buildModel). Default merah. */
    public Color bodyColor = new Color(0.80f, 0.16f, 0.16f, 1f);
    /** Varian: 0 = bebek (ala Beat), 1 = trail (roda besar, bodi tinggi). */
    public int variant = 0;

    private float leanVisual = 0f;
    private float wheelSpin = 0f;

    private static class Part {
        Model model;
        ModelInstance instance;
        Matrix4 local = new Matrix4();
        /** 0 = statis, 1 = roda (silinder), 2 = jari-jari roda. */
        int kind;
        float px, py, pz;
    }

    private final List<Part> parts = new ArrayList<Part>();
    private final Matrix4 bikeMatrix = new Matrix4();
    private final List<Texture> textures = new ArrayList<Texture>();

    public void reset(float x0, float z0, float h0) {
        x = x0;
        z = z0;
        heading = h0;
        speed = 0f;
        impact = 0f;
        leanVisual = 0f;
        wheelSpin = 0f;
    }

    public void update(float dt, float steer, boolean gas, boolean brake, List<GameWorld.Collider> colliders) {
        // Akselerasi progresif: jambakan kuat di awal, melandai menuju top speed.
        if (gas) speed += (16f * (1f - speed / MAX_SPEED * 0.72f)) * dt;
        // fix v2.2: rem diperkuat; tahan rem saat (hampir) berhenti -> mundur (maks 8 m/s)
        if (brake) speed -= 34f * dt;
        // Hambatan udara: selalu mengarah ke 0 (berlaku juga saat mundur).
        speed -= speed * 0.35f * dt;
        // Perlambatan alami hanya saat maju & gas lepas.
        if (!gas && speed > 0f) speed -= 2.0f * dt;
        speed = Math.max(-8f, Math.min(speedCap, speed));
        // Rem dilepas saat mundur -> merayap kembali ke 0.
        if (speed < 0f && !brake) speed = Math.min(0f, speed + 12f * dt);

        // Belok: otoritas penuh di pelan, diredam di kencang agar stabil.
        float lowAuth = Math.max(0f, Math.min(1f, speed / 6f));
        float highDamp = 1f - 0.5f * (speed / MAX_SPEED);
        heading += steer * 2.7f * lowAuth * highDamp * dt;

        x += (float) Math.sin(heading) * speed * dt;
        z += (float) Math.cos(heading) * speed * dt;

        // Tabrakan: dorong keluar lingkaran collider, catat impact.
        for (GameWorld.Collider c : colliders) {
            float dx = x - c.x;
            float dz = z - c.z;
            float d = (float) Math.sqrt(dx * dx + dz * dz);
            float minD = c.r + 1f;
            if (d < minD && d > 0.0001f) {
                x = c.x + dx / d * minD;
                z = c.z + dz / d * minD;
                impact = Math.max(impact, Math.max(0f, Math.min(1f, speed / MAX_SPEED)));
                speed *= 0.45f;
            }
        }
        // Batas pulau (pasir sampai r=210)
        float r = (float) Math.sqrt(x * x + z * z);
        if (r > 200f) {
            x *= 200f / r;
            z *= 200f / r;
            impact = Math.max(impact, 0.35f);
            speed *= 0.7f;
        }
        impact = Math.max(0f, impact - dt * 1.8f);

        leanVisual += (steer * lowAuth - leanVisual) * Math.min(1f, 8f * dt);
        // Roda berputar seiring jarak tempuh
        wheelSpin += speed * dt / 0.55f;
    }

    /**
     * Jelajah otomatis (motor lain di jalan): kecepatan tetap, heading dijaga
     * mengikuti tangent lingkaran jalan (r=94). Dipakai untuk lalu lintas.
     */
    public void cruise(float dt, float targetSpeed, List<GameWorld.Collider> colliders) {
        float a = (float) Math.atan2(z, x);
        heading = -a; // tangent lingkaran (lawan arah jarum jam)
        speed = targetSpeed;
        x += (float) Math.sin(heading) * speed * dt;
        z += (float) Math.cos(heading) * speed * dt;
        // Jaga di jalan ring bila terdorong keluar
        float r = (float) Math.sqrt(x * x + z * z);
        if (r > 104f || r < 84f) {
            x *= 94f / r;
            z *= 94f / r;
        }
        wheelSpin += speed * dt / 0.55f;
    }

    /** Lean visual saat ini (-1..1), untuk roll kamera (v2.4). */
    public float lean() {
        return leanVisual;
    }

    public int speedKmh() {
        // fix v2.2: speedometer tidak menunjukkan angka negatif saat mundur
        return Math.max(0, (int) (speed * 3.6f));
    }

    /** 0..1 untuk FOV kick & audio. */
    public float speed01() {
        return Math.max(0f, Math.min(1f, speed / MAX_SPEED));
    }

    /** Bangun semua part motor. Dipanggil sekali di GL thread. */
    public void buildModel() {
        Texture texMetal = loadTex("tex/metal.jpg");
        Texture texFabric = loadTex("tex/fabric.jpg");

        Color dark = new Color(0.15f, 0.15f, 0.16f, 1f);
        Color bodyDark = new Color(bodyColor.r * 0.65f, bodyColor.g * 0.65f, bodyColor.b * 0.65f, 1f);
        Color silver = new Color(0.75f, 0.75f, 0.78f, 1f);
        Color jacket = new Color(0.15f, 0.30f, 0.65f, 1f);
        Color skin = new Color(0.85f, 0.62f, 0.45f, 1f);
        Color helmet = new Color(0.95f, 0.75f, 0.15f, 1f);
        Color cream = new Color(1f, 0.95f, 0.7f, 1f);

        // v2.3: trail = roda lebih besar + bodi lebih tinggi
        float wheelR = (variant == 1) ? 1.25f : 1.1f;
        float lift = (variant == 1) ? 0.25f : 0f;
        float wheelY = wheelR * 0.5f + lift;

        // roda: ban gelap + pelek metal (berputar saat jalan)
        addWheelPart(wheelR, 0.35f, dark, 0f, wheelY, -0.95f);
        addWheelPart(wheelR, 0.35f, dark, 0f, wheelY, 0.95f);
        addWheelPart(wheelR * 0.55f, 0.38f, silver, 0f, wheelY, -0.95f);
        addWheelPart(wheelR * 0.55f, 0.38f, silver, 0f, wheelY, 0.95f);
        // jari-jari (batang terang) agar putaran roda kelihatan
        addSpokePart(0.14f, wheelR * 0.9f, 0.42f, silver, 0f, wheelY, -0.95f);
        addSpokePart(0.14f, wheelR * 0.9f, 0.42f, silver, 0f, wheelY, 0.95f);
        // bodi bertekstur metal (warna pilihan)
        addBoxPartTex(0.5f, 0.5f, 1.7f, texMetal, bodyColor, 0f, 0.95f + lift, 0.05f);
        addBoxPartTex(0.44f, 0.32f, 0.65f, texMetal, bodyDark, 0f, 1.22f + lift, 0.35f); // tangki
        addBoxPartTex(0.44f, 0.22f, 0.75f, texFabric, dark, 0f, 1.18f + lift, -0.55f); // jok kain
        // garpu depan + setang
        addBoxPart(0.14f, 1.5f, 0.14f, silver, 0f, 1.05f + lift, 1.0f);
        addBoxPart((variant == 1) ? 1.1f : 0.9f, 0.12f, 0.12f, dark, 0f, 1.78f + lift, 1.02f);
        addBoxPart(0.3f, 0.25f, 0.1f, cream, 0f, 1.45f + lift, 1.18f); // lampu
        // sorot cahaya lampu: cone tembus pandang ke depan
        addLightCone(0f, 1.35f + lift, 2.1f);
        // knalpot
        addBoxPart(0.18f, 0.18f, 1.2f, silver, 0.3f, 0.6f + lift, -0.7f);
        // pengendara
        addBoxPart(0.5f, 0.5f, 0.5f, dark, 0f, 1.0f + lift, -0.45f); // paha
        addBoxPart(0.52f, 0.7f, 0.42f, jacket, 0f, 1.62f + lift, -0.25f); // badan
        addBoxPart(0.14f, 0.14f, 0.9f, jacket, -0.32f, 1.6f + lift, 0.35f); // lengan kiri
        addBoxPart(0.14f, 0.14f, 0.9f, jacket, 0.32f, 1.6f + lift, 0.35f); // lengan kanan
        addBoxPart(0.3f, 0.35f, 0.3f, skin, 0f, 1.5f + lift, -0.1f); // kepala
        addCylinderPart(0.64f, 0.48f, 0.64f, 8, helmet, 0f, 1.96f + lift, -0.1f, 0f); // helm
    }

    private Texture loadTex(String path) {
        Texture t = new Texture(Gdx.files.internal(path), true);
        t.setWrap(Texture.TextureWrap.MirroredRepeat, Texture.TextureWrap.MirroredRepeat);
        t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        textures.add(t);
        return t;
    }

    private Material mat(Color c) {
        return new Material(ColorAttribute.createDiffuse(c));
    }

    private Material matTex(Texture tex, Color tint) {
        return new Material(TextureAttribute.createDiffuse(tex),
                ColorAttribute.createDiffuse(tint));
    }

    private void addBoxPart(float w, float h, float d, Color c, float x, float y, float z) {
        ModelBuilder mb = new ModelBuilder();
        Model model = mb.createBox(w, h, d, mat(c), Usage.Position | Usage.Normal);
        addPart(model, x, y, z, 0f, 0);
    }

    private void addBoxPartTex(float w, float h, float d, Texture tex, Color tint,
                               float x, float y, float z) {
        ModelBuilder mb = new ModelBuilder();
        Model model = mb.createBox(w, h, d, matTex(tex, tint),
                Usage.Position | Usage.Normal | Usage.TextureCoordinates);
        addPart(model, x, y, z, 0f, 0);
    }

    /** Silinder; rotZ90=true bila sumbu harus menghadap X (roda). */
    private void addCylinderPart(float w, float h, float d, int div, Color c,
                                 float x, float y, float z, float rotZDeg) {
        ModelBuilder mb = new ModelBuilder();
        Model model = mb.createCylinder(w, h, d, div, mat(c), Usage.Position | Usage.Normal);
        addPart(model, x, y, z, rotZDeg, 0);
    }

    /** Roda: silinder sumbu Y diputar 90 derajat agar sumbu jadi X. */
    private void addWheelPart(float dia, float thick, Color c, float x, float y, float z) {
        ModelBuilder mb = new ModelBuilder();
        Model model = mb.createCylinder(dia, thick, dia, 10, mat(c), Usage.Position | Usage.Normal);
        addPart(model, x, y, z, 90f, 1);
    }

    /** Jari-jari roda: batang tipis yang ikut berputar (putaran jadi kelihatan). */
    private void addSpokePart(float w, float h, float d, Color c, float x, float y, float z) {
        ModelBuilder mb = new ModelBuilder();
        Model model = mb.createBox(w, h, d, mat(c), Usage.Position | Usage.Normal);
        addPart(model, x, y, z, 0f, 2);
    }

    /** Cone cahaya lampu depan (tembus pandang, kuning redup). */
    private void addLightCone(float x, float y, float z) {
        ModelBuilder mb = new ModelBuilder();
        Material m = new Material(
                ColorAttribute.createDiffuse(1f, 0.9f, 0.5f, 1f),
                new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.16f));
        // createCone: puncak di +Y. Putar -90 derajat di X -> puncak di lampu
        // (belakang), melebar ke depan (+Z).
        Model model = mb.createCone(1.6f, 2.2f, 1.6f, 8, m, Usage.Position | Usage.Normal);
        Part p = new Part();
        p.model = model;
        p.instance = new ModelInstance(model);
        p.local.setToTranslation(x, y, z).rotate(Vector3.X, -90f);
        p.px = x;
        p.py = y;
        p.pz = z;
        p.kind = 0;
        parts.add(p);
    }

    private void addPart(Model model, float x, float y, float z, float rotZDeg, int kind) {
        Part p = new Part();
        p.model = model;
        p.instance = new ModelInstance(model);
        // v2.3 fix: translasi dulu, baru rotasi orientasi (sebelumnya Rz*T
        // menggeser posisi roda ke samping).
        p.local.setToTranslation(x, y, z).rotate(Vector3.Z, rotZDeg);
        p.px = x;
        p.py = y;
        p.pz = z;
        p.kind = kind;
        parts.add(p);
    }

    /** Matriks motor: translasi * rotasi Y (heading) * guling (lean). */
    private Matrix4 computeMatrix() {
        bikeMatrix.idt();
        bikeMatrix.translate(x, 0f, z);
        bikeMatrix.rotate(Vector3.Y, heading * 57.29578f);
        bikeMatrix.rotate(Vector3.Z, -leanVisual * 22f);
        return bikeMatrix;
    }

    /** Set transform tiap part dari posisi/heading motor (dipakai render & shadow). */
    public void applyTransforms() {
        Matrix4 m = computeMatrix();
        float spinDeg = (float) Math.toDegrees(wheelSpin);
        for (Part p : parts) {
            if (p.kind == 1) {
                // roda: Rz dulu (sumbu Y -> X), baru Rx (putar mengelilingi as X)
                p.local.setToTranslation(p.px, p.py, p.pz)
                        .rotate(Vector3.X, spinDeg)
                        .rotate(Vector3.Z, 90f);
            } else if (p.kind == 2) {
                // jari-jari: putar mengelilingi as X
                p.local.setToTranslation(p.px, p.py, p.pz)
                        .rotate(Vector3.X, spinDeg);
            }
            p.instance.transform.set(m).mul(p.local);
        }
    }

    /** Instance part motor untuk shadow pass (panggil applyTransforms dulu). */
    public List<ModelInstance> getInstances() {
        List<ModelInstance> out = new ArrayList<ModelInstance>(parts.size());
        for (Part p : parts) out.add(p.instance);
        return out;
    }

    public void render(ModelBatch batch, Environment env) {
        applyTransforms();
        for (Part p : parts) {
            batch.render(p.instance, env);
        }
    }

    public void dispose() {
        for (Part p : parts) p.model.dispose();
        parts.clear();
        for (Texture t : textures) t.dispose();
        textures.clear();
    }
}
