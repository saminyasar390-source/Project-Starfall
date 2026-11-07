package com.starfall.game;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

public class Enemy {
    public float x, y;
    public final float radius;        // hit-box size
    public final float speed;
    public int hp;
    public final int maxHp;
    public final int damage;          // damage to the player on contact
    public final int xpValue;         // XP of the gem it drops
    public final boolean boss;

    private final Bitmap[] right, left;
    private float animTime = 0f;
    private boolean movingRight = true;

    public Enemy(Bitmap[] right, Bitmap[] left, float x, float y, int hp, float speed,
                 int damage, float radius, int xpValue, boolean boss) {
        this.right = right;
        this.left = left;
        this.x = x;
        this.y = y;
        this.hp = hp;
        this.maxHp = hp;
        this.speed = speed;
        this.damage = damage;
        this.radius = radius;
        this.xpValue = xpValue;
        this.boss = boss;
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

    /** Health bar above a boss. Normal enemies don't get one. */
    public void drawBossBar(Canvas c, float camX, float camY, Paint p) {
        if (!boss) return;
        float barW = radius * 2.4f, barH = 16f;
        float sx = x - camX - barW / 2f;
        float sy = y - camY - right[0].getHeight() / 2f - 30f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xAA000000);
        c.drawRect(sx - 3, sy - 3, sx + barW + 3, sy + barH + 3, p);
        p.setColor(0xFFE53935);
        c.drawRect(sx, sy, sx + barW * Math.max(0, hp) / (float) maxHp, sy + barH, p);
    }
}
