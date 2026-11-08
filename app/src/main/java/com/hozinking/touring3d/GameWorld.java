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
import com.badlogic.gdx.graphics.g3d.ModelCache;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalShadowLight;
import com.badlogic.gdx.graphics.g3d.utils.DepthShaderProvider;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Pulau mini prosedural ala Sumatra/Jambi: laut bertekstur, pantai,
 * jalan melingkar, sawah, kebun sawit, kampung (rumah panggung + masjid),
 * bukit, pohon, 6 spot (POM, AIR TERJUN, PANTAI, WARUNG KOPI, PASAR,
 * VIEW POINT), lampu jalan, burung, awan, papan tanda, ring checkpoint.
 * Semua statis kecuali laut (scroll), air terjun (scroll), awan, burung, ring.
 * Tekstur CC0 (ambientcg.com) + air prosedural — lihat README.
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

    public static final int SPOT_COUNT = 6;
    public static final int RING_COUNT = SPOT_COUNT + 1; // 6 spot + start

    public final List<Spot> spots = new ArrayList<Spot>();
    public final List<Collider> colliders = new ArrayList<Collider>();
    /** Rumah panggung — target misi Antar Paket. */
    public final List<Spot> deliveryTargets = new ArrayList<Spot>();
    public final float startX = -94f;
    public final float startZ = 0f;
    /** Arah hadap awal: tangent jalan di titik start. */
    public final float startHeading = (float) Math.PI;

    private final List<Model> models = new ArrayList<Model>();
    private final List<Texture> textures = new ArrayList<Texture>();
    private final List<Texture> signTextures = new ArrayList<Texture>();
    private final List<ModelInstance> statics = new ArrayList<ModelInstance>();
    private final ModelInstance[] rings = new ModelInstance[RING_COUNT];
    private final Material[] ringMats = new Material[RING_COUNT];
    private final ModelInstance[] ringPosts = new ModelInstance[RING_COUNT];
    // Organisasi scene ala Mundus (mbrlabs, Apache 2.0): geometri statis
    // di-bake SEKALI ke ModelCache (pola GdxDemo3D/jsjolund, Apache 2.0) jadi
    // render loop hanya puluhan draw call. Yang dinamis (laut, air terjun,
    // awan, burung, ring) digambar terpisah.
    private ModelCache modelCache;
    private ModelInstance seaInstance;
    private DirectionalShadowLight shadowLight;
    private ModelBatch shadowBatch;

    // Tekstur CC0 (ambientcg.com) + air prosedural.
    private Texture texRoad, texGrass, texSand, texWater, texWall, texBrick,
            texRoof, texWood, texBark, texLeaf, texRock, texConcrete,
            texPaving, texDirt, texMetal, texFabric;
    // Atribut tekstur laut & air terjun (offset di-scroll di animate()).
    private TextureAttribute seaTexAttr;
    private TextureAttribute fallTexAttr;
    private final List<Cloud> clouds = new ArrayList<Cloud>();
    private final List<Bird> birds = new ArrayList<Bird>();
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

    private static class Bird {
        ModelInstance wingL, wingR;
        float x, y, z, speed;

        void apply() {
            wingL.transform.setToTranslation(x - 0.7f, y, z);
            wingR.transform.setToTranslation(x + 0.7f, y, z);
        }
    }

    private final Color ringAmber = new Color(1f, 0.72f, 0.15f, 1f);
    private final Color ringWhite = new Color(0.95f, 0.95f, 0.95f, 1f);
    private final Color ringGreen = new Color(0.2f, 0.85f, 0.3f, 1f);

    public GameWorld() {
        // Urutan tour memutar searah jarum jam: timur -> selatan -> barat -> utara.
        spots.add(new Spot("POM", 112f, 0f));
        spots.add(new Spot("AIR TERJUN", 128f, 82f));
        spots.add(new Spot("PANTAI", 30f, 185f));
        spots.add(new Spot("WARUNG KOPI", -56f, 97f));
        spots.add(new Spot("PASAR", -105f, 60f));
        spots.add(new Spot("VIEW POINT", -57.5f, -99.6f));
    }

    public void addCollider(float x, float z, float r) {
        colliders.add(new Collider(x, z, r));
    }

    /** Bangun seluruh geometri. Dipanggil sekali di GL thread. */
    public void build() {
        loadTextures();

        // Laut bertekstur (scroll), pantai pasir, rumput, jalan tanah
        buildSea();
        addTexturedBox(0f, -0.30f, 0f, 420f, 0.5f, 420f, texSand, 10f, 0f); // pasir
        addTexturedBox(0f, -0.25f, 0f, 340f, 0.5f, 340f, texGrass, 30f, 0f); // rumput
        // Jalan tanah di dalam pulau (menghubungkan spot off-road)
        addTexturedBox(60f, -0.20f, 90f, 90f, 0.4f, 14f, texDirt, 8f, 0f);

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
        // Marka tengah putus-putus (cat jalan memang putih)
        Color lineWhite = new Color(0.95f, 0.95f, 0.9f, 1f);
        for (int i = 0; i < 36; i++) {
            float a = (float) (i / 36f * 2f * Math.PI);
            float cx = (float) Math.cos(a) * 94f;
            float cz = (float) Math.sin(a) * 94f;
            addBox(cx, 0.09f, cz, 3.2f, 0.04f, 0.7f, lineWhite, (float) Math.toDegrees(-a));
        }

        buildKampung();
        buildSawah();
        buildSawit();
        buildMasjid();
        buildHills();
        buildTrees();
        buildSpots();
        buildClouds();
        buildBirds();
        buildLamps();

        // Ring checkpoint: 6 spot (amber) + start (putih)
        for (int i = 0; i < SPOT_COUNT; i++) {
            Spot s = spots.get(i);
            addRing(i, s.x, s.z, ringAmber);
        }
        addRing(SPOT_COUNT, startX, startZ, ringWhite);

        // Papan tanda jalan
        addSign("POM", 103f, 0f);
        addSign("AIR TERJUN", 118f, 74f);
        addSign("PANTAI", 26f, 172f);
        addSign("WARUNG KOPI", -51f, 89f);
        addSign("PASAR", -97f, 55f);
        addSign("VIEW POINT", -52f, -91f);
        addSign("START", startX + 7f, startZ);

        // Bake SEMUA geometri statis ke ModelCache (pola GdxDemo3D): material
        // yang sama digabung jadi satu mesh -> render loop jadi puluhan draw
        // call, bukan ratusan.
        modelCache = new ModelCache();
        modelCache.begin();
        for (ModelInstance inst : statics) modelCache.add(inst);
        modelCache.end();

        // Matahari + bayangan real-time (pola GdxDemo3D): shadow map 1024,
        // kotak ortho 60 m mengikuti motor.
        shadowLight = new DirectionalShadowLight(1024, 1024, 60f, 60f, 1f, 160f);
        shadowLight.set(new Color(0.85f, 0.85f, 0.85f, 1f), new Vector3(-0.55f, -1f, 0.35f));
        shadowBatch = new ModelBatch(new DepthShaderProvider());
    }

    /** Muat semua tekstur (mipmap agar tidak berkilau dari jauh). */
    private void loadTextures() {
        texRoad = loadTex("tex/asphalt.jpg");
        texGrass = loadTex("tex/grass.jpg");
        texSand = loadTex("tex/sand.jpg");
        texWater = loadTex("tex/water.jpg");
        texWall = loadTex("tex/wall.jpg");
        texBrick = loadTex("tex/brick.jpg");
        texRoof = loadTex("tex/roof.jpg");
        texWood = loadTex("tex/wood.jpg");
        texBark = loadTex("tex/bark.jpg");
        texLeaf = loadTex("tex/leaf.jpg");
        texRock = loadTex("tex/rock.jpg");
        texConcrete = loadTex("tex/concrete.jpg");
        texPaving = loadTex("tex/paving.jpg");
        texDirt = loadTex("tex/dirt.jpg");
        texMetal = loadTex("tex/metal.jpg");
        texFabric = loadTex("tex/fabric.jpg");
    }

    private Texture loadTex(String path) {
        Texture t = new Texture(Gdx.files.internal(path), true);
        t.setWrap(Texture.TextureWrap.MirroredRepeat, Texture.TextureWrap.MirroredRepeat);
        t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        textures.add(t);
        return t;
    }

    /** Material bertekstur; tint warna dikalikan ke tekstur (libGDX). */
    private Material matTex(Texture tex) {
        return new Material(TextureAttribute.createDiffuse(tex));
    }

    private Material matTexTint(Texture tex, Color tint) {
        return new Material(TextureAttribute.createDiffuse(tex),
                ColorAttribute.createDiffuse(tint));
    }

    /** Laut: bidang raksasa bertekstur air (UV tiling tiap 40 m), offset di-scroll. */
    private void buildSea() {
        seaTexAttr = TextureAttribute.createDiffuse(texWater);
        Material mat = new Material(seaTexAttr);
        float s = 800f; // setengah sisi
        float rep = 1600f / 40f; // tekstur diulang tiap 40 m
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        MeshPartBuilder mpb = mb.part("sea", GL20.GL_TRIANGLES,
                Usage.Position | Usage.Normal | Usage.TextureCoordinates, mat);
        face(mpb, -s, 0f, -s, s, 0f, -s, s, 0f, s, -s, 0f, s,
                0f, 1f, 0f, rep, rep);
        Model model = mb.end();
        models.add(model);
        ModelInstance inst = new ModelInstance(model);
        inst.transform.setToTranslation(0f, -1.45f, 0f);
        seaInstance = inst; // dinamis (scroll) -> tidak masuk cache
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
        return texturedBoxTint(w, h, d, tex, tile, null);
    }

    private Model texturedBoxTint(float w, float h, float d, Texture tex, float tile, Color tint) {
        float hw = w / 2f, hh = h / 2f, hd = d / 2f;
        ModelBuilder mb = new ModelBuilder();
        mb.begin();
        Material mat = (tint == null) ? matTex(tex) : matTexTint(tex, tint);
        MeshPartBuilder mpb = mb.part("tbox", GL20.GL_TRIANGLES,
                Usage.Position | Usage.Normal | Usage.TextureCoordinates, mat);
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

    /** Box bertekstur sekali pakai. */
    private ModelInstance addTexturedBox(float x, float y, float z, float w, float h, float d,
                                        Texture tex, float tile, float yawDeg) {
        return addTexturedBoxTint(x, y, z, w, h, d, tex, tile, null, yawDeg);
    }

    private ModelInstance addTexturedBoxTint(float x, float y, float z, float w, float h, float d,
                                            Texture tex, float tile, Color tint, float yawDeg) {
        ModelInstance inst = new ModelInstance(texturedBoxTint(w, h, d, tex, tile, tint));
        inst.transform.set(new Matrix4().setToRotation(Vector3.Y, yawDeg).trn(x, y, z));
        statics.add(inst);
        return inst;
    }

    /** Cone bertekstur (atap, tajuk pohon). */
    private ModelInstance addTexturedCone(float x, float yCenter, float z, float w, float h, float d,
                                          int div, Texture tex, Color tint, float yawDeg) {
        ModelBuilder mb = new ModelBuilder();
        Material mat = (tint == null) ? matTex(tex) : matTexTint(tex, tint);
        Model model = mb.createCone(w, h, d, div, mat,
                Usage.Position | Usage.Normal | Usage.TextureCoordinates);
        models.add(model);
        ModelInstance inst = new ModelInstance(model);
        inst.transform.set(new Matrix4().setToRotation(Vector3.Y, yawDeg).trn(x, yCenter, z));
        statics.add(inst);
        return inst;
    }

    /** Silinder bertekstur (batang, tiang, menara). */
    private ModelInstance addTexturedCylinder(float x, float y, float z, float w, float h, float d,
                                              int div, Texture tex, Color tint, float yawDeg) {
        ModelBuilder mb = new ModelBuilder();
        Material mat = (tint == null) ? matTex(tex) : matTexTint(tex, tint);
        Model model = mb.createCylinder(w, h, d, div, mat,
                Usage.Position | Usage.Normal | Usage.TextureCoordinates);
        models.add(model);
        ModelInstance inst = new ModelInstance(model);
        inst.transform.set(new Matrix4().setToRotation(Vector3.Y, yawDeg).trn(x, y, z));
        statics.add(inst);
        return inst;
    }

    /** Bola bertekstur (kubah masjid). */
    private ModelInstance addTexturedSphere(float x, float y, float z, float r,
                                            Texture tex, Color tint) {
        ModelBuilder mb = new ModelBuilder();
        Material mat = (tint == null) ? matTex(tex) : matTexTint(tex, tint);
        Model model = mb.createSphere(r * 2f, r * 2f, r * 2f, 12, 8, mat,
                Usage.Position | Usage.Normal | Usage.TextureCoordinates);
        models.add(model);
        ModelInstance inst = new ModelInstance(model);
        inst.transform.setToTranslation(x, y, z);
        statics.add(inst);
        return inst;
    }

    private void buildKampung() {
        Random rnd = new Random(7);
        // 6 rumah kampung: dinding plester + atap seng + pintu kayu
        for (int i = 0; i < 6; i++) {
            float a = (float) (i / 6f * 2f * Math.PI) + 0.3f;
            float dist = 22f + rnd.nextFloat() * 28f;
            float hx = (float) Math.cos(a) * dist;
            float hz = (float) Math.sin(a) * dist;
            float w = 7f + rnd.nextFloat() * 3f;
            float d = 6f + rnd.nextFloat() * 2f;
            float h = 4.5f;
            addTexturedBox(hx, h / 2, hz, w, h, d, texWall, 3f, 0f); // dinding plester
            float diag = (float) Math.sqrt(w * w + d * d) / 2f;
            addTexturedCone(hx, h + 1.5f, hz, diag * 2f, 3f, diag * 2f, 4,
                    texRoof, new Color(0.75f, 0.35f, 0.25f, 1f), 45f); // atap seng merah
            addTexturedBox(hx, 1.2f, hz + d / 2 + 0.05f, 1.6f, 2.4f, 0.15f,
                    texWood, 1f, 0f); // pintu kayu
            addCollider(hx, hz, diag + 1f);
        }
        // 4 rumah panggung: tiang kayu + lantai + dinding + atap seng.
        // Juga target misi Antar Paket.
        float[][] panggung = {
                {15f, 25f}, {-20f, 30f}, {25f, -25f}, {-5f, -35f}
        };
        for (int i = 0; i < panggung.length; i++) {
            float hx = panggung[i][0], hz = panggung[i][1];
            float w = 8f, d = 7f, floorY = 2.2f, h = 4f;
            // 6 tiang kayu
            for (float[] tp : new float[][]{{-3.4f, -2.9f}, {3.4f, -2.9f}, {-3.4f, 2.9f},
                    {3.4f, 2.9f}, {-3.4f, 0f}, {3.4f, 0f}}) {
                addTexturedCylinder(hx + tp[0], floorY / 2f, hz + tp[1], 0.5f, floorY, 0.5f,
                        6, texWood, null, 0f);
            }
            addTexturedBox(hx, floorY + 0.2f, hz, w + 0.6f, 0.4f, d + 0.6f,
                    texWood, 2f, 0f); // lantai kayu
            addTexturedBox(hx, floorY + 0.4f + h / 2f, hz, w, h, d, texWall, 3f, 0f); // dinding
            float diag = (float) Math.sqrt(w * w + d * d) / 2f;
            addTexturedCone(hx, floorY + 0.4f + h + 1.5f, hz, diag * 2f, 3f, diag * 2f, 4,
                    texRoof, new Color(0.35f, 0.45f, 0.65f, 1f), 45f); // atap seng biru
            // tangga kayu di depan
            addTexturedBox(hx, floorY / 2f, hz + d / 2f + 0.9f, 1.6f, floorY, 1.8f,
                    texWood, 1f, 0f);
            addCollider(hx, hz, diag + 1f);
            deliveryTargets.add(new Spot("RUMAH " + (i + 1), hx, hz + d / 2f + 3f));
        }
        // Pagar kayu di sekitar kampung
        for (int i = 0; i < 10; i++) {
            float a = (float) (i / 10f * 2f * Math.PI);
            float px = (float) Math.cos(a) * 52f;
            float pz = (float) Math.sin(a) * 52f;
            if (Math.hypot(px - startX, pz - startZ) < 12f) continue;
            addTexturedBox(px, 0.6f, pz, 4f, 1.2f, 0.2f, texWood, 2f,
                    (float) Math.toDegrees(-a));
        }
    }

    /** Sawah: petak hijau muda + petak berair. */
    private void buildSawah() {
        float[][] plots = {
                {30f, 60f, 22f, 14f}, {-42f, 42f, 18f, 12f}, {62f, -52f, 20f, 13f}
        };
        Color young = new Color(0.75f, 1f, 0.55f, 1f); // padi muda
        Color mid = new Color(0.55f, 0.9f, 0.4f, 1f); // padi setengah tumbuh
        for (int i = 0; i < plots.length; i++) {
            float px = plots[i][0], pz = plots[i][1], w = plots[i][2], d = plots[i][3];
            Color tint = (i % 2 == 0) ? young : mid;
            addTexturedBoxTint(px, -0.18f, pz, w, 0.3f, d, texGrass, 4f, tint, 0f);
            // petak berair (cermin kecil)
            addTexturedBox(px - w * 0.2f, -0.12f, pz, w * 0.45f, 0.2f, d * 0.5f,
                    texWater, 2f, 0f);
            // pematang tanah
            addTexturedBox(px, -0.12f, pz + d / 2f, w, 0.35f, 0.6f, texDirt, 2f, 0f);
        }
    }

    /** Kebun sawit: batang tinggi + pelepah panjang. */
    private void buildSawit() {
        Random rnd = new Random(123);
        int placed = 0, tries = 0;
        while (placed < 12 && tries < 200) {
            tries++;
            float px = 70f + rnd.nextFloat() * 60f;
            float pz = 20f + rnd.nextFloat() * 60f;
            float rr = (float) Math.sqrt(px * px + pz * pz);
            if (rr > 84f && rr < 104f) continue; // jangan di jalan
            boolean bad = false;
            for (Spot s : spots) {
                if (Math.hypot(px - s.x, pz - s.z) < 16f) { bad = true; break; }
            }
            if (bad) continue;
            for (Collider c : colliders) {
                if (Math.hypot(px - c.x, pz - c.z) < c.r + 2f) { bad = true; break; }
            }
            if (bad) continue;
            addTexturedCylinder(px, 5f, pz, 0.9f, 10f, 0.9f, 6, texBark, null, 0f);
            // 6 pelepah: daun panjang tipis miring ke bawah
            for (int i = 0; i < 6; i++) {
                float a = (float) (i / 6f * 2f * Math.PI);
                float fx = px + (float) Math.cos(a) * 2.6f;
                float fz = pz + (float) Math.sin(a) * 2.6f;
                ModelInstance frond = addTexturedBox(fx, 9.2f, fz, 5.5f, 0.12f, 1.1f,
                        texLeaf, 2f, (float) Math.toDegrees(-a));
                // miringkan pelepah ke bawah (rotasi X lokal via transform)
                Matrix4 m = new Matrix4().setToRotation(Vector3.Y, (float) Math.toDegrees(-a))
                        .rotate(Vector3.X, 28f).trn(fx, 9.2f, fz);
                frond.transform.set(m);
            }
            // tandan buah oranye di pangkal pelepah
            addTexturedSphere(px, 9.6f, pz, 0.7f, texFabric,
                    new Color(1f, 0.6f, 0.15f, 1f));
            addCollider(px, pz, 1.2f);
            placed++;
        }
    }

    /** Masjid kampung: bata + kubah hijau + menara. */
    private void buildMasjid() {
        float mx = 45f, mz = -45f;
        addTexturedBox(mx, 3f, mz, 12f, 6f, 9f, texBrick, 4f, 0f); // badan bata
        addTexturedBox(mx, 0.8f, mz + 4.6f, 2f, 1.6f, 0.3f, texWood, 1f, 0f); // pintu
        // kubah hijau di atas badan
        addTexturedSphere(mx, 6.8f, mz, 3.2f, texMetal, new Color(0.15f, 0.55f, 0.3f, 1f));
        addTexturedCylinder(mx, 9.5f, mz, 0.3f, 1.6f, 0.3f, 6, texMetal, null, 0f); // puncak
        // menara
        addTexturedCylinder(mx + 8f, 6f, mz + 5f, 1.6f, 12f, 1.6f, 8, texWall, null, 0f);
        addTexturedCone(mx + 8f, 12f + 1.2f, mz + 5f, 2.6f, 2.4f, 2.6f, 8,
                texRoof, new Color(0.15f, 0.55f, 0.3f, 1f), 0f);
        addCollider(mx, mz, 8f);
        addCollider(mx + 8f, mz + 5f, 2f);
    }

    private void buildHills() {
        float[][] hills = {
                {50f, 45f, 22f}, {-60f, -30f, 18f},
                {130f, 60f, 24f}, {-120f, -70f, 20f}
        };
        for (float[] hh : hills) {
            float hx = hh[0], hz = hh[1], r = hh[2];
            float h = r * 1.2f;
            // kaki bukit batu, puncak berumput
            addTexturedCone(hx, h / 2, hz, r * 2f, h, r * 2f, 9, texRock, null, 0f);
            addTexturedCone(hx, h * 0.55f + (h * 0.45f) / 2f, hz, r * 0.9f, h * 0.45f, r * 0.9f,
                    9, texGrass, new Color(0.7f, 1f, 0.7f, 1f), 0f);
            addCollider(hx, hz, r * 0.85f);
        }
    }

    private void buildTrees() {
        Random rnd = new Random(42);
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
            addTexturedCylinder(tx, 1.5f, tz, 0.9f, 3f, 0.9f, 6, texBark, null, 0f); // batang
            addTexturedCone(tx, 2.2f + 2.15f, tz, 5.2f, 4.3f, 5.2f, 7, texLeaf, null, 0f);
            addTexturedCone(tx, 4.6f + 1.8f, tz, 3.6f, 3.6f, 3.6f, 7, texLeaf, null, 0f);
            addCollider(tx, tz, 1.2f);
            placed++;
        }
        // batu-batu bertekstur
        for (int i = 0; i < 10; i++) {
            float a = rnd.nextFloat() * 2f * (float) Math.PI;
            float dist = 20f + rnd.nextFloat() * 130f;
            float bx = (float) Math.cos(a) * dist;
            float bz = (float) Math.sin(a) * dist;
            float rr = (float) Math.sqrt(bx * bx + bz * bz);
            if (rr < 84f || rr > 104f) {
                addTexturedBox(bx, 0.6f, bz, 2f, 1.2f, 1.6f, texRock, 1f, rnd.nextFloat() * 90f);
            }
        }
    }

    private void buildSpots() {
        Color pillar = new Color(0.8f, 0.8f, 0.82f, 1f);
        // ---- POM (112, 0): kanopi seng merah + kantor plester + 2 pompa ----
        float sx = 112f, sz = 0f;
        float[][] posts = {{-6f, -4f}, {6f, -4f}, {-6f, 4f}, {6f, 4f}};
        for (float[] p : posts) addBox(sx + p[0], 2.5f, sz + p[1], 0.6f, 5f, 0.6f, pillar, 0f);
        addTexturedBoxTint(sx, 5.4f, sz, 15f, 0.8f, 11f, texRoof, 3f,
                new Color(0.85f, 0.2f, 0.2f, 1f), 0f); // atap seng merah
        addTexturedBox(sx, 4.9f, sz, 15.2f, 0.3f, 11.2f, texWall, 4f, 0f); // lis putih
        addTexturedBox(sx + 10f, 1.5f, sz + 8f, 6f, 3f, 5f, texWall, 2f, 0f); // kantor
        // 2 pompa bensin
        addTexturedBoxTint(sx - 2f, 1f, sz, 1.2f, 2f, 0.8f, texMetal, 1f,
                new Color(0.85f, 0.2f, 0.2f, 1f), 0f);
        addTexturedBoxTint(sx + 2f, 1f, sz, 1.2f, 2f, 0.8f, texMetal, 1f,
                new Color(0.85f, 0.2f, 0.2f, 1f), 0f);
        addCollider(sx, sz, 7.5f);
        addCollider(sx + 10f, sz + 8f, 5f);

        // ---- AIR TERJUN (128, 82): tebing batu + tirai air + kolam ----
        sx = 128f;
        sz = 82f;
        addTexturedBox(140f, 5f, 88f, 8f, 10f, 14f, texRock, 3f, 0f); // tebing
        // tirai air (plane vertikal, offset di-scroll)
        fallTexAttr = TextureAttribute.createDiffuse(texWater);
        {
            Material mat = new Material(fallTexAttr);
            ModelBuilder mb = new ModelBuilder();
            mb.begin();
            MeshPartBuilder mpb = mb.part("fall", GL20.GL_TRIANGLES,
                    Usage.Position | Usage.Normal | Usage.TextureCoordinates, mat);
            face(mpb, -3f, 0f, 0f, 3f, 0f, 0f, 3f, 9f, 0f, -3f, 9f, 0f,
                    0f, 0f, -1f, 2f, 3f);
            Model model = mb.end();
            models.add(model);
            ModelInstance inst = new ModelInstance(model);
            inst.transform.set(new Matrix4().setToRotation(Vector3.Y, -38f)
                    .trn(135.5f, 0.5f, 84.5f));
            statics.add(inst);
        }
        // kolam di kaki air terjun
        addTexturedCylinder(131f, -0.1f, 79f, 10f, 0.4f, 10f, 12, texWater, null, 0f);
        addCollider(140f, 88f, 8f);

        // ---- PANTAI (30, 185): payung + kursi pantai ----
        sx = 30f;
        sz = 185f;
        addTexturedBox(sx, -0.15f, sz, 26f, 0.3f, 20f, texSand, 4f, 0f); // pasir halus
        addTexturedCylinder(sx - 4f, 1.4f, sz, 0.25f, 2.8f, 0.25f, 6, texWood, null, 0f);
        addTexturedCone(sx - 4f, 3.4f, sz, 7f, 1.6f, 7f, 8, texFabric,
                new Color(1f, 0.5f, 0.2f, 1f), 0f); // payung oranye
        addTexturedCylinder(sx + 5f, 1.4f, sz + 3f, 0.25f, 2.8f, 0.25f, 6, texWood, null, 0f);
        addTexturedCone(sx + 5f, 3.4f, sz + 3f, 7f, 1.6f, 7f, 8, texFabric,
                new Color(0.2f, 0.6f, 1f, 1f), 0f); // payung biru
        addTexturedBox(sx, 0.6f, sz - 2f, 3f, 0.5f, 1.2f, texWood, 1f, 20f); // kursi
        addTexturedBox(sx + 6f, 0.6f, sz - 4f, 3f, 0.5f, 1.2f, texWood, 1f, -15f);
        addCollider(sx - 4f, sz, 1f);
        addCollider(sx + 5f, sz + 3f, 1f);

        // ---- WARUNG KOPI (-56, 97): bangunan kayu + tenda kain + meja ----
        sx = -56f;
        sz = 97f;
        addTexturedBox(sx, 2f, sz, 9f, 4f, 7f, texWood, 3f, 0f); // dinding kayu
        addTexturedCone(sx, 4f + 1f, sz, 14f, 2f, 14f, 4, texRoof,
                new Color(0.5f, 0.35f, 0.25f, 1f), 45f); // atap seng coklat
        // tenda kain garis (pakai tekstur kain, warna bergantian via tint)
        for (int i = 0; i < 6; i++) {
            Color c = (i % 2 == 0) ? new Color(0.85f, 0.2f, 0.2f, 1f)
                    : new Color(0.95f, 0.95f, 0.95f, 1f);
            addTexturedBoxTint(sx - 3.5f + i * 1.4f, 3.4f, sz + 5.5f, 1.4f, 0.15f, 4f,
                    texFabric, 1f, c, 0f);
        }
        addBox(sx - 4f, 1.7f, sz + 3.6f, 0.3f, 3.4f, 0.3f, new Color(0.4f, 0.3f, 0.2f, 1f), 0f);
        addBox(sx + 4f, 1.7f, sz + 3.6f, 0.3f, 3.4f, 0.3f, new Color(0.4f, 0.3f, 0.2f, 1f), 0f);
        // meja + kursi kayu di depan
        addTexturedBox(sx - 2f, 0.8f, sz + 6.5f, 2.2f, 0.15f, 1.4f, texWood, 1f, 0f);
        addTexturedBox(sx + 2f, 0.8f, sz + 6.5f, 2.2f, 0.15f, 1.4f, texWood, 1f, 0f);
        addCollider(sx, sz, 7.5f);

        // ---- PASAR (-105, 60): plaza paving + 4 kios + penduduk ----
        sx = -105f;
        sz = 60f;
        addTexturedBox(sx, -0.18f, sz, 34f, 0.35f, 26f, texPaving, 6f, 0f); // plaza
        Color[] awning = {
                new Color(1f, 0.45f, 0.15f, 1f), new Color(0.2f, 0.65f, 0.25f, 1f),
                new Color(0.95f, 0.8f, 0.15f, 1f), new Color(0.85f, 0.25f, 0.45f, 1f)
        };
        float[][] kios = {{-10f, -6f}, {10f, -6f}, {-10f, 6f}, {10f, 6f}};
        for (int i = 0; i < 4; i++) {
            float kx = sx + kios[i][0], kz = sz + kios[i][1];
            addTexturedBox(kx, 1.5f, kz, 6f, 3f, 5f, texBrick, 2f, 0f); // dinding bata
            addTexturedBoxTint(kx, 3.4f, kz, 7f, 0.3f, 6f, texFabric, 2f, awning[i], 0f); // atap kain
            // meja dagangan + buah/sayur warna-warni (kecil)
            addTexturedBox(kx, 0.9f, kz + 3.4f, 5f, 0.5f, 1.2f, texWood, 1f, 0f);
            addBox(kx - 1.5f, 1.35f, kz + 3.4f, 0.9f, 0.5f, 0.9f,
                    new Color(1f, 0.5f, 0.1f, 1f), 0f); // jeruk
            addBox(kx, 1.35f, kz + 3.4f, 0.9f, 0.5f, 0.9f,
                    new Color(0.3f, 0.8f, 0.2f, 1f), 0f); // sayur
            addBox(kx + 1.5f, 1.35f, kz + 3.4f, 0.9f, 0.5f, 0.9f,
                    new Color(0.9f, 0.15f, 0.2f, 1f), 0f); // cabai
            addCollider(kx, kz, 4.5f);
        }
        // penduduk statis di plaza (badan + kepala)
        float[][] people = {{-2f, -2f, 0.15f}, {3f, 2f, 0.65f}, {0f, -7f, 0.35f}};
        for (float[] p : people) {
            float px = sx + p[0], pz = sz + p[1];
            Color shirt = new Color(p[2], 0.5f, 0.7f - p[2] * 0.4f, 1f);
            addTexturedBoxTint(px, 0.9f, pz, 0.9f, 1.8f, 0.6f, texFabric, 1f, shirt, 0f);
            addTexturedSphere(px, 2.1f, pz, 0.42f, texWall, new Color(0.85f, 0.62f, 0.45f, 1f));
        }
        addCollider(sx, sz, 6f);

        // ---- VIEW POINT (-57.5, -99.6): deck kayu + pagar + teropong ----
        sx = -57.5f;
        sz = -99.6f;
        addTexturedCylinder(sx, 0.6f, sz, 12f, 1.2f, 12f, 10, texWood, null, 0f); // deck kayu
        for (int i = 0; i < 10; i++) {
            float a = (float) (i / 10f * 2f * Math.PI);
            addTexturedBox(sx + (float) Math.cos(a) * 5.6f, 1.9f, sz + (float) Math.sin(a) * 5.6f,
                    0.25f, 1.6f, 0.25f, texWood, 1f, 0f);
        }
        addTexturedCylinder(sx, 2.2f, sz, 0.6f, 2f, 0.6f, 6, texMetal, null, 0f); // tiang
        addTexturedBox(sx, 3.6f, sz, 2.2f, 0.7f, 0.7f, texMetal, 1f, 0f); // teropong
        addTexturedBox(sx + 3f, 0.9f, sz + 3f, 2.4f, 0.5f, 1f, texWood, 1f, 30f); // bangku
        addCollider(sx, sz, 6.5f);
    }

    private void buildClouds() {
        Random rnd = new Random(99);
        Color c1 = new Color(0.98f, 0.98f, 1f, 1f);
        Color c2 = new Color(0.96f, 0.96f, 0.99f, 1f);
        for (int i = 0; i < 10; i++) {
            float cx = (rnd.nextFloat() - 0.5f) * 500f;
            float cz = (rnd.nextFloat() - 0.5f) * 500f;
            float cy = 45f + rnd.nextFloat() * 25f;
            float s = 10f + rnd.nextFloat() * 10f;
            Cloud c = new Cloud();
            c.a = addBox(cx, cy, cz, s * 1.8f, s * 0.5f, s, c1, 0f);
            c.b = addBox(cx + s * 0.5f, cy + s * 0.2f, cz, s, s * 0.4f, s * 0.8f, c2, 0f);
            statics.remove(c.a); // awan bergerak -> dinamis, tidak masuk cache
            statics.remove(c.b);
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

    /** Burung: bentuk V dari 2 sayap tipis, melintas langit. */
    private void buildBirds() {
        Random rnd = new Random(5);
        Color dark = new Color(0.12f, 0.12f, 0.14f, 1f);
        for (int i = 0; i < 5; i++) {
            Bird b = new Bird();
            b.x = (rnd.nextFloat() - 0.5f) * 400f;
            b.y = 32f + rnd.nextFloat() * 18f;
            b.z = (rnd.nextFloat() - 0.5f) * 400f;
            b.speed = 6f + rnd.nextFloat() * 5f;
            b.wingL = addBox(b.x - 0.7f, b.y, b.z, 1.4f, 0.08f, 0.5f, dark, 25f);
            b.wingR = addBox(b.x + 0.7f, b.y, b.z, 1.4f, 0.08f, 0.5f, dark, -25f);
            statics.remove(b.wingL);
            statics.remove(b.wingR);
            birds.add(b);
        }
    }

    /** Lampu jalan di sepanjang ring (tiang metal + kepala kuning). */
    private void buildLamps() {
        Color lampHead = new Color(1f, 0.85f, 0.4f, 1f);
        for (int i = 0; i < 12; i++) {
            float a = (float) (i / 12f * 2f * Math.PI);
            float r = (i % 2 == 0) ? 101f : 87f;
            float lx = (float) Math.cos(a) * r;
            float lz = (float) Math.sin(a) * r;
            addTexturedCylinder(lx, 2.5f, lz, 0.35f, 5f, 0.35f, 6, texMetal, null, 0f);
            addBox(lx, 5.1f, lz, 1.4f, 0.4f, 0.6f, lampHead, (float) Math.toDegrees(-a));
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

    /** Matahari untuk bayangan real-time (didaftarkan ke Environment pemanggil). */
    public DirectionalShadowLight getShadowLight() {
        return shadowLight;
    }

    /**
     * Depth pass bayangan ala GdxDemo3D: kamera ortho 60m mengikuti motor.
     * center = posisi motor (y=0), forward = arah kamera utama,
     * extra = instance dinamis lain yang ikut membuang bayangan (motor).
     */
    public void renderShadows(Vector3 center, Vector3 forward, List<ModelInstance> extra) {
        shadowLight.begin(center, forward);
        shadowBatch.begin(shadowLight.getCamera());
        shadowBatch.render(modelCache);
        if (extra != null) {
            for (ModelInstance inst : extra) shadowBatch.render(inst);
        }
        shadowBatch.end();
        shadowLight.end();
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
        // ring warnanya dinamis (denyut target) -> digambar manual via rings[], bukan cache
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
        for (int i = 0; i < RING_COUNT; i++) {
            Color c = (i < SPOT_COUNT) ? ringAmber : ringWhite;
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
     * Animasi dunia: laut scroll, air terjun mengalir, awan melayang,
     * burung melintas, ring target berdenyut. Murah: hanya ubah offset
     * tekstur + translasi, tanpa rebuild geometri.
     */
    public void animate(float dt, float t) {
        // Laut: tekstur bergeser pelan (arus)
        if (seaTexAttr != null) {
            seaTexAttr.offsetU += dt * 0.006f;
            seaTexAttr.offsetV += dt * 0.004f;
        }
        // Air terjun: tekstur mengalir ke bawah
        if (fallTexAttr != null) fallTexAttr.offsetV -= dt * 0.35f;
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
        // Burung melintas ke +x, bungkus di ±320
        for (Bird b : birds) {
            b.x += b.speed * dt;
            if (b.x > 320f) b.x -= 640f;
            b.apply();
        }
        // Ring target berdenyut amber
        if (targetRing >= 0 && targetRing < SPOT_COUNT) {
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
        batch.render(modelCache, env); // seluruh dunia statis: 1 provider
        batch.render(seaInstance, env); // laut (scroll)
        for (Cloud c : clouds) { // awan (bergerak)
            batch.render(c.a, env);
            batch.render(c.b, env);
        }
        for (Bird b : birds) { // burung (melintas)
            batch.render(b.wingL, env);
            batch.render(b.wingR, env);
        }
        for (int i = 0; i < RING_COUNT; i++) { // ring + tiang (warna dinamis)
            batch.render(rings[i], env);
            batch.render(ringPosts[i], env);
        }
    }

    public void dispose() {
        if (modelCache != null) modelCache.dispose();
        if (shadowBatch != null) shadowBatch.dispose();
        if (shadowLight != null) shadowLight.dispose();
        for (Model m : models) m.dispose();
        models.clear();
        for (Texture t : signTextures) t.dispose();
        signTextures.clear();
        for (Texture t : textures) t.dispose();
        textures.clear();
    }
}
