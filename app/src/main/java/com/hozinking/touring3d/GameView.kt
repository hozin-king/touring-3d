package com.hozinking.touring3d

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
import android.opengl.Matrix
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Scene
import com.google.android.filament.Skybox
import com.google.android.filament.SwapChain
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.VertexBuffer
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Mengelola Filament + loop game Touring 3D.
 * State: MENU -> COUNTDOWN -> RIDING -> FINISHED.
 */
class GameView(
    private val context: Context,
    private val surfaceView: SurfaceView
) {
    enum class State { MENU, COUNTDOWN, RIDING, FINISHED }

    var state = State.MENU
        private set

    var onHud: ((speedKmh: Int, timeStr: String, visited: Int, total: Int) -> Unit)? = null
    var onToast: ((String) -> Unit)? = null
    var onFinish: ((timeStr: String, bestStr: String, isNewBest: Boolean) -> Unit)? = null
    var onCountdown: ((String) -> Unit)? = null
    var onMenuBest: ((String) -> Unit)? = null

    private lateinit var engine: Engine
    private lateinit var renderer: com.google.android.filament.Renderer
    private lateinit var scene: Scene
    private lateinit var view: View
    private lateinit var camera: Camera
    private lateinit var uiHelper: UiHelper
    private var swapChain: SwapChain? = null

    private lateinit var matColor: Material
    private lateinit var matTextured: Material

    private val world = World()
    private val bike = Bike()
    private val visited = BooleanArray(3)
    private val ringEntities = IntArray(4) // 3 spot + 1 start
    private val ringPos = ArrayList<Pair<Float, Float>>()

    private var steer = 0f
    private var gas = false
    private var brake = false
    private var isTrial = false

    private var rideTime = 0f
    private var countdownT = 0f
    private var orbitA = 0f
    private var camX = 0f
    private var camZ = 0f

    private var running = false
    private var lastNanos = 0L
    private val choreographer = Choreographer.getInstance()

    private val prefs by lazy {
        context.getSharedPreferences("touring3d", Context.MODE_PRIVATE)
    }

    // ------------------------------------------------------------------ setup

    fun start() {
        engine = Engine.create()
        renderer = engine.createRenderer()
        scene = engine.createScene()
        view = engine.createView()
        val camEntity = EntityManager.get().create()
        camera = engine.createCamera(camEntity)
        view.camera = camera
        view.scene = scene
        scene.skybox = Skybox.Builder()
            .color(0.52f, 0.80f, 0.96f, 1.0f)
            .build(engine)

        matColor = loadMaterial("unlit_vcolor.filamat")
        matTextured = loadMaterial("unlit_textured.filamat")
        val mi = matColor.createInstance()

        scene.addEntity(world.build(engine, mi))
        bike.reset(world.startX, world.startZ, world.startHeading)
        bikeEntityCached = bike.buildMesh(engine, mi)
        scene.addEntity(bikeEntityCached)

        // ring checkpoint: 3 spot (amber) + start (putih)
        for (s in world.spots) {
            ringPos.add(s.x to s.z)
        }
        ringPos.add(world.startX to world.startZ)
        for (i in ringPos.indices) {
            val (rx, rz) = ringPos[i]
            val c = if (i < 3) Triple(1f, 0.72f, 0.15f) else Triple(0.95f, 0.95f, 0.95f)
            ringEntities[i] = world.buildRing(engine, mi, rx, rz, c.first, c.second, c.third)
            scene.addEntity(ringEntities[i])
        }

        buildSigns()

        camX = world.startX - sin(world.startHeading) * 8f
        camZ = world.startZ - cos(world.startHeading) * 8f

        uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = engine.createSwapChain(surface)
            }

            override fun onDetachedFromSurface() {
                swapChain?.let { engine.destroySwapChain(it); swapChain = null }
            }

            override fun onResized(width: Int, height: Int) {
                val aspect = width.toDouble() / height.toDouble()
                camera.setProjection(45.0, aspect, 0.1, 900.0, Camera.Fov.VERTICAL)
                view.viewport = Viewport(0, 0, width, height)
            }
        }
        uiHelper.attachTo(surfaceView)

        showMenuBest()
        running = true
        choreographer.postFrameCallback(frameCallback)
    }

    private fun loadMaterial(assetName: String): Material {
        context.assets.open("materials/$assetName").use { ins ->
            val bytes = ins.readBytes()
            val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
            buf.put(bytes)
            buf.flip()
            return Material.Builder().payload(buf, buf.remaining()).build(engine)
        }
    }

    /** Papan nama spot: teks digambar via Canvas -> texture Filament. */
    private fun buildSigns() {
        data class Sign(val text: String, val x: Float, val z: Float)
        val signs = listOf(
            Sign("POM", 103f, 0f),
            Sign("WARUNG", -51f, 89f),
            Sign("VIEW POINT", -52f, -91f),
            Sign("START", world.startX + 7f, world.startZ)
        )
        for (s in signs) {
            // tiang
            val pole = MeshBuilder()
            pole.box(s.x, 2f, s.z, 0.4f, 4f, 0.4f, 0.35f, 0.35f, 0.38f)
            scene.addEntity(pole.build(engine, matColor.createInstance()))
            // papan menghadap jalan (ke arah pusat)
            val ang = atan2(-s.x, -s.z)
            val tex = makeSignTexture(s.text)
            scene.addEntity(
                buildBoard(8f, 2.6f, s.x, 5.2f, s.z, Math.toDegrees(ang.toDouble()).toFloat(), tex)
            )
        }
    }

    private fun makeSignTexture(text: String): Texture {
        val w = 512
        val h = 168
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(AColor.rgb(0x1B, 0x24, 0x51))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = AColor.rgb(0xFF, 0xC9, 0x3C)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 10f
        c.drawRect(10f, 10f, w - 10f, h - 10f, paint)
        paint.style = Paint.Style.FILL
        paint.color = AColor.WHITE
        paint.textAlign = Paint.Align.CENTER
        var ts = 88f
        paint.textSize = ts
        while (paint.measureText(text) > w - 70 && ts > 24f) {
            ts -= 4f
            paint.textSize = ts
        }
        c.drawText(text, w / 2f, h / 2f + ts * 0.36f, paint)
        val buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        bmp.copyPixelsToBuffer(buf as java.nio.Buffer)
        buf.flip()
        val tex = Texture.Builder()
            .width(w).height(h).levels(1)
            .format(Texture.InternalFormat.SRGB8_A8)
            .sampler(Texture.Sampler.SAMPLER_2D)
            .build(engine)
        tex.setImage(
            engine, 0,
            Texture.PixelBufferDescriptor(buf, Texture.Format.RGBA, Texture.Type.UBYTE)
        )
        bmp.recycle()
        return tex
    }

    /** Quad ber-texture menghadap +Z lalu diputar. */
    private fun buildBoard(
        w: Float, h: Float, x: Float, y: Float, z: Float,
        rotYDeg: Float, tex: Texture
    ): Int {
        val hw = w / 2
        val hh = h / 2
        val vp = floatArrayOf(
            -hw, hh, 0f, hw, hh, 0f, hw, -hh, 0f, -hw, -hh, 0f
        )
        val uv = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
        val vdata = ByteBuffer.allocateDirect(4 * 5 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        for (i in 0 until 4) {
            vdata.put(vp[i * 3]); vdata.put(vp[i * 3 + 1]); vdata.put(vp[i * 3 + 2])
            vdata.put(uv[i * 2]); vdata.put(uv[i * 2 + 1])
        }
        vdata.flip()
        val vb = VertexBuffer.Builder()
            .vertexCount(4).bufferCount(1)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 20)
            .attribute(VertexBuffer.VertexAttribute.UV0, 0, VertexBuffer.AttributeType.FLOAT2, 12, 20)
            .build(engine)
        vb.setBufferAt(engine, 0, vdata)
        val idata = ByteBuffer.allocateDirect(6 * 4).order(ByteOrder.nativeOrder()).asIntBuffer()
        idata.put(intArrayOf(0, 3, 2, 0, 2, 1)); idata.flip()
        val ib = IndexBuffer.Builder()
            .indexCount(6)
            .bufferType(IndexBuffer.Builder.IndexType.UINT)
            .build(engine)
        ib.setBuffer(engine, idata)

        val mi: MaterialInstance = matTextured.createInstance()
        mi.setParameter(
            "map", tex,
            TextureSampler(
                TextureSampler.MinFilter.LINEAR,
                TextureSampler.MagFilter.LINEAR,
                TextureSampler.WrapMode.CLAMP_TO_EDGE
            )
        )
        val e = EntityManager.get().create()
        RenderableManager.Builder(1)
            .boundingBox(com.google.android.filament.Box(0f, 0f, 0f, 10f, 10f, 10f))
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vb, ib)
            .material(0, mi)
            .culling(false)
            .build(engine, e)
        val m = FloatArray(16)
        Matrix.setIdentityM(m, 0)
        Matrix.translateM(m, 0, x, y, z)
        Matrix.rotateM(m, 0, rotYDeg, 0f, 1f, 0f)
        engine.transformManager.setTransform(engine.transformManager.getInstance(e), m)
        return e
    }

    // ------------------------------------------------------------------ loop

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            choreographer.postFrameCallback(this)
            val dt = if (lastNanos == 0L) 0.016f
            else ((frameTimeNanos - lastNanos) / 1e9).toFloat().coerceAtMost(0.05f)
            lastNanos = frameTimeNanos
            update(dt)
            val sc = swapChain
            if (sc != null && renderer.beginFrame(sc)) {
                renderer.render(view)
                renderer.endFrame()
            }
        }
    }

    private fun update(dt: Float) {
        when (state) {
            State.MENU -> {
                orbitA += dt * 0.08f
                val ex = cos(orbitA) * 170f
                val ez = sin(orbitA) * 170f
                camera.lookAt(ex, 70.0, ez, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0)
            }
            State.COUNTDOWN -> {
                countdownT += dt
                val label = when {
                    countdownT < 1f -> "3"
                    countdownT < 2f -> "2"
                    countdownT < 3f -> "1"
                    else -> "GAS!"
                }
                onCountdown?.invoke(label)
                updateCamera(dt)
                if (countdownT >= 3.4f) {
                    state = State.RIDING
                    onCountdown?.invoke("")
                }
            }
            State.RIDING -> {
                rideTime += dt
                bike.update(dt, steer, gas, brake, world)
                checkSpots()
                onHud?.invoke(bike.speedKmh(), fmtTime(rideTime), visited.count { it }, 3)
                if (visited.all { it }) finish()
                updateCamera(dt)
            }
            State.FINISHED -> {
                updateCamera(dt)
            }
        }
        // transform motor selalu di-update
        if (bikeEntityCached != 0) {
            val tm = engine.transformManager
            tm.setTransform(tm.getInstance(bikeEntityCached), bike.writeMatrix())
        }
    }

    private var bikeEntityCached = 0

    private fun updateCamera(dt: Float) {
        val fx = sin(bike.heading)
        val fz = cos(bike.heading)
        val tx = bike.x - fx * 8f
        val tz = bike.z - fz * 8f
        val k = min(1f, 5f * dt)
        camX += (tx - camX) * k
        camZ += (tz - camZ) * k
        camera.lookAt(
            camX.toDouble(), 3.6, camZ.toDouble(),
            (bike.x + fx * 5f).toDouble(), 1.4, (bike.z + fz * 5f).toDouble(),
            0.0, 1.0, 0.0
        )
    }

    private fun checkSpots() {
        for (i in world.spots.indices) {
            if (visited[i]) continue
            val s = world.spots[i]
            if (hypot(bike.x - s.x, bike.z - s.z) < 9f) {
                visited[i] = true
                // warnai ring jadi hijau
                val (rx, rz) = ringPos[i]
                scene.removeEntity(ringEntities[i])
                engine.destroyEntity(ringEntities[i])
                val mi = matColor.createInstance()
                ringEntities[i] = world.buildRing(engine, mi, rx, rz, 0.2f, 0.85f, 0.3f)
                scene.addEntity(ringEntities[i])
                onToast?.invoke("${s.name} dikunjungi! (${visited.count { it }}/3)")
            }
        }
    }

    private fun finish() {
        state = State.FINISHED
        val key = if (isTrial) "best_trial" else "best_tour"
        val prev = prefs.getFloat(key, -1f)
        val isBest = prev < 0f || rideTime < prev
        if (isBest) prefs.edit().putFloat(key, rideTime).apply()
        val bestStr = fmtTime(if (isBest) rideTime else prev)
        onFinish?.invoke(fmtTime(rideTime), bestStr, isBest)
    }

    // ------------------------------------------------------------------ API

    fun setSteer(x: Float) { steer = x.coerceIn(-1f, 1f) }
    fun setGas(b: Boolean) { gas = b }
    fun setBrake(b: Boolean) { brake = b }

    fun startGame(trial: Boolean) {
        isTrial = trial
        bike.reset(world.startX, world.startZ, world.startHeading)
        visited.fill(false)
        // kembalikan ring ke warna awal
        val mi = matColor.createInstance()
        for (i in ringPos.indices) {
            scene.removeEntity(ringEntities[i])
            engine.destroyEntity(ringEntities[i])
            val (rx, rz) = ringPos[i]
            val c = if (i < 3) Triple(1f, 0.72f, 0.15f) else Triple(0.95f, 0.95f, 0.95f)
            ringEntities[i] = world.buildRing(engine, mi, rx, rz, c.first, c.second, c.third)
            scene.addEntity(ringEntities[i])
        }
        camX = world.startX - sin(world.startHeading) * 8f
        camZ = world.startZ - cos(world.startHeading) * 8f
        rideTime = 0f
        countdownT = 0f
        state = State.COUNTDOWN
        onHud?.invoke(0, fmtTime(0f), 0, 3)
    }

    fun backToMenu() {
        state = State.MENU
        showMenuBest()
    }

    private fun showMenuBest() {
        val bt = prefs.getFloat("best_tour", -1f)
        val br = prefs.getFloat("best_trial", -1f)
        val s = buildString {
            if (bt > 0) append("Tur tercepat: ${fmtTime(bt)}   ")
            if (br > 0) append("Trial tercepat: ${fmtTime(br)}")
        }
        onMenuBest?.invoke(s)
    }

    fun pause() {
        running = false
        choreographer.removeFrameCallback(frameCallback)
    }

    fun resume() {
        if (::engine.isInitialized && !running) {
            running = true
            lastNanos = 0L
            choreographer.postFrameCallback(frameCallback)
        }
    }

    fun destroy() {
        running = false
        choreographer.removeFrameCallback(frameCallback)
        try {
            uiHelper.detach()
            swapChain?.let { engine.destroySwapChain(it) }
            engine.destroy()
        } catch (_: Exception) {
        }
    }

    private fun fmtTime(sec: Float): String {
        val s = sec.toInt()
        return "%02d:%02d".format(s / 60, s % 60)
    }
}
