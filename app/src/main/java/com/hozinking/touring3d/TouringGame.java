package com.hozinking.touring3d;

import com.badlogic.gdx.Game;

/** Game libGDX Touring 3D: motor touring keliling pulau, kunjungi 3 spot. */
public class TouringGame extends Game {
    @Override
    public void create() {
        setScreen(new TouringScreen(this));
    }

    @Override
    public void dispose() {
        if (getScreen() != null) getScreen().dispose();
    }
}
