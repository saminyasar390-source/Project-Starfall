package com.starfall.game;

import android.graphics.Canvas;
import android.graphics.Paint;

public class Projectile {
    public float x, y, vx, vy;
    public float life = 1.3f;             // seconds before it disappears
    public final float radius = 16f;
    public final int damage;

    public Projectile(float x, float y, float dirX, float dirY, float speed, int damage) {
        this.x = x;
        this.y = y;
        this.vx = dirX * speed;
        this.vy = dirY * speed;
        this.damage = damage;
    }

    public void update(float dt) {
        x += vx * dt;
        y += vy * dt;
        life -= dt;
    }

    public void draw(Canvas c, float camX, float camY, Paint p) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFB3E5FF);
        c.drawCircle(x - camX, y - camY, radius, p);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(x - camX, y - camY, radius * 0.5f, p);
    }
}
