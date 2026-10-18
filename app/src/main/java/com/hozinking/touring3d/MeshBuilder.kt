package com.hozinking.touring3d

import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.VertexBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

/**
 * Membangun mesh segitiga berwarna (flat low-poly).
 * Semua geometri statis dunia digabung jadi SATU mesh = 1 draw call.
 */
class MeshBuilder {
    private val pos = ArrayList<Float>()
    private val col = ArrayList<Float>()
    private val idx = ArrayList<Int>()

    fun vertexCount(): Int = pos.size / 3

    private fun v(
        x: Float, y: Float, z: Float,
        r: Float, g: Float, b: Float, a: Float = 1f
    ): Int {
        pos.add(x); pos.add(y); pos.add(z)
        col.add(r); col.add(g); col.add(b); col.add(a)
        return pos.size / 3 - 1
    }

    fun tri(
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float,
        r: Float, g: Float, b: Float
    ) {
        idx.add(v(ax, ay, az, r, g, b))
        idx.add(v(bx, by, bz, r, g, b))
        idx.add(v(cx, cy, cz, r, g, b))
    }

    fun quad(
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz:Float,
        cx: Float, cy: Float, cz: Float,
        dx: Float, dy: Float, dz: Float,
        r: Float, g: Float, b: Float
    ) {
        tri(ax, ay, az, bx, by, bz, cx, cy, cz, r, g, b)
        tri(ax, ay, az, cx, cy, cz, dx, dy, dz, r, g, b)
    }

    /** Kotak: center (cx,cy,cz), ukuran penuh (sx,sy,sz). */
    fun box(
        cx: Float, cy: Float, cz: Float,
        sx: Float, sy: Float, sz: Float,
        r: Float, g: Float, b: Float
    ) {
        val x0 = cx - sx / 2; val x1 = cx + sx / 2
        val y0 = cy - sy / 2; val y1 = cy + sy / 2
        val z0 = cz - sz / 2; val z1 = cz + sz / 2
        // atas & bawah
        quad(x0,y1,z0, x1,y1,z0, x1,y1,z1, x0,y1,z1, r,g,b)
        quad(x0,y0,z0, x0,y0,z1, x1,y0,z1, x1,y0,z0, r,g,b)
        // depan & belakang
        quad(x0,y0,z1, x1,y0,z1, x1,y1,z1, x0,y1,z1, r,g,b)
        quad(x0,y0,z0, x0,y1,z0, x1,y1,z0, x1,y0,z0, r,g,b)
        // kiri & kanan
        quad(x0,y0,z0, x0,y0,z1, x0,y1,z1, x0,y1,z0, r,g,b)
        quad(x1,y0,z0, x1,y1,z0, x1,y1,z1, x1,y0,z1, r,g,b)
    }

    /** Prisma segi-n (silinder kasar): center bawah (cx, cz), dari y0 ke y1. */
    fun prism(
        cx: Float, cz: Float, y0: Float, y1: Float,
        radius: Float, sides: Int,
        r: Float, g: Float, b: Float,
        angleOffset: Float = 0f
    ) {
        for (i in 0 until sides) {
            val a0 = angleOffset + (i.toFloat() / sides) * 2f * Math.PI.toFloat()
            val a1 = angleOffset + ((i + 1).toFloat() / sides) * 2f * Math.PI.toFloat()
            val x0 = cx + cos(a0) * radius; val z0 = cz + sin(a0) * radius
            val x1 = cx + cos(a1) * radius; val z1 = cz + sin(a1) * radius
            quad(x0,y0,z0, x1,y0,z1, x1,y1,z1, x0,y1,z1, r,g,b)
            tri(cx,y1,cz, x1,y1,z1, x0,y1,z0, r,g,b) // tutup atas
        }
    }

    /** Kerucut / piramida: alas di y0, puncak di (cx, y1, cz). */
    fun cone(
        cx: Float, cz: Float, y0: Float, y1: Float,
        radius: Float, sides: Int,
        r: Float, g: Float, b: Float,
        angleOffset: Float = 0f
    ) {
        for (i in 0 until sides) {
            val a0 = angleOffset + (i.toFloat() / sides) * 2f * Math.PI.toFloat()
            val a1 = angleOffset + ((i + 1).toFloat() / sides) * 2f * Math.PI.toFloat()
            val x0 = cx + cos(a0) * radius; val z0 = cz + sin(a0) * radius
            val x1 = cx + cos(a1) * radius; val z1 = cz + sin(a1) * radius
            tri(x0,y0,z0, x1,y0,z1, cx,y1,cz, r,g,b)
        }
    }

