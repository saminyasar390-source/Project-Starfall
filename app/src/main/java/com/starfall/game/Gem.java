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
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF4DD0FF);
        c.save();
        c.rotate(45, sx, sy);
        c.drawRect(sx - 10, sy - 10, sx + 10, sy + 10, p);
        c.restore();
    }
}
