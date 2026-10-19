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
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Pulau mini prosedural: laut, pantai, jalan melingkar, kampung,
 * bukit, pohon, 3 spot (POM, WARUNG, VIEW POINT), papan tanda, ring checkpoint.
 * Semua statis kecuali warna ring.
 */
public class GameWorld {

    public static class Collider {
        public float x, z, r;

        Collider(float x, float z, float r) {
            this.x = x;
            this.z = z;
            this.r = r;
        }
    }

    public static class Spot {
        public String name;
        public float x, z;

        Spot(String name, float x, float z) {
            this.name = name;
            this.x = x;
            this.z = z;
        }
    }

    public final List<Spot> spots = new ArrayList<Spot>();
    public final List<Collider> colliders = new ArrayList<Collider>();
    public final float startX = -94f;
    public final float startZ = 0f;
    /** Arah hadap awal: tangent jalan di titik start. */
    public final float startHeading = (float) Math.PI;

    private final List<Model> models = new ArrayList<Model>();
    private final List<Texture> signTextures = new ArrayList<Texture>();
    private final List<ModelInstance> statics = new ArrayList<ModelInstance>();
    private final ModelInstance[] rings = new ModelInstance[4]; // 3 spot + start
    private final Material[] ringMats = new Material[4];
    private final ModelInstance[] ringPosts = new ModelInstance[4];

    // Tekstur CC0 (ambientcg.com): aspal jalan + rumput.
    private Texture texRoad;
    private Texture texGrass;
    // Material laut (dianimasikan) + awan (bergerak).
    private Material seaMat;
    private final List<Cloud> clouds = new ArrayList<Cloud>();
    // Ring checkpoint yang jadi target berikut (berdenyut); -1 = tak ada.
    private int targetRing = 0;

    private static class Cloud {
        ModelInstance a, b;
        float ax, ay, az, bx, by, bz, speed;

        void apply() {
            a.transform.setToTranslation(ax, ay, az);
            b.transform.setToTranslation(bx, by, bz);
        }
    }

    private final Color ringAmber = new Color(1f, 0.72f, 0.15f, 1f);
    private final Color ringWhite = new Color(0.95f, 0.95f, 0.95f, 1f);
    private final Color ringGreen = new Color(0.2f, 0.85f, 0.3f, 1f);

    public GameWorld() {
        spots.add(new Spot("POM", 112f, 0f));
        spots.add(new Spot("WARUNG", -56f, 97f));
        spots.add(new Spot("VIEW POINT", -57.5f, -99.6f));
    }

    public void addCollider(float x, float z, float r) {
        colliders.add(new Collider(x, z, r));
    }

    /** Bangun seluruh geometri. Dipanggil sekali di GL thread. */
    public void build() {
        // Tekstur CC0 (ambientcg.com). Mipmap agar tidak berkilau dari jauh.
        texRoad = new Texture(Gdx.files.internal("tex/asphalt.jpg"), true);
        texRoad.setWrap(Texture.TextureWrap.MirroredRepeat, Texture.TextureWrap.MirroredRepeat);
        texRoad.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        texGrass = new Texture(Gdx.files.internal("tex/grass.jpg"), true);
        texGrass.setWrap(Texture.TextureWrap.MirroredRepeat, Texture.TextureWrap.MirroredRepeat);
        texGrass.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);

        // Laut (material disimpan untuk animasi), pantai, rumput bertekstur
        buildSea();
        addBox(0f, -0.30f, 0f, 420f, 0.5f, 420f, new Color(0.87f, 0.76f, 0.55f, 1f), 0f); // pasir
        addTexturedBox(0f, -0.25f, 0f, 340f, 0.5f, 340f, texGrass, 30f, 0f); // rumput

        // Jalan melingkar r=88..100 -> 72 segmen, SATU model bertekstur dipakai ulang
        Model roadModel = texturedBox(12f, 0.08f, 8.8f, texRoad, 6f);
        for (int i = 0; i < 72; i++) {
            float a = (float) (i / 72f * 2f * Math.PI);
            float cx = (float) Math.cos(a) * 94f;
            float cz = (float) Math.sin(a) * 94f;
            ModelInstance inst = new ModelInstance(roadModel);
            inst.transform.set(new Matrix4().setToRotation(Vector3.Y, (float) Math.toDegrees(-a))
                    .trn(cx, 0.03f, cz));
            statics.add(inst);
        }
        // Marka tengah putus-putus
        Color lineWhite = new Color(0.95f, 0.95f, 0.9f, 1f);
        for (int i = 0; i < 36; i++) {
            float a = (float) (i / 36f * 2f * Math.PI);
            float cx = (float) Math.cos(a) * 94f;
            float cz = (float) Math.sin(a) * 94f;
            addBox(cx, 0.09f, cz, 3.2f, 0.04f, 0.7f, lineWhite, (float) Math.toDegrees(-a));
        }

