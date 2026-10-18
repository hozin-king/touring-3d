package com.hozinking.touring3d

import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class Collider(val x: Float, val z: Float, val r: Float)
data class SpotInfo(val name: String, val x: Float, val z: Float)

/**
 * Pulau mini prosedural: laut, pantai, jalan melingkar, kampung,
 * bukit, pohon, dan 3 spot (POM, WARUNG, VIEW POINT).
 */
class World {
    val spots = listOf(
        SpotInfo("POM", 112f, 0f),
        SpotInfo("WARUNG", -56f, 97f),
        SpotInfo("VIEW POINT", -57.5f, -99.6f)
    )
    val startX = -94f
    val startZ = 0f
    /** Arah hadap awal: tangent jalan di titik start. */
    val startHeading = PI.toFloat()

    val colliders = ArrayList<Collider>()

    fun addCollider(x: Float, z: Float, r: Float) {
        colliders.add(Collider(x, z, r))
    }

    /** Bangun seluruh geometri statis jadi satu entity. */
    fun build(engine: Engine, mi: MaterialInstance): Int {
        val m = MeshBuilder()

        // Laut, pantai, rumput
        m.groundPlane(1600f, -1.2f, 0.16f, 0.47f, 0.75f) // laut
        m.groundPlane(420f, -0.05f, 0.87f, 0.76f, 0.55f) // pasir
        m.groundPlane(340f, 0f, 0.36f, 0.66f, 0.33f)     // rumput

        // Jalan melingkar r=88..100
        m.flatRing(0f, 0f, 0.03f, 88f, 100f, 72, 0.24f, 0.24f, 0.27f)
        // Marka tengah putus-putus
        for (i in 0 until 36) {
            val a = (i.toFloat() / 36f) * 2f * PI.toFloat()
            val cx = cos(a) * 94f; val cz = sin(a) * 94f
            val dx = -sin(a); val dz = cos(a) // arah tangent
            val px = -dz; val pz = dx          // arah radial tegak lurus
            val hw = 1.6f; val hl = 0.35f
            m.quad(
                cx - dx * hl - px * hw, 0.05f, cz - dz * hl - pz * hw,
                cx + dx * hl - px * hw, 0.05f, cz + dz * hl - pz * hw,
                cx + dx * hl + px * hw, 0.05f, cz + dz * hl + pz * hw,
                cx - dx * hl + px * hw, 0.05f, cz - dz * hl + pz * hw,
                0.95f, 0.95f, 0.9f
            )
        }

        buildKampung(m)
        buildHills(m)
        buildTrees(m)
        buildSpots(m)
        buildClouds(m)

        return m.build(engine, mi)
    }

    private fun buildKampung(m: MeshBuilder) {
        val rnd = Random(7)
        val wallR = 0.93f; val wallG = 0.88f; val wallB = 0.76f
        val roofR = 0.72f; val roofG = 0.32f; val roofB = 0.22f
        repeat(8) { i ->
            val a = (i.toFloat() / 8f) * 2f * PI.toFloat() + 0.3f
            val dist = 22f + rnd.nextFloat() * 28f
            val hx = cos(a) * dist; val hz = sin(a) * dist
            val w = 7f + rnd.nextFloat() * 3f
            val d = 6f + rnd.nextFloat() * 2f
            val h = 4.5f
            // dinding
            m.box(hx, h / 2, hz, w, h, d, wallR, wallG, wallB)
            // atap piramida (segi-4, diputar 45 derajat agar sejajar kotak)
            val diag = hypot(w, d) / 2f
            m.cone(hx, hz, h, h + 3f, diag, 4, roofR, roofG, roofB, PI.toFloat() / 4f)
            // pintu
            m.box(hx, 1.2f, hz + d / 2 + 0.05f, 1.6f, 2.4f, 0.15f, 0.3f, 0.2f, 0.15f)
            addCollider(hx, hz, (diag + 1f))
        }
    }

    private fun buildHills(m: MeshBuilder) {
        val hills = listOf(
            Triple(50f, 45f, 22f), Triple(-60f, -30f, 18f),
            Triple(130f, 60f, 24f), Triple(-120f, -70f, 20f)
        )
        for ((hx, hz, r) in hills) {
            val h = r * 1.2f
            m.cone(hx, hz, 0f, h, r, 9, 0.30f, 0.55f, 0.30f)
            m.cone(hx, hz, h * 0.55f, h, r * 0.45f, 9, 0.36f, 0.60f, 0.34f)
            addCollider(hx, hz, r * 0.85f)
        }
    }

