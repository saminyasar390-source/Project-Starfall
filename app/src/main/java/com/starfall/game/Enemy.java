package com.starfall.game;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

public class Enemy {
    public float x, y;
    public final float radius = 45f;
    public final float speed;
    public int hp;
    public final int damage;

    private final Bitmap[] right, left;
    private float animTime = 0f;
    private boolean movingRight = true;

    public Enemy(Bitmap[] right, Bitmap[] left, float x, float y, int hp, float speed, int damage) {
        this.right = right;
        this.left = left;
        this.x = x;
        this.y = y;
        this.hp = hp;
        this.speed = speed;
        this.damage = damage;
    }

    /** Walks straight toward the target (the player). */
    public void update(float dt, float tx, float ty) {
        float dx = tx - x, dy = ty - y;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d > 1f) {
            x += dx / d * speed * dt;
            y += dy / d * speed * dt;
        }
        movingRight = dx >= 0;
        animTime = (animTime + dt) % (right.length * 0.12f);
    }

    public void draw(Canvas c, float camX, float camY, Paint p) {
        Bitmap b = (movingRight ? right : left)[(int) (animTime / 0.12f)];
        c.drawBitmap(b, x - camX - b.getWidth() / 2f, y - camY - b.getHeight() / 2f, p);
    }
}
