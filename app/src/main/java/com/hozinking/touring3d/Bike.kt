package com.hozinking.touring3d

import android.opengl.Matrix
import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.cos

/**
 * Motor + fisika arcade: gas/rem/belok, tabrakan lingkaran sederhana.
 * Model low-poly menghadap +Z.
 */
class Bike {
    var x = 0f
    var z = 0f
    var heading = 0f
    var speed = 0f

    private var leanVisual = 0f
    private val matrix = FloatArray(16)

    fun reset(x0: Float, z0: Float, h0: Float) {
        x = x0; z = z0; heading = h0; speed = 0f; leanVisual = 0f
    }

    fun update(dt: Float, steer: Float, gas: Boolean, brake: Boolean, world: World) {
        val maxSpeed = 30f // ~108 km/h
        if (gas) speed += 9.5f * dt
        if (brake) speed -= 22f * dt
        // hambatan + perlambatan alami
        speed -= speed * 0.45f * dt
        if (!gas) speed -= 1.2f * dt
        speed = speed.coerceIn(0f, maxSpeed)

        val authority = (speed / 8f).coerceIn(0f, 1f)
        heading += steer * 2.1f * authority * dt

        x += sin(heading) * speed * dt
        z += cos(heading) * speed * dt

        // tabrakan: dorong keluar lingkaran collider
        for (c in world.colliders) {
            val dx = x - c.x
            val dz = z - c.z
            val d = hypot(dx, dz)
            val minD = c.r + 1f
            if (d < minD && d > 0.0001f) {
                x = c.x + dx / d * minD
                z = c.z + dz / d * minD
                speed *= 0.55f
            }
        }
        // batas pulau
        val r = hypot(x, z)
        if (r > 165f) {
            x *= 165f / r
            z *= 165f / r
            speed *= 0.7f
        }

        leanVisual += (steer * authority - leanVisual) * min(1f, 8f * dt)
    }

    fun speedKmh(): Int = (speed * 3.6f).toInt()

    /** Matriks model: translasi * rotasi Y (heading) * guling (lean). */
    fun writeMatrix(): FloatArray {
        Matrix.setIdentityM(matrix, 0)
        Matrix.translateM(matrix, 0, x, 0f, z)
        Matrix.rotateM(matrix, 0, Math.toDegrees(heading.toDouble()).toFloat(), 0f, 1f, 0f)
        Matrix.rotateM(matrix, 0, -leanVisual * 22f, 0f, 0f, 1f)
        return matrix
    }

    /** Bangun mesh motor + pengendara (ruang lokal, menghadap +Z). */
    fun buildMesh(engine: Engine, mi: MaterialInstance): Int {
        val m = MeshBuilder()
        val dark = Triple(0.15f, 0.15f, 0.16f)
        val red = Triple(0.80f, 0.16f, 0.16f)
        val redDark = Triple(0.55f, 0.10f, 0.10f)
        val silver = Triple(0.75f, 0.75f, 0.78f)
        val jacket = Triple(0.15f, 0.30f, 0.65f)
        val skin = Triple(0.85f, 0.62f, 0.45f)
        val helmet = Triple(0.95f, 0.75f, 0.15f)

        // roda (sumbu X)
        m.wheelX(0f, 0.55f, -0.95f, 0.55f, 0.35f, 10, dark.first, dark.second, dark.third)
        m.wheelX(0f, 0.55f, 0.95f, 0.55f, 0.35f, 10, dark.first, dark.second, dark.third)
        // bodi
        m.box(0f, 0.95f, 0.05f, 0.5f, 0.5f, 1.7f, red.first, red.second, red.third)
        m.box(0f, 1.22f, 0.35f, 0.44f, 0.32f, 0.65f, redDark.first, redDark.second, redDark.third) // tangki
        m.box(0f, 1.18f, -0.55f, 0.44f, 0.22f, 0.75f, dark.first, dark.second, dark.third)       // jok
        // garpu depan + setang
        m.box(0f, 1.05f, 1.0f, 0.14f, 1.5f, 0.14f, silver.first, silver.second, silver.third)
        m.box(0f, 1.78f, 1.02f, 0.9f, 0.12f, 0.12f, dark.first, dark.second, dark.third)
        m.box(0f, 1.45f, 1.18f, 0.3f, 0.25f, 0.1f, 1f, 0.95f, 0.7f) // lampu
        // knalpot
        m.box(0.3f, 0.6f, -0.7f, 0.18f, 0.18f, 1.2f, silver.first, silver.second, silver.third)
        // pengendara
        m.box(0f, 1.0f, -0.45f, 0.5f, 0.5f, 0.5f, dark.first, dark.second, dark.third)  // paha
        m.box(0f, 1.62f, -0.25f, 0.52f, 0.7f, 0.42f, jacket.first, jacket.second, jacket.third) // badan
        m.box(-0.32f, 1.6f, 0.35f, 0.14f, 0.14f, 0.9f, jacket.first, jacket.second, jacket.third) // lengan k
        m.box(0.32f, 1.6f, 0.35f, 0.14f, 0.14f, 0.9f, jacket.first, jacket.second, jacket.third)  // lengan k
        m.box(0f, 1.5f, -0.1f, 0.3f, 0.35f, 0.3f, skin.first, skin.second, skin.third)   // leher/kepala
        m.prism(0f, -0.1f, 1.72f, 2.2f, 0.32f, 8, helmet.first, helmet.second, helmet.third) // helm

        return m.build(engine, mi)
    }
}
