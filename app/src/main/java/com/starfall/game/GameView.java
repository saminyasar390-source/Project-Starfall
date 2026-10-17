package com.starfall.game;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The whole game lives here: a SurfaceView with its own thread running
 * update() -> draw() about 60 times per second.
 */
public class GameView extends SurfaceView implements Runnable {

    private static final float MAP_SCALE = 3f;      // 1536x1024 map -> 4608x3072 world
    private static final float SPRITE_SCALE = 3f;   // 48px sprite -> 144px on screen
    private static final int PLAYING = 0, GAME_OVER = 1;

    private Thread thread;
    private volatile boolean running;
    private final SurfaceHolder holder;

    // ---- images ----
    private final Bitmap map;
    private final Bitmap[] rinR, rinL, slimeR, slimeL, wolfR, wolfL;
    private final float worldW, worldH;

    // ---- game objects ----
    private final Player player;
    private final Joystick joystick = new Joystick();
    private final List<Enemy> enemies = new ArrayList<>();
    private final List<Projectile> shots = new ArrayList<>();
    private final List<Gem> gems = new ArrayList<>();
    private final Random rng = new Random();

    // ---- state ----
    private int state = PLAYING;
    private float time = 0f, spawnTimer = 1f, fireTimer = 0f, levelUpFlash = 0f;
    private int kills = 0;
    private int joyPointerId = -1;

