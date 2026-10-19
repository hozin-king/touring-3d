package com.hozinking.touring3d;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;

import java.io.ByteArrayOutputStream;

/**
 * Papan tanda jalan: teks digambar via Canvas Android -> PNG -> Pixmap -> Texture.
 * Dipanggil di GL thread (dari Screen.show()).
 */
public class SignFactory {

    public static Texture makeSignTexture(String text) {
        final int w = 512, h = 168;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(Color.rgb(0x1B, 0x24, 0x51));
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.rgb(0xFF, 0xC9, 0x3C));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(10f);
        c.drawRect(10f, 10f, w - 10f, h - 10f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        paint.setTextAlign(Paint.Align.CENTER);
        float ts = 88f;
        paint.setTextSize(ts);
        while (paint.measureText(text) > w - 70 && ts > 24f) {
            ts -= 4f;
            paint.setTextSize(ts);
        }
        c.drawText(text, w / 2f, h / 2f + ts * 0.36f, paint);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
        bmp.recycle();
        byte[] bytes = out.toByteArray();
        Pixmap pixmap = new Pixmap(bytes, 0, bytes.length);
        Texture tex = new Texture(pixmap);
        pixmap.dispose();
        return tex;
    }
}
