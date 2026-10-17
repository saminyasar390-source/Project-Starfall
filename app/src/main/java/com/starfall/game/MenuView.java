package com.starfall.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * Draws the main-menu artwork (letter-boxed so nothing is cropped) and turns taps on the
 * painted buttons into callbacks. Button areas are stored as fractions of the image.
 */
public class MenuView extends View {

    public interface Listener { void onButton(int index); }

    public static final int PLAY = 0, CONVENE = 1, SHOP = 2, LEADERBOARD = 3, SETTINGS = 4, EXIT = 5;

    // left, top, right, bottom as fractions of the 1536x1024 menu image
    private static final float[][] BUTTONS = {
            {0.768f, 0.314f, 0.945f, 0.373f},   // PLAY
            {0.768f, 0.403f, 0.945f, 0.461f},   // CONVENE BANNER
            {0.768f, 0.499f, 0.945f, 0.558f},   // SHOP
            {0.768f, 0.593f, 0.945f, 0.653f},   // LEADERBOARD
            {0.768f, 0.688f, 0.945f, 0.748f},   // SETTINGS
            {0.768f, 0.787f, 0.945f, 0.846f},   // EXIT
    };

    private final Bitmap menu;
    private final Listener listener;
    private final SoundManager sounds;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF dst = new RectF();

    public MenuView(Context c, Listener l) {
        super(c);
        listener = l;
        sounds = new SoundManager(c);
        menu = Assets.load(c, R.drawable.menu_main);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.BLACK);
        float s = Math.min(getWidth() / (float) menu.getWidth(), getHeight() / (float) menu.getHeight());
        float w = menu.getWidth() * s, h = menu.getHeight() * s;
        dst.set((getWidth() - w) / 2f, (getHeight() - h) / 2f, (getWidth() + w) / 2f, (getHeight() + h) / 2f);
        canvas.drawBitmap(menu, null, dst, paint);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        sounds.release();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP && dst.width() > 0) {
            float fx = (e.getX() - dst.left) / dst.width();
            float fy = (e.getY() - dst.top) / dst.height();
            for (int i = 0; i < BUTTONS.length; i++) {
                float[] b = BUTTONS[i];
                if (fx >= b[0] && fx <= b[2] && fy >= b[1] && fy <= b[3]) {
                    sounds.click();
                    listener.onButton(i);
                    break;
                }
            }
        }
        return true;
    }
}