    // ---- reusable drawing objects (avoid creating objects every frame) ----
    private final Paint spritePaint = new Paint();                       // no filter = crisp pixels
    private final Paint mapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);  // smooth painted map
    private final Paint shapePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect src = new Rect();
    private final RectF dstF = new RectF();

    public GameView(Context context) {
        super(context);
        holder = getHolder();

        map = Assets.load(context, R.drawable.map_forest);   // try map_cyberpunk / map_kingdom too
        worldW = map.getWidth() * MAP_SCALE;
        worldH = map.getHeight() * MAP_SCALE;

        rinR = Assets.frames(context, "rin_walk_", 6, SPRITE_SCALE);
        rinL = Assets.flipAll(rinR);
        slimeR = Assets.frames(context, "slime_walk_", 6, SPRITE_SCALE);
        slimeL = Assets.flipAll(slimeR);
        wolfL = Assets.frames(context, "wolf_walk_", 6, SPRITE_SCALE);   // wolf art faces left
        wolfR = Assets.flipAll(wolfL);

        player = new Player(rinR, rinL, worldW / 2f, worldH / 2f);
        textPaint.setColor(Color.WHITE);
        textPaint.setFakeBoldText(true);
    }

    // ------------------------------------------------------------ thread control
    public void resume() {
        running = true;
        thread = new Thread(this);
        thread.start();
    }

    public void pause() {
        running = false;
        try { if (thread != null) thread.join(); } catch (InterruptedException ignored) { }
    }

    @Override
    public void run() {
        long last = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            float dt = Math.min((now - last) / 1_000_000_000f, 0.05f);
            last = now;

            if (!holder.getSurface().isValid() || getWidth() == 0) { sleep(16); continue; }

            update(dt);

            Canvas c = Build.VERSION.SDK_INT >= 26 ? holder.lockHardwareCanvas() : holder.lockCanvas();
            if (c != null) {
                try { draw(c); } finally { holder.unlockCanvasAndPost(c); }
            }

            long spentMs = (System.nanoTime() - now) / 1_000_000;
            if (spentMs < 16) sleep(16 - spentMs);   // cap at ~60 FPS
        }
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }

    // ------------------------------------------------------------ input
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (state == GAME_OVER) {
            if (e.getActionMasked() == MotionEvent.ACTION_UP) ((Activity) getContext()).finish();
            return true;
        }
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                if (joyPointerId == -1) {
                    int i = e.getActionIndex();
                    joyPointerId = e.getPointerId(i);
                    joystick.start(e.getX(i), e.getY(i));
                }
                break;
            case MotionEvent.ACTION_MOVE:
                int idx = e.findPointerIndex(joyPointerId);
                if (idx >= 0) joystick.move(e.getX(idx), e.getY(idx));
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                if (e.getActionMasked() == MotionEvent.ACTION_CANCEL
                        || e.getPointerId(e.getActionIndex()) == joyPointerId) {
                    joystick.stop();
                    joyPointerId = -1;
                }
                break;
        }
        return true;
    }

    // ------------------------------------------------------------ update
    private void update(float dt) {
        if (state != PLAYING) return;
        time += dt;
        if (levelUpFlash > 0) levelUpFlash -= dt;

        player.update(dt, joystick.dirX, joystick.dirY, worldW, worldH);

        // spawn enemies faster as time passes
        spawnTimer -= dt;
        if (spawnTimer <= 0) {
            spawnTimer = Math.max(0.25f, 1.0f - time * 0.008f);
            if (enemies.size() < 250) spawnEnemy();
        }

        // auto-fire at the nearest enemy
        fireTimer -= dt;
        if (fireTimer <= 0) {
            Enemy target = nearestEnemy(1000f);
            if (target != null) {
                float dx = target.x - player.x, dy = target.y - player.y;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                shots.add(new Projectile(player.x, player.y, dx / d, dy / d, 900f, 12));
                fireTimer = 0.6f;
            }
        }

        // enemies chase + hurt the player
        for (Enemy en : enemies) {
            en.update(dt, player.x, player.y);
            float dx = player.x - en.x, dy = player.y - en.y;
            float r = player.radius + en.radius;
            if (dx * dx + dy * dy < r * r) player.damage(en.damage);
        }

        // projectiles hit enemies
        for (int i = shots.size() - 1; i >= 0; i--) {
            Projectile s = shots.get(i);
            s.update(dt);
            boolean remove = s.life <= 0;
            if (!remove) {
                for (int j = enemies.size() - 1; j >= 0; j--) {
                    Enemy en = enemies.get(j);
                    float dx = en.x - s.x, dy = en.y - s.y;
                    float r = en.radius + s.radius;
                    if (dx * dx + dy * dy < r * r) {
                        en.hp -= s.damage;
                        remove = true;
                        if (en.hp <= 0) {
                            gems.add(new Gem(en.x, en.y, 1));
                            enemies.remove(j);
                            kills++;
                        }
                        break;
                    }
                }
            }
            if (remove) shots.remove(i);
        }

        // gems: magnet + pickup
        for (int i = gems.size() - 1; i >= 0; i--) {
            Gem g = gems.get(i);
            g.update(dt, player.x, player.y, 220f);
            if (g.distanceTo(player.x, player.y) < 50f) {
                if (player.addXp(g.value)) {
                    levelUpFlash = 1.2f;   // TODO: open the upgrade-choice screen here
                }
                gems.remove(i);
            }
        }

        if (player.hp <= 0) state = GAME_OVER;
    }

    private Enemy nearestEnemy(float maxDist) {
        Enemy best = null;
        float bestD = maxDist * maxDist;
        for (Enemy en : enemies) {
            float dx = en.x - player.x, dy = en.y - player.y;
            float d = dx * dx + dy * dy;
            if (d < bestD) { bestD = d; best = en; }
        }
        return best;
    }

    private void spawnEnemy() {
        double angle = rng.nextDouble() * Math.PI * 2;
        float dist = (float) Math.hypot(getWidth(), getHeight()) / 2f + 120f;
        float ex = clamp(player.x + (float) Math.cos(angle) * dist, 50, worldW - 50);
        float ey = clamp(player.y + (float) Math.sin(angle) * dist, 50, worldH - 50);
        float scale = 1f + time / 120f;     // enemies get tougher over time

        if (time > 20f && rng.nextFloat() < 0.35f) {
            enemies.add(new Enemy(wolfR, wolfL, ex, ey, (int) (30 * scale), 260f, 12));
        } else {
            enemies.add(new Enemy(slimeR, slimeL, ex, ey, (int) (20 * scale), 150f, 8));
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ------------------------------------------------------------ draw
    private void draw(Canvas c) {
        int w = getWidth(), h = getHeight();
        float camX = clamp(player.x - w / 2f, 0, Math.max(0, worldW - w));
        float camY = clamp(player.y - h / 2f, 0, Math.max(0, worldH - h));

        // map (only the visible part of the picture is drawn)
        src.set((int) (camX / MAP_SCALE), (int) (camY / MAP_SCALE),
                Math.min(map.getWidth(), (int) Math.ceil((camX + w) / MAP_SCALE)),
                Math.min(map.getHeight(), (int) Math.ceil((camY + h) / MAP_SCALE)));
        dstF.set(src.left * MAP_SCALE - camX, src.top * MAP_SCALE - camY,
                src.right * MAP_SCALE - camX, src.bottom * MAP_SCALE - camY);
        c.drawColor(Color.BLACK);
        c.drawBitmap(map, src, dstF, mapPaint);

        for (Gem g : gems) g.draw(c, camX, camY, shapePaint);
        for (Enemy en : enemies) en.draw(c, camX, camY, spritePaint);
        player.draw(c, camX, camY, spritePaint);
        for (Projectile s : shots) s.draw(c, camX, camY, shapePaint);

        joystick.draw(c, shapePaint);
        drawHud(c, w, h);
        if (state == GAME_OVER) drawGameOver(c, w, h);
    }

    private void drawHud(Canvas c, int w, int h) {
        float m = h * 0.03f;
        float left = w * 0.04f;
        textPaint.setTextSize(h * 0.05f);
        textPaint.setTextAlign(Paint.Align.LEFT);
        shapePaint.setStyle(Paint.Style.FILL);

        // XP bar (top of the screen)
        shapePaint.setColor(0x88000000);
        c.drawRect(0, 0, w, h * 0.02f, shapePaint);
        shapePaint.setColor(0xFF4DD0FF);
        c.drawRect(0, 0, w * player.xp / (float) player.xpNeeded, h * 0.02f, shapePaint);

        // HP bar
        float barW = w * 0.22f, barH = h * 0.035f, top = h * 0.05f;
        shapePaint.setColor(0x88000000);
        c.drawRect(left, top, left + barW, top + barH, shapePaint);
        shapePaint.setColor(0xFFE53935);
        c.drawRect(left, top, left + barW * Math.max(0, player.hp) / (float) player.maxHp, top + barH, shapePaint);
        c.drawText("Lv " + player.level, left + barW + m, top + barH, textPaint);

        // timer + kills (top right)
        textPaint.setTextAlign(Paint.Align.RIGHT);
        int sec = (int) time;
        c.drawText(String.format("%02d:%02d", sec / 60, sec % 60), w - left, top + barH, textPaint);
        c.drawText("Kills " + kills, w - left, top + barH + h * 0.06f, textPaint);

        if (levelUpFlash > 0) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            c.drawText("LEVEL UP!", w / 2f, h * 0.2f, textPaint);
        }
    }

    private void drawGameOver(Canvas c, int w, int h) {
        shapePaint.setColor(0xAA000000);
        c.drawRect(0, 0, w, h, shapePaint);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(h * 0.12f);
        c.drawText("GAME OVER", w / 2f, h * 0.42f, textPaint);
        textPaint.setTextSize(h * 0.05f);
        int sec = (int) time;
        c.drawText(String.format("Survived %02d:%02d   |   Kills %d", sec / 60, sec % 60, kills),
                w / 2f, h * 0.55f, textPaint);
        c.drawText("Tap to return to the menu", w / 2f, h * 0.68f, textPaint);
    }
}
