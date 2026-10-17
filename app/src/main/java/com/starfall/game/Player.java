package com.starfall.game;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

public class Player {
    public float x, y;
    public final float speed = 420f;      // pixels per second
    public final float radius = 40f;      // hit-box radius
    public int hp = 100, maxHp = 100;
    public int level = 1, xp = 0, xpNeeded = 8;
    public float invuln = 0f;             // seconds of invincibility after being hit

    private final Bitmap[] right, left;
    private float animTime = 0f;
    private boolean facingRight = true;

    public Player(Bitmap[] right, Bitmap[] left, float x, float y) {
        this.right = right;
        this.left = left;
        this.x = x;
        this.y = y;
    }

    public void update(float dt, float dirX, float dirY, float worldW, float worldH) {
        x += dirX * speed * dt;
        y += dirY * speed * dt;
        x = Math.max(radius, Math.min(worldW - radius, x));
        y = Math.max(radius, Math.min(worldH - radius, y));

        if (dirX > 0.1f) facingRight = true;
        else if (dirX < -0.1f) facingRight = false;

        boolean moving = dirX != 0 || dirY != 0;
        animTime = (animTime + dt * (moving ? 1f : 0.4f)) % (right.length * 0.12f);
        if (invuln > 0) invuln -= dt;
    }

    public void draw(Canvas c, float camX, float camY, Paint p) {
        if (invuln > 0 && ((int) (invuln * 20)) % 2 == 0) return;   // blink when hit
        Bitmap b = (facingRight ? right : left)[(int) (animTime / 0.12f)];
        c.drawBitmap(b, x - camX - b.getWidth() / 2f, y - camY - b.getHeight() / 2f, p);
    }

    public void damage(int d) {
        if (invuln > 0) return;
        hp -= d;
        invuln = 0.6f;
    }

    /** @return true if this XP caused a level up */
    public boolean addXp(int amount) {
        xp += amount;
        if (xp >= xpNeeded) {
            xp -= xpNeeded;
            level++;
            xpNeeded = 8 + level * 3;
            hp = Math.min(maxHp, hp + 10);
            return true;
        }
        return false;
    }
}
