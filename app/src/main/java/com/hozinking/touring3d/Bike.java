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
 */
public class Bike {
    public float x = 0f;
    public float z = 0f;
    public float heading = 0f;
    public float speed = 0f;

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
        leanVisual = 0f;
    }

    public void update(float dt, float steer, boolean gas, boolean brake, List<GameWorld.Collider> colliders) {
        final float maxSpeed = 30f; // ~108 km/h
        if (gas) speed += 9.5f * dt;
        if (brake) speed -= 22f * dt;
        // hambatan + perlambatan alami
        speed -= speed * 0.45f * dt;
        if (!gas) speed -= 1.2f * dt;
        speed = Math.max(0f, Math.min(maxSpeed, speed));

        float authority = Math.max(0f, Math.min(1f, speed / 8f));
        heading += steer * 2.1f * authority * dt;

        x += (float) Math.sin(heading) * speed * dt;
        z += (float) Math.cos(heading) * speed * dt;

        // tabrakan: dorong keluar lingkaran collider
        for (GameWorld.Collider c : colliders) {
            float dx = x - c.x;
            float dz = z - c.z;
            float d = (float) Math.sqrt(dx * dx + dz * dz);
            float minD = c.r + 1f;
            if (d < minD && d > 0.0001f) {
                x = c.x + dx / d * minD;
                z = c.z + dz / d * minD;
                speed *= 0.55f;
            }
        }
        // batas pulau
        float r = (float) Math.sqrt(x * x + z * z);
        if (r > 165f) {
            x *= 165f / r;
            z *= 165f / r;
            speed *= 0.7f;
        }

        leanVisual += (steer * authority - leanVisual) * Math.min(1f, 8f * dt);
    }

    public int speedKmh() {
        return (int) (speed * 3.6f);
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

    public void render(ModelBatch batch, Environment env) {
        Matrix4 m = computeMatrix();
        for (Part p : parts) {
            p.instance.transform.set(m).mul(p.local);
            batch.render(p.instance, env);
        }
    }

    public void dispose() {
        for (Part p : parts) p.model.dispose();
        parts.clear();
    }
}
