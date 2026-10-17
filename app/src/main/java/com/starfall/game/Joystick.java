package com.starfall.game;

import android.graphics.Canvas;
import android.graphics.Paint;

/** Floating virtual joystick: appears wherever the thumb touches the screen. */
public class Joystick {
    public final float radius = 150f;
    public volatile boolean active;
    public volatile float baseX, baseY, knobX, knobY;
    public volatile float dirX, dirY;      // -1..1, read by the game thread

    public void start(float x, float y) {
        baseX = knobX = x;
        baseY = knobY = y;
        dirX = dirY = 0;
        active = true;
    }

    public void move(float x, float y) {
        float dx = x - baseX, dy = y - baseY;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d > radius) { dx = dx / d * radius; dy = dy / d * radius; d = radius; }
        knobX = baseX + dx;
        knobY = baseY + dy;
        if (d < radius * 0.15f) { dirX = dirY = 0; }          // dead zone
        else { dirX = dx / radius; dirY = dy / radius; }
    }

    public void stop() {
        active = false;
        dirX = dirY = 0;
    }

    public void draw(Canvas c, Paint p) {
        if (!active) return;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x33FFFFFF);
        c.drawCircle(baseX, baseY, radius, p);
        p.setColor(0x88FFFFFF);
        c.drawCircle(knobX, knobY, radius * 0.4f, p);
    }
}