    /** Cincin datar (untuk jalan melingkar & ring checkpoint). */
    fun flatRing(
        cx: Float, cz: Float, y: Float,
        rIn: Float, rOut: Float, segs: Int,
        r: Float, g: Float, b: Float
    ) {
        for (i in 0 until segs) {
            val a0 = (i.toFloat() / segs) * 2f * Math.PI.toFloat()
            val a1 = ((i + 1).toFloat() / segs) * 2f * Math.PI.toFloat()
            val ix0 = cx + cos(a0) * rIn; val iz0 = cz + sin(a0) * rIn
            val ix1 = cx + cos(a1) * rIn; val iz1 = cz + sin(a1) * rIn
            val ox0 = cx + cos(a0) * rOut; val oz0 = cz + sin(a0) * rOut
            val ox1 = cx + cos(a1) * rOut; val oz1 = cz + sin(a1) * rOut
            quad(ix0,y,iz0, ox0,y,oz0, ox1,y,oz1, ix1,y,iz1, r,g,b)
        }
    }

    /** Bidang datar persegi. */
    fun groundPlane(size: Float, y: Float, r: Float, g: Float, b: Float) {
        val h = size / 2
        quad(-h,y,-h, h,y,-h, h,y,h, -h,y,h, r,g,b)
    }

    /** Roda: silinder dengan sumbu X (untuk motor yang menghadap +Z). */
    fun wheelX(
        cx: Float, cy: Float, cz: Float,
        r: Float, w: Float, sides: Int,
        cr: Float, cg: Float, cb: Float
    ) {
        val x0 = cx - w / 2; val x1 = cx + w / 2
        for (i in 0 until sides) {
            val a0 = (i.toFloat() / sides) * 2f * Math.PI.toFloat()
            val a1 = ((i + 1).toFloat() / sides) * 2f * Math.PI.toFloat()
            val y0 = cy + cos(a0) * r; val z0 = cz + sin(a0) * r
            val y1 = cy + cos(a1) * r; val z1 = cz + sin(a1) * r
            quad(x0,y0,z0, x0,y1,z1, x1,y1,z1, x1,y0,z0, cr,cg,cb)
        }
    }

    /** Bangun jadi entity Filament. */
    fun build(engine: Engine, mi: MaterialInstance): Int {
        val n = vertexCount()
        val data = ByteBuffer.allocateDirect(n * 7 * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        for (i in 0 until n) {
            data.put(pos[i * 3]); data.put(pos[i * 3 + 1]); data.put(pos[i * 3 + 2])
            data.put(col[i * 4]); data.put(col[i * 4 + 1])
            data.put(col[i * 4 + 2]); data.put(col[i * 4 + 3])
        }
        data.flip()
        val vb = VertexBuffer.Builder()
            .vertexCount(n)
            .bufferCount(1)
            .attribute(
                VertexBuffer.VertexAttribute.POSITION, 0,
                VertexBuffer.AttributeType.FLOAT3, 0, 28
            )
            .attribute(
                VertexBuffer.VertexAttribute.COLOR, 0,
                VertexBuffer.AttributeType.FLOAT4, 12, 28
            )
            .build(engine)
        vb.setBufferAt(engine, 0, data)

        val idata = ByteBuffer.allocateDirect(idx.size * 4)
            .order(ByteOrder.nativeOrder()).asIntBuffer()
        for (i in idx) idata.put(i)
        idata.flip()
        val ib = IndexBuffer.Builder()
            .indexCount(idx.size)
            .bufferType(IndexBuffer.Builder.IndexType.UINT)
            .build(engine)
        ib.setBuffer(engine, idata)

        val e = EntityManager.get().create()
        engine.transformManager.create(e)
        RenderableManager.Builder(1)
            .boundingBox(Box(0f, 0f, 0f, 900f, 120f, 900f))
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vb, ib)
            .material(0, mi)
            .culling(false)
            .build(engine, e)
        return e
    }
}
