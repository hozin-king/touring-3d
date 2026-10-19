package com.hozinking.touring3d;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

import java.util.ArrayList;
import java.util.List;

/**
 * Motor + fisika arcade: gas/rem/belok, tabrakan lingkaran sederhana.
 * Model low-poly menghadap +Z, dibangun dari banyak part (satu ModelInstance
 * per part dengan offset lokal — tanpa node hierarchy agar simpel).
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

    private float leanVisual = 0f;

    private static class Part {
        Model model;
        ModelInstance instance;
        Matrix4 local = new Matrix4();
    }

    private final List<Part> parts = new ArrayList<Part>();
    private final Matrix4 bikeMatrix = new Matrix4();

    public void reset(float x0, float z0, float h0) {
        x = x0;
        z = z0;
        heading = h0;
        speed = 0f;
        impact = 0f;
        leanVisual = 0f;
    }

    public void update(float dt, float steer, boolean gas, boolean brake, List<GameWorld.Collider> colliders) {
        // Akselerasi progresif: jambakan kuat di awal, melandai menuju top speed.
        if (gas) speed += (16f * (1f - speed / MAX_SPEED * 0.72f)) * dt;
        if (brake) speed -= 30f * dt;
        // Hambatan udara + perlambatan alami.
        speed -= speed * 0.35f * dt;
        if (!gas) speed -= 2.0f * dt;
        speed = Math.max(0f, Math.min(MAX_SPEED, speed));

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
        // Batas pulau
        float r = (float) Math.sqrt(x * x + z * z);
        if (r > 165f) {
            x *= 165f / r;
            z *= 165f / r;
            impact = Math.max(impact, 0.35f);
            speed *= 0.7f;
        }
        impact = Math.max(0f, impact - dt * 1.8f);

        leanVisual += (steer * lowAuth - leanVisual) * Math.min(1f, 8f * dt);
    }

    public int speedKmh() {
        return (int) (speed * 3.6f);
    }

    /** 0..1 untuk FOV kick & audio. */
    public float speed01() {
        return Math.max(0f, Math.min(1f, speed / MAX_SPEED));
    }

    /** Bangun semua part motor. Dipanggil sekali di GL thread. */
    public void buildModel() {
        Color dark = new Color(0.15f, 0.15f, 0.16f, 1f);
        Color red = new Color(0.80f, 0.16f, 0.16f, 1f);
        Color redDark = new Color(0.55f, 0.10f, 0.10f, 1f);
        Color silver = new Color(0.75f, 0.75f, 0.78f, 1f);
        Color jacket = new Color(0.15f, 0.30f, 0.65f, 1f);
        Color skin = new Color(0.85f, 0.62f, 0.45f, 1f);
        Color helmet = new Color(0.95f, 0.75f, 0.15f, 1f);
        Color cream = new Color(1f, 0.95f, 0.7f, 1f);

        // roda: silinder (sumbu Y) diputar 90° agar sumbu jadi X
        addCylinderPart(1.1f, 0.35f, 1.1f, 10, dark, 0f, 0.55f, -0.95f, 90f);
        addCylinderPart(1.1f, 0.35f, 1.1f, 10, dark, 0f, 0.55f, 0.95f, 90f);
        // bodi
        addBoxPart(0.5f, 0.5f, 1.7f, red, 0f, 0.95f, 0.05f);
        addBoxPart(0.44f, 0.32f, 0.65f, redDark, 0f, 1.22f, 0.35f); // tangki
        addBoxPart(0.44f, 0.22f, 0.75f, dark, 0f, 1.18f, -0.55f); // jok
        // garpu depan + setang
        addBoxPart(0.14f, 1.5f, 0.14f, silver, 0f, 1.05f, 1.0f);
        addBoxPart(0.9f, 0.12f, 0.12f, dark, 0f, 1.78f, 1.02f);
        addBoxPart(0.3f, 0.25f, 0.1f, cream, 0f, 1.45f, 1.18f); // lampu
        // knalpot
        addBoxPart(0.18f, 0.18f, 1.2f, silver, 0.3f, 0.6f, -0.7f);
        // pengendara
        addBoxPart(0.5f, 0.5f, 0.5f, dark, 0f, 1.0f, -0.45f); // paha
        addBoxPart(0.52f, 0.7f, 0.42f, jacket, 0f, 1.62f, -0.25f); // badan
        addBoxPart(0.14f, 0.14f, 0.9f, jacket, -0.32f, 1.6f, 0.35f); // lengan kiri
        addBoxPart(0.14f, 0.14f, 0.9f, jacket, 0.32f, 1.6f, 0.35f); // lengan kanan
        addBoxPart(0.3f, 0.35f, 0.3f, skin, 0f, 1.5f, -0.1f); // kepala
        addCylinderPart(0.64f, 0.48f, 0.64f, 8, helmet, 0f, 1.96f, -0.1f, 0f); // helm
    }

    private Material mat(Color c) {
        return new Material(ColorAttribute.createDiffuse(c));
    }

    private void addBoxPart(float w, float h, float d, Color c, float x, float y, float z) {
        ModelBuilder mb = new ModelBuilder();
        Model model = mb.createBox(w, h, d, mat(c), Usage.Position | Usage.Normal);
        addPart(model, x, y, z, 0f);
    }

    /** Silinder; rotZ90=true bila sumbu harus menghadap X (roda). */
    private void addCylinderPart(float w, float h, float d, int div, Color c,
                                 float x, float y, float z, float rotZDeg) {
        ModelBuilder mb = new ModelBuilder();
        Model model = mb.createCylinder(w, h, d, div, mat(c), Usage.Position | Usage.Normal);
        addPart(model, x, y, z, rotZDeg);
    }

    private void addPart(Model model, float x, float y, float z, float rotZDeg) {
        Part p = new Part();
        p.model = model;
        p.instance = new ModelInstance(model);
        p.local.setToRotation(Vector3.Z, rotZDeg).trn(x, y, z);
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
        for (Part p : parts) {
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
    }
}