        buildKampung();
        buildHills();
        buildTrees();
        buildSpots();
        buildClouds();

        // Ring checkpoint: 3 spot (amber) + start (putih)
        for (int i = 0; i < 3; i++) {
            Spot s = spots.get(i);
            addRing(i, s.x, s.z, ringAmber);
        }
        addRing(3, startX, startZ, ringWhite);

        // Papan tanda jalan
        addSign("POM", 103f, 0f);
        addSign("WARUNG", -51f, 89f);
        addSign("VIEW POINT", -52f, -91f);
        addSign("START", startX + 7f, startZ);
    }

    /** Laut: box raksasa dengan material tersimpan agar warnanya bisa dianimasikan. */
    private void buildSea() {
        seaMat = new Material(ColorAttribute.createDiffuse(new Color(0.16f, 0.47f, 0.75f, 1f)));
        ModelBuilder mb = new ModelBuilder();
        Model model = mb.createBox(1600f, 0.5f, 1600f, seaMat, Usage.Position | Usage.Normal);
        models.add(model);
        ModelInstance inst = new ModelInstance(model);
        inst.transform.setToTranslation(0f, -1.45f, 0f);
        statics.add(inst);
    }

    /** Satu sisi quad dengan UV tiling. */
    private void face(MeshPartBuilder mpb,
                      float x1, float y1, float z1, float x2, float y2, float z2,
                      float x3, float y3, float z3, float x4, float y4, float z4,
                      float nx, float ny, float nz, float uRep, float vRep) {
        VertexInfo a = new VertexInfo().setPos(x1, y1, z1).setNor(nx, ny, nz).setUV(0f, 0f);
        VertexInfo b = new VertexInfo().setPos(x2, y2, z2).setNor(nx, ny, nz).setUV(uRep, 0f);
        VertexInfo c = new VertexInfo().setPos(x3, y3, z3).setNor(nx, ny, nz).setUV(uRep, vRep);
        VertexInfo d = new VertexInfo().setPos(x4, y4, z4).setNor(nx, ny, nz).setUV(0f, vRep);
        mpb.rect(a, b, c, d);
    }

    /**
     * Box bertekstur dengan UV tiling: tekstur diulang tiap `tile` meter.
     * Model didaftarkan ke `models` agar ikut di-dispose.
     */
    private Model texturedBox(float w, float h, float d, Texture tex, float tile) {
        float hw = w / 2f, hh = h / 2f, hd = d / 2f;
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        MeshPartBuilder mpb = mb.part("tbox", GL20.GL_TRIANGLES,
                Usage.Position | Usage.Normal | Usage.TextureCoordinates,
                new Material(TextureAttribute.createDiffuse(tex)));
        face(mpb, -hw, hh, -hd, hw, hh, -hd, hw, hh, hd, -hw, hh, hd,
                0f, 1f, 0f, w / tile, d / tile); // atas
        face(mpb, -hw, -hh, hd, hw, -hh, hd, hw, -hh, -hd, -hw, -hh, -hd,
                0f, -1f, 0f, w / tile, d / tile); // bawah
        face(mpb, -hw, -hh, hd, hw, -hh, hd, hw, hh, hd, -hw, hh, hd,
                0f, 0f, 1f, w / tile, h / tile); // depan
        face(mpb, hw, -hh, -hd, -hw, -hh, -hd, -hw, hh, -hd, hw, hh, -hd,
                0f, 0f, -1f, w / tile, h / tile); // belakang
        face(mpb, hw, -hh, hd, hw, -hh, -hd, hw, hh, -hd, hw, hh, hd,
                1f, 0f, 0f, d / tile, h / tile); // kanan
        face(mpb, -hw, -hh, -hd, -hw, -hh, hd, -hw, hh, hd, -hw, hh, -hd,
                -1f, 0f, 0f, d / tile, h / tile); // kiri
        Model model = mb.end();
        models.add(model);
        return model;
    }

    /** Box bertekstur sekali pakai (untuk rumput). */
    private ModelInstance addTexturedBox(float x, float y, float z, float w, float h, float d,
                                        Texture tex, float tile, float yawDeg) {
        ModelInstance inst = new ModelInstance(texturedBox(w, h, d, tex, tile));
        inst.transform.set(new Matrix4().setToRotation(Vector3.Y, yawDeg).trn(x, y, z));
        statics.add(inst);
        return inst;
    }

    private void buildKampung() {
        Random rnd = new Random(7);
        Color wall = new Color(0.93f, 0.88f, 0.76f, 1f);
        Color roof = new Color(0.72f, 0.32f, 0.22f, 1f);
        Color door = new Color(0.3f, 0.2f, 0.15f, 1f);
        for (int i = 0; i < 8; i++) {
            float a = (float) (i / 8f * 2f * Math.PI) + 0.3f;
            float dist = 22f + rnd.nextFloat() * 28f;
            float hx = (float) Math.cos(a) * dist;
            float hz = (float) Math.sin(a) * dist;
            float w = 7f + rnd.nextFloat() * 3f;
            float d = 6f + rnd.nextFloat() * 2f;
            float h = 4.5f;
            addBox(hx, h / 2, hz, w, h, d, wall, 0f); // dinding
            float diag = (float) Math.sqrt(w * w + d * d) / 2f;
            addCone(hx, h + 1.5f, hz, diag * 2f, 3f, diag * 2f, 4, roof, 45f); // atap piramida
            addBox(hx, 1.2f, hz + d / 2 + 0.05f, 1.6f, 2.4f, 0.15f, door, 0f); // pintu
            addCollider(hx, hz, diag + 1f);
        }
    }

    private void buildHills() {
        float[][] hills = {
                {50f, 45f, 22f}, {-60f, -30f, 18f},
                {130f, 60f, 24f}, {-120f, -70f, 20f}
        };
        Color c1 = new Color(0.30f, 0.55f, 0.30f, 1f);
        Color c2 = new Color(0.36f, 0.60f, 0.34f, 1f);
        for (float[] hh : hills) {
            float hx = hh[0], hz = hh[1], r = hh[2];
            float h = r * 1.2f;
            addCone(hx, h / 2, hz, r * 2f, h, r * 2f, 9, c1, 0f);
            addCone(hx, h * 0.55f + (h * 0.45f) / 2f, hz, r * 0.9f, h * 0.45f, r * 0.9f, 9, c2, 0f);
            addCollider(hx, hz, r * 0.85f);
        }
    }

    private void buildTrees() {
        Random rnd = new Random(42);
        Color trunk = new Color(0.42f, 0.29f, 0.18f, 1f);
        Color leaf1 = new Color(0.25f, 0.55f, 0.27f, 1f);
        Color leaf2 = new Color(0.29f, 0.60f, 0.30f, 1f);
        int placed = 0, tries = 0;
        while (placed < 55 && tries < 600) {
            tries++;
            float a = rnd.nextFloat() * 2f * (float) Math.PI;
            float dist = 12f + rnd.nextFloat() * 148f;
            float tx = (float) Math.cos(a) * dist;
            float tz = (float) Math.sin(a) * dist;
            float rr = (float) Math.sqrt(tx * tx + tz * tz);
            if (rr > 84f && rr < 104f) continue; // jangan di jalan
            boolean bad = false;
            for (Spot s : spots) {
                if (Math.hypot(tx - s.x, tz - s.z) < 14f) {
                    bad = true;
                    break;
                }
            }
            if (bad) continue;
            if (Math.hypot(tx - startX, tz - startZ) < 10f) continue;
            for (Collider c : colliders) {
                if (Math.hypot(tx - c.x, tz - c.z) < c.r + 2f) {
                    bad = true;
                    break;
                }
            }
            if (bad) continue;
            addCylinder(tx, 1.5f, tz, 0.9f, 3f, 0.9f, 6, trunk, 0f); // batang
            addCone(tx, 2.2f + 2.15f, tz, 5.2f, 4.3f, 5.2f, 7, leaf1, 0f); // tajuk bawah
            addCone(tx, 4.6f + 1.8f, tz, 3.6f, 3.6f, 3.6f, 7, leaf2, 0f); // tajuk atas
            addCollider(tx, tz, 1.2f);
            placed++;
        }
        // batu-batu
        Color rock = new Color(0.55f, 0.55f, 0.58f, 1f);
        for (int i = 0; i < 10; i++) {
            float a = rnd.nextFloat() * 2f * (float) Math.PI;
            float dist = 20f + rnd.nextFloat() * 130f;
            float bx = (float) Math.cos(a) * dist;
            float bz = (float) Math.sin(a) * dist;
            float rr = (float) Math.sqrt(bx * bx + bz * bz);
            if (rr < 84f || rr > 104f) {
                addBox(bx, 0.6f, bz, 2f, 1.2f, 1.6f, rock, rnd.nextFloat() * 90f);
            }
        }
    }

    private void buildSpots() {
        Color pillar = new Color(0.8f, 0.8f, 0.82f, 1f);
        // ---- POM (112, 0): kanopi merah-putih + kantor ----
        float sx = 112f, sz = 0f;
        float[][] posts = {{-6f, -4f}, {6f, -4f}, {-6f, 4f}, {6f, 4f}};
        for (float[] p : posts) addBox(sx + p[0], 2.5f, sz + p[1], 0.6f, 5f, 0.6f, pillar, 0f);
        addBox(sx, 5.4f, sz, 15f, 0.8f, 11f, new Color(0.85f, 0.2f, 0.2f, 1f), 0f); // atap merah
        addBox(sx, 4.9f, sz, 15.2f, 0.3f, 11.2f, new Color(0.95f, 0.95f, 0.95f, 1f), 0f); // lis putih
        addBox(sx + 10f, 1.5f, sz + 8f, 6f, 3f, 5f, new Color(0.92f, 0.9f, 0.85f, 1f), 0f); // kantor
        addCollider(sx, sz, 7.5f);
        addCollider(sx + 10f, sz + 8f, 5f);

        // ---- WARUNG (-56, 97): bangunan + tenda garis ----
        sx = -56f;
        sz = 97f;
        addBox(sx, 2f, sz, 9f, 4f, 7f, new Color(0.85f, 0.7f, 0.5f, 1f), 0f);
        addCone(sx, 4f + 1f, sz, 14f, 2f, 14f, 4, new Color(0.5f, 0.35f, 0.25f, 1f), 45f);
        for (int i = 0; i < 6; i++) {
            Color c = (i % 2 == 0) ? new Color(0.85f, 0.2f, 0.2f, 1f) : new Color(0.95f, 0.95f, 0.95f, 1f);
            addBox(sx - 3.5f + i * 1.4f, 3.4f, sz + 5.5f, 1.4f, 0.15f, 4f, c, 0f);
        }
        addBox(sx - 4f, 1.7f, sz + 3.6f, 0.3f, 3.4f, 0.3f, new Color(0.4f, 0.3f, 0.2f, 1f), 0f);
        addBox(sx + 4f, 1.7f, sz + 3.6f, 0.3f, 3.4f, 0.3f, new Color(0.4f, 0.3f, 0.2f, 1f), 0f);
        addCollider(sx, sz, 7.5f);

        // ---- VIEW POINT (-57.5, -99.6): deck + teropong ----
        sx = -57.5f;
        sz = -99.6f;
        addCylinder(sx, 0.6f, sz, 12f, 1.2f, 12f, 10, new Color(0.6f, 0.5f, 0.38f, 1f), 0f); // deck
        Color fence = new Color(0.45f, 0.32f, 0.2f, 1f);
        for (int i = 0; i < 10; i++) {
            float a = (float) (i / 10f * 2f * Math.PI);
            addBox(sx + (float) Math.cos(a) * 5.6f, 1.9f, sz + (float) Math.sin(a) * 5.6f,
                    0.25f, 1.6f, 0.25f, fence, 0f);
        }
        addCylinder(sx, 2.2f, sz, 0.6f, 2f, 0.6f, 6, new Color(0.3f, 0.3f, 0.32f, 1f), 0f); // tiang
        addBox(sx, 3.6f, sz, 2.2f, 0.7f, 0.7f, new Color(0.2f, 0.25f, 0.35f, 1f), 0f); // teropong
        addCollider(sx, sz, 6.5f);
    }

    private void buildClouds() {
        Random rnd = new Random(99);
        Color c1 = new Color(0.98f, 0.98f, 1f, 1f);
        Color c2 = new Color(0.96f, 0.96f, 0.99f, 1f);
        for (int i = 0; i < 7; i++) {
            float cx = (rnd.nextFloat() - 0.5f) * 500f;
            float cz = (rnd.nextFloat() - 0.5f) * 500f;
            float cy = 45f + rnd.nextFloat() * 25f;
            float s = 10f + rnd.nextFloat() * 10f;
            Cloud c = new Cloud();
            c.a = addBox(cx, cy, cz, s * 1.8f, s * 0.5f, s, c1, 0f);
            c.b = addBox(cx + s * 0.5f, cy + s * 0.2f, cz, s, s * 0.4f, s * 0.8f, c2, 0f);
            c.ax = cx;
            c.ay = cy;
            c.az = cz;
            c.bx = cx + s * 0.5f;
            c.by = cy + s * 0.2f;
            c.bz = cz;
            c.speed = 1.2f + rnd.nextFloat() * 1.6f;
            clouds.add(c);
        }
    }

    /** Papan tanda: tiang + papan bertekstur teks menghadap pusat pulau. */
    private void addSign(String text, float x, float z) {
        addCylinder(x, 2f, z, 0.4f, 4f, 0.4f, 6, new Color(0.35f, 0.35f, 0.38f, 1f), 0f);
        float yawDeg = (float) Math.toDegrees(Math.atan2(-x, -z));
        // backing papan
        addBox(x, 5.2f, z, 8.2f, 2.8f, 0.12f, new Color(0.11f, 0.14f, 0.32f, 1f), yawDeg);
        // permukaan teks
        Texture tex = SignFactory.makeSignTexture(text);
        signTextures.add(tex);
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        MeshPartBuilder mpb = mb.part("signface", GL20.GL_TRIANGLES,
                Usage.Position | Usage.Normal | Usage.TextureCoordinates,
                new Material(TextureAttribute.createDiffuse(tex)));
        // quad menghadap +Z; V dibalik agar teks tegak (Pixmap baris-0 = atas gambar)
        VertexInfo v00 = new VertexInfo().setPos(-4f, -1.3f, 0.09f).setNor(0f, 0f, 1f).setUV(0f, 1f);
        VertexInfo v10 = new VertexInfo().setPos(4f, -1.3f, 0.09f).setNor(0f, 0f, 1f).setUV(1f, 1f);
        VertexInfo v11 = new VertexInfo().setPos(4f, 1.3f, 0.09f).setNor(0f, 0f, 1f).setUV(1f, 0f);
        VertexInfo v01 = new VertexInfo().setPos(-4f, 1.3f, 0.09f).setNor(0f, 0f, 1f).setUV(0f, 0f);
        mpb.rect(v00, v10, v11, v01);
        Model model = mb.end();
        models.add(model);
        ModelInstance inst = new ModelInstance(model);
        inst.transform.set(new Matrix4().setToRotation(Vector3.Y, yawDeg).trn(x, 5.2f, z));
        statics.add(inst);
        addCollider(x, z, 0.8f);
    }

    /** Ring checkpoint mengambang + tiang cahaya. Satu material agar mudah diwarnai ulang. */
    private void addRing(int idx, float x, float z, Color c) {
        Material mat = new Material(ColorAttribute.createDiffuse(c));
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        MeshPartBuilder mpb = mb.part("ring", GL20.GL_TRIANGLES,
                Usage.Position | Usage.Normal, mat);
        int n = 40;
        float rIn = 5.5f, rOut = 7f, y = 1.6f;
        for (int i = 0; i < n; i++) {
            float a0 = (float) (i * 2f * Math.PI / n);
            float a1 = (float) ((i + 1) * 2f * Math.PI / n);
            float ix0 = x + (float) Math.cos(a0) * rIn, iz0 = z + (float) Math.sin(a0) * rIn;
            float ox0 = x + (float) Math.cos(a0) * rOut, oz0 = z + (float) Math.sin(a0) * rOut;
            float ix1 = x + (float) Math.cos(a1) * rIn, iz1 = z + (float) Math.sin(a1) * rIn;
            float ox1 = x + (float) Math.cos(a1) * rOut, oz1 = z + (float) Math.sin(a1) * rOut;
            // urutan (outer0, inner0, inner1, outer1) -> normal +Y
            mpb.rect(ox0, y, oz0, ix0, y, iz0, ix1, y, iz1, ox1, y, oz1, 0f, 1f, 0f);
        }
        Model model = mb.end();
        models.add(model);
        ModelInstance inst = new ModelInstance(model);
        statics.add(inst);
        // tiang cahaya tipis di tengah ring
        ModelInstance post = addBox(x, 0.8f, z, 0.5f, 1.6f, 0.5f, new Color(c.r, c.g, c.b, 1f), 0f);
        statics.remove(post); // digambar manual lewat ringPosts
        rings[idx] = inst;
        ringMats[idx] = mat;
        ringPosts[idx] = post;
    }

    /** Tandai ring sudah dikunjungi (hijau). */
    public void setRingVisited(int idx) {
        ringMats[idx].set(ColorAttribute.createDiffuse(ringGreen));
        // tiang ikut hijau: ganti material instance
        ringPosts[idx].materials.get(0).set(ColorAttribute.createDiffuse(ringGreen));
    }

    /** Kembalikan semua ring ke warna awal. */
    public void resetRings() {
        for (int i = 0; i < 4; i++) {
            Color c = (i < 3) ? ringAmber : ringWhite;
            ringMats[i].set(ColorAttribute.createDiffuse(c));
            ringPosts[i].materials.get(0).set(ColorAttribute.createDiffuse(c));
        }
        targetRing = 0;
    }

    /** Ring checkpoint target berikut (berdenyut). -1 = tak ada target. */
    public void setTargetRing(int idx) {
        targetRing = idx;
    }

    /**
     * Animasi dunia: laut berdenyut halus, awan melayang, ring target berdenyut.
     * Murah: hanya ubah warna material + translasi, tanpa rebuild geometri.
     */
    public void animate(float dt, float t) {
        // Laut: kecerahan berdenyut halus
        float b = 0.93f + 0.07f * (float) Math.sin(t * 0.9f);
        seaMat.set(ColorAttribute.createDiffuse(0.16f * b, 0.47f * b, 0.75f * b, 1f));
        // Awan melayang ke +x, bungkus di ±320
        for (Cloud c : clouds) {
            float dx = c.speed * dt;
            c.ax += dx;
            c.bx += dx;
            if (c.ax > 320f) {
                c.ax -= 640f;
                c.bx -= 640f;
            }
            c.apply();
        }
        // Ring target berdenyut amber
        if (targetRing >= 0 && targetRing < 3) {
            float p = 0.70f + 0.42f * (float) Math.sin(t * 5.2f);
            ringMats[targetRing].set(ColorAttribute.createDiffuse(
                    ringAmber.r * p, ringAmber.g * p, ringAmber.b * p, 1f));
        }
    }

    // ------------------------------------------------------------------ util

    private ModelInstance addBox(float x, float y, float z, float w, float h, float d,
                                 Color c, float yawDeg) {
        ModelBuilder mb = new ModelBuilder();
        Model model = mb.createBox(w, h, d,
                new Material(ColorAttribute.createDiffuse(c)),
                Usage.Position | Usage.Normal);
        models.add(model);
        ModelInstance inst = new ModelInstance(model);
        inst.transform.set(new Matrix4().setToRotation(Vector3.Y, yawDeg).trn(x, y, z));
        statics.add(inst);
        return inst;
    }

    private void addCylinder(float x, float y, float z, float w, float h, float d, int div,
                             Color c, float yawDeg) {
        ModelBuilder mb = new ModelBuilder();
        Model model = mb.createCylinder(w, h, d, div,
                new Material(ColorAttribute.createDiffuse(c)),
                Usage.Position | Usage.Normal);
        models.add(model);
        ModelInstance inst = new ModelInstance(model);
        inst.transform.set(new Matrix4().setToRotation(Vector3.Y, yawDeg).trn(x, y, z));
        statics.add(inst);
    }

    private void addCone(float x, float yCenter, float z, float w, float h, float d, int div,
                         Color c, float yawDeg) {
        ModelBuilder mb = new ModelBuilder();
        Model model = mb.createCone(w, h, d, div,
                new Material(ColorAttribute.createDiffuse(c)),
                Usage.Position | Usage.Normal);
        models.add(model);
        ModelInstance inst = new ModelInstance(model);
        inst.transform.set(new Matrix4().setToRotation(Vector3.Y, yawDeg).trn(x, yCenter, z));
        statics.add(inst);
    }

    public void render(ModelBatch batch, Environment env) {
        for (ModelInstance inst : statics) batch.render(inst, env);
        for (int i = 0; i < 4; i++) {
            batch.render(rings[i], env);
            batch.render(ringPosts[i], env);
        }
    }

    public void dispose() {
        for (Model m : models) m.dispose();
        models.clear();
        for (Texture t : signTextures) t.dispose();
        signTextures.clear();
        if (texRoad != null) texRoad.dispose();
        if (texGrass != null) texGrass.dispose();
        texRoad = null;
        texGrass = null;
    }
}