    private fun buildTrees(m: MeshBuilder) {
        val rnd = Random(42)
        var placed = 0
        var tries = 0
        while (placed < 55 && tries < 600) {
            tries++
            val a = rnd.nextFloat() * 2f * PI.toFloat()
            val dist = 12f + rnd.nextFloat() * 148f
            val tx = cos(a) * dist; val tz = sin(a) * dist
            val rr = hypot(tx, tz)
            if (rr > 84f && rr < 104f) continue          // jangan di jalan
            if (spots.any { hypot(tx - it.x, tz - it.z) < 14f }) continue
            if (hypot(tx - startX, tz - startZ) < 10f) continue
            if (colliders.any { hypot(tx - it.x, tz - it.z) < it.r + 2f }) continue
            // batang + 2 tajuk
            m.prism(tx, tz, 0f, 3f, 0.45f, 6, 0.42f, 0.29f, 0.18f)
            m.cone(tx, tz, 2.2f, 6.5f, 2.6f, 7, 0.25f, 0.55f, 0.27f)
            m.cone(tx, tz, 4.6f, 8.2f, 1.8f, 7, 0.29f, 0.60f, 0.30f)
            addCollider(tx, tz, 1.2f)
            placed++
        }
        // batu-batu
        repeat(10) {
            val a = rnd.nextFloat() * 2f * PI.toFloat()
            val dist = 20f + rnd.nextFloat() * 130f
            val bx = cos(a) * dist; val bz = sin(a) * dist
            val rr = hypot(bx, bz)
            if (rr < 84f || rr > 104f) {
                m.box(bx, 0.6f, bz, 2f, 1.2f, 1.6f, 0.55f, 0.55f, 0.58f)
            }
        }
    }

    private fun buildSpots(m: MeshBuilder) {
        // ---- POM (112, 0): kanopi merah-putih + kantor ----
        run {
            val (sx, sz) = 112f to 0f
            for ((px, pz) in listOf(-6f to -4f, 6f to -4f, -6f to 4f, 6f to 4f)) {
                m.box(sx + px, 2.5f, sz + pz, 0.6f, 5f, 0.6f, 0.8f, 0.8f, 0.82f)
            }
            m.box(sx, 5.4f, sz, 15f, 0.8f, 11f, 0.85f, 0.2f, 0.2f)   // atap merah
            m.box(sx, 4.9f, sz, 15.2f, 0.3f, 11.2f, 0.95f, 0.95f, 0.95f) // lis putih
            m.box(sx + 10f, 1.5f, sz + 8f, 6f, 3f, 5f, 0.92f, 0.9f, 0.85f) // kantor
            // Collider 7.5 (bukan 9): radius deteksi spot = 9, collider 9 bikin
            // motor tertahan di jarak 10 > 9 sehingga spot POM tak pernah tercapai.
            addCollider(sx, sz, 7.5f)
            addCollider(sx + 10f, sz + 8f, 5f)
        }
        // ---- WARUNG (-56, 97): bangunan + tenda garis ----
        run {
            val (sx, sz) = -56f to 97f
            m.box(sx, 2f, sz, 9f, 4f, 7f, 0.85f, 0.7f, 0.5f)
            m.cone(sx, sz, 4f, 6f, 7f, 4, 0.5f, 0.35f, 0.25f, PI.toFloat() / 4f)
            // tenda depan: garis-garis merah putih
            for (i in 0 until 6) {
                val c = if (i % 2 == 0) Triple(0.85f, 0.2f, 0.2f) else Triple(0.95f, 0.95f, 0.95f)
                m.box(sx - 3.5f + i * 1.4f, 3.4f, sz + 5.5f, 1.4f, 0.15f, 4f, c.first, c.second, c.third)
            }
            for ((px, pz) in listOf(-4f to 3.6f, 4f to 3.6f)) {
                m.box(sx + px, 1.7f, sz + pz, 0.3f, 3.4f, 0.3f, 0.4f, 0.3f, 0.2f)
            }
            addCollider(sx, sz, 7.5f)
        }
        // ---- VIEW POINT (-57.5, -99.6): deck + teropong ----
        run {
            val (sx, sz) = -57.5f to -99.6f
            m.prism(sx, sz, 0f, 1.2f, 6f, 10, 0.6f, 0.5f, 0.38f) // deck
            for (i in 0 until 10) { // pagar
                val a = (i.toFloat() / 10f) * 2f * PI.toFloat()
                m.box(sx + cos(a) * 5.6f, 1.9f, sz + sin(a) * 5.6f, 0.25f, 1.6f, 0.25f, 0.45f, 0.32f, 0.2f)
            }
            m.prism(sx, sz, 1.2f, 3.2f, 0.3f, 6, 0.3f, 0.3f, 0.32f) // tiang teropong
            m.box(sx, 3.6f, sz, 2.2f, 0.7f, 0.7f, 0.2f, 0.25f, 0.35f) // teropong
            addCollider(sx, sz, 6.5f)
        }
    }

    private fun buildClouds(m: MeshBuilder) {
        val rnd = Random(99)
        repeat(7) {
            val cx = (rnd.nextFloat() - 0.5f) * 500f
            val cz = (rnd.nextFloat() - 0.5f) * 500f
            val cy = 45f + rnd.nextFloat() * 25f
            val s = 10f + rnd.nextFloat() * 10f
            m.box(cx, cy, cz, s * 1.8f, s * 0.5f, s, 0.98f, 0.98f, 1f)
            m.box(cx + s * 0.5f, cy + s * 0.2f, cz, s, s * 0.4f, s * 0.8f, 0.96f, 0.96f, 0.99f)
        }
    }

    /** Ring checkpoint mengambang — dibangun terpisah agar bisa diwarnai ulang. */
    fun buildRing(
        engine: Engine, mi: MaterialInstance,
        x: Float, z: Float, r: Float, g: Float, b: Float
    ): Int {
        val m = MeshBuilder()
        m.flatRing(x, z, 1.6f, 5.5f, 7f, 40, r, g, b)
        // tiang cahaya tipis
        m.prism(x, z, 0f, 1.6f, 0.4f, 6, r, g, b)
        return m.build(engine, mi)
    }
}
