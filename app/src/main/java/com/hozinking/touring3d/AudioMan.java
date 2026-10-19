package com.hozinking.touring3d;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;

/**
 * Manajer audio Touring 3D. Semua aset dari internet berlisensi bebas
 * (lihat layar Kredit dalam game + README):
 * - SFX: Mixkit (mixkit.co), Mixkit License — gratis komersial.
 * - Musik "Carefree": Kevin MacLeod (incompetech.com) — CC BY 4.0.
 */
public class AudioMan {

    private Sound engine;
    private Sound uiClick;
    private Sound crashS;
    private Sound beepS;
    private Sound checkpointS;
    private Music music;

    private long engineId = -1;
    private float crashCd = 0f;

    public void load() {
        engine = Gdx.audio.newSound(Gdx.files.internal("audio/engine_loop.ogg"));
        uiClick = Gdx.audio.newSound(Gdx.files.internal("audio/ui_click.ogg"));
        crashS = Gdx.audio.newSound(Gdx.files.internal("audio/crash.ogg"));
        beepS = Gdx.audio.newSound(Gdx.files.internal("audio/beep.ogg"));
        checkpointS = Gdx.audio.newSound(Gdx.files.internal("audio/checkpoint.ogg"));
        music = Gdx.audio.newMusic(Gdx.files.internal("audio/music.ogg"));
        music.setLooping(true);
        music.setVolume(0.30f);
    }

    // ---- musik latar ----
    public void musicPlay() {
        if (music != null && !music.isPlaying()) music.play();
    }

    /** riding=true -> volume dikecilkan agar mesin terdengar. */
    public void musicRide(boolean riding) {
        if (music != null) music.setVolume(riding ? 0.13f : 0.30f);
    }

    public void musicPause() {
        if (music != null) music.pause();
    }

    // ---- loop mesin: pitch & volume mengikuti kecepatan ----
    public void engineStart() {
        if (engine != null && engineId == -1) engineId = engine.loop(0f);
    }

    /** s01 = 0..1 (kecepatan/max), throttle = gas ditekan. */
    public void engineUpdate(float s01, boolean throttle) {
        if (engineId == -1) return;
        float pitch = 0.72f + s01 * 0.95f + (throttle ? 0.08f : 0f);
        engine.setPitch(engineId, pitch);
        engine.setVolume(engineId, 0.28f + s01 * 0.38f);
    }

    public void engineStop() {
        if (engineId != -1) {
            engine.stop(engineId);
            engineId = -1;
        }
    }

    // ---- sfx ----
    public void click() {
        if (uiClick != null) uiClick.play(0.55f);
    }

    /** pitch 1.0 = normal; untuk countdown 3-2-1-GO. */
    public void beep(float pitch) {
        if (beepS != null) beepS.play(0.7f, pitch, 0f);
    }

    public void checkpoint() {
        if (checkpointS != null) checkpointS.play(0.75f);
    }

    /** Tabrakan: ada cooldown agar tidak spam saat menempel tembok. */
    public void crash(float dt) {
        crashCd -= dt;
        if (crashCd > 0 || crashS == null) return;
        crashCd = 0.45f;
        crashS.play(0.85f, 0.9f + (float) Math.random() * 0.25f, 0f);
    }

    public void dispose() {
        engineStop();
        if (engine != null) engine.dispose();
        if (uiClick != null) uiClick.dispose();
        if (crashS != null) crashS.dispose();
        if (beepS != null) beepS.dispose();
        if (checkpointS != null) checkpointS.dispose();
        if (music != null) music.dispose();
    }
}
