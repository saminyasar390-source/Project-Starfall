package com.starfall.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;

/** Loads images from res/drawable-nodpi without Android resizing them. */
public class Assets {

    public static Bitmap load(Context c, int resId) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;               // keep the original pixels
        return BitmapFactory.decodeResource(c.getResources(), resId, o);
    }

    /** Loads prefix0, prefix1 ... prefix(count-1) and scales them (nearest-neighbour = crisp pixel art). */
    public static Bitmap[] frames(Context c, String prefix, int count, float scale) {
        Bitmap[] out = new Bitmap[count];
        for (int i = 0; i < count; i++) {
            int id = c.getResources().getIdentifier(prefix + i, "drawable", c.getPackageName());
            Bitmap b = load(c, id);
            Bitmap scaled = Bitmap.createScaledBitmap(
                    b, (int) (b.getWidth() * scale), (int) (b.getHeight() * scale), false);
            if (scaled != b) b.recycle();     // free the small original right away
            out[i] = scaled;
        }
        return out;
    }

    public static Bitmap[] flipAll(Bitmap[] src) {
        Bitmap[] out = new Bitmap[src.length];
        Matrix m = new Matrix();
        m.preScale(-1f, 1f);
        for (int i = 0; i < src.length; i++) {
            out[i] = Bitmap.createBitmap(src[i], 0, 0, src[i].getWidth(), src[i].getHeight(), m, false);
        }
        return out;
    }
}
