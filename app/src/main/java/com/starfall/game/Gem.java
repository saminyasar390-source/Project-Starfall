package com.starfall.game;

import android.graphics.Canvas;
import android.graphics.Paint;

/** XP gem dropped by enemies. Gets pulled toward the player when close. */
public class Gem {
    public float x, y;
    public final int value;

    public Gem(float x, float y, int value) {
        this.x = x;
        this.y = y;
        this.value = value;
    }

    public void update(float dt, float px, float py, float magnetRadius) {
        float dx = px - x, dy = py - y;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d < magnetRadius && d > 1f) {
            x += dx / d * 800f * dt;
            y += dy / d * 800f * dt;
        }
    }

    public float distanceTo(float px, float py) {
        float dx = px - x, dy = py - y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    public void draw(Canvas c, float camX, float camY, Paint p) {
        float sx = x - camX, sy = y - camY;
        // bigger value = bigger gem: blue (1), green (2+), gold (boss, 10+)
        float half = value >= 10 ? 22 : value >= 2 ? 14 : 10;
        int color = value >= 10 ? 0xFFFFD54F : value >= 2 ? 0xFF69F0AE : 0xFF4DD0FF;
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        c.save();
        c.rotate(45, sx, sy);
        c.drawRect(sx - half, sy - half, sx + half, sy + half, p);
        c.restore();
    }
}
