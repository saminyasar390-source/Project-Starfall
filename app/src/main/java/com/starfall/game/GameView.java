package com.starfall.game;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.util.Log;
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

    // ================= tuning: change these numbers to rebalance the game =================
    private static final float WAVE_SECONDS = 35f;          // length of one wave
    private static final int BOSS_EVERY = 5;                // boss wave on wave 5, 10, 15 ...
    private static final int BOSS_BASE_HP = 1000;           // first boss; each later boss has +75%
    private static final float BOSS_SCALE = 5f;             // boss sprite size (normal enemies use 3)
    private static final int LEVELS_PER_WEAPON_BUFF = 3;    // weapon upgrade every 3 level ups
    private static final int BULLETS_PER_BUFF = 2;          // bullets added by each upgrade
    private static final int MAX_BULLETS = 9;
    private static final float FIRE_INTERVAL = 0.6f;        // seconds between volleys
    private static final int BULLET_DAMAGE = 12;
    private static final int GOLD_PER_WAVE = 120;           // gold reward = waves survived ...
    private static final int GOLD_PER_BOSS = 400;           // ... plus bosses defeated
    private static final float BULLET_SPREAD = (float) Math.toRadians(15);   // fan angle for extra bullets

    private static final String[] BOSS_FRAMES = {"skel_walk_", "tree_walk_", "mecha_walk_", "malenia_walk_"};
    private static final String[] BOSS_NAMES = {"Skeleton Mage", "The Old Tree", "NX-09 Mecha", "Malenia"};
    // ======================================================================================

    private static final float MAP_SCALE = 3f;      // 1536x1024 map -> 4608x3072 world
    private static final float SPRITE_SCALE = 3f;   // 48px sprite -> 144px on screen
    private static final int PLAYING = 0, GAME_OVER = 1, PAUSED = 2;

    private Thread thread;
    private volatile boolean running;
    private final SurfaceHolder holder;

    // ---- images ----
    private final Bitmap map;
    private final Bitmap[] rinR, rinL, slimeR, slimeL, wolfR, wolfL;
    private final Bitmap[][] bossR = new Bitmap[BOSS_FRAMES.length][];   // loaded when first needed
    private final Bitmap[][] bossL = new Bitmap[BOSS_FRAMES.length][];
    private final float worldW, worldH;

    // ---- game objects ----
    private final Player player;
    private final Joystick joystick = new Joystick();
    private final List<Enemy> enemies = new ArrayList<>();
    private final List<Projectile> shots = new ArrayList<>();
    private final List<Gem> gems = new ArrayList<>();
    private final Random rng = new Random();
    private final SoundManager sounds;
    private final Enemy[] targets = new Enemy[MAX_BULLETS];   // enemies picked for one volley

    // ---- cached HUD text (rebuilt only when the value changes, so no garbage every frame) ----
    private String waveText = "WAVE 1", timerText = "0:35", levelText = "Lv 1";
    private String weaponText = "Bullets x1", weaponFlashText = "", gameOverText;
    private int lastWave = -1, lastTimerSec = -1, lastLevel = -1;

    // ---- state ----
    private volatile int state = PLAYING;    // touched by both the UI thread and the game thread
    private String pauseReason = "";
    private float time = 0f;                 // total time survived
    private int wave = 1;
    private float waveTime = 0f;             // seconds into the current wave
    private float spawnTimer = 1f, fireTimer = 0f;
    private int bossKills = 0, goldEarned = 0;   // for the gold reward at game over
    private String goldText;
    private float damageMult = 1f;               // hero bonus from duplicate pulls
    private int lastTier = 0;                // weapon upgrade level we already announced
    private float levelUpFlash = 0f, weaponFlash = 0f;
    private String bannerText = "";
    private int bannerColor = Color.WHITE;
    private float bannerTime = 0f;
    private float spawnX, spawnY;            // result of pickSpawnPoint()
    private int joyPointerId = -1;

    // ---- reusable drawing objects (avoid creating objects every frame) ----
    private final Paint spritePaint = new Paint();                       // no filter = crisp pixels
    private final Paint mapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);  // smooth painted map
    private final Paint shapePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect src = new Rect();
    private final RectF dstF = new RectF();

    // ---- pause button (top-left) and the buttons of the pause menu ----
    private final RectF pauseBtn = new RectF(), resumeBtn = new RectF(),
            settingsBtn = new RectF(), exitBtn = new RectF();

    public GameView(Context context) {
        super(context);
        holder = getHolder();
        sounds = new SoundManager(context);

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

        // Duplicate pulls from the Convene banner: +1% HP, ATK and SPD per copy (max +20%)
        PlayerData.load(context);
        float bonus = 1f + PlayerData.dupes(PlayerData.selectedHero()) / 100f;
        player.maxHp = Math.round(player.maxHp * bonus);
        player.hp = player.maxHp;
        player.speed *= bonus;
        damageMult = bonus;
        textPaint.setColor(Color.WHITE);
        textPaint.setFakeBoldText(true);
        textPaint.setShadowLayer(4f, 2f, 2f, Color.BLACK);

        showBanner("WAVE 1", Color.WHITE, 2.5f);
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

    // ------------------------------------------------------------ pause (ESC)
    /**
     * Pauses the game. Used by the pause button, the Back key (ESC) and the Activity lifecycle
     * (onPause / losing window focus), so the game is already paused when the player comes back.
     */
    public void pauseGame(boolean byLifecycle) {
        if (state != PLAYING) return;
        pauseReason = byLifecycle
                ? "The app left the foreground, so the game paused itself (onPause)"
                : "Game paused";
        joystick.stop();
        joyPointerId = -1;
        state = PAUSED;
        Log.d("Lifecycle", "GameView paused " + (byLifecycle ? "automatically" : "by the player"));
    }

    public void resumeGame() {
        if (state != PAUSED) return;
        state = PLAYING;
        Log.d("Lifecycle", "GameView resumed by the player");
    }

    /**
     * Back key = ESC: pauses, or resumes if already paused.
     * @return false when the game is over, so the Activity should close itself.
     */
    public boolean handleBack() {
        if (state == PLAYING) { pauseGame(false); return true; }
        if (state == PAUSED) { resumeGame(); return true; }
        return false;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float s = h * 0.09f;
        pauseBtn.set(w * 0.03f, h * 0.04f, w * 0.03f + s, h * 0.04f + s);
        float bw = w * 0.28f, bh = h * 0.12f;
        resumeBtn.set(w / 2f - bw / 2f, h * 0.42f, w / 2f + bw / 2f, h * 0.42f + bh);
        settingsBtn.set(w / 2f - bw / 2f, h * 0.57f, w / 2f + bw / 2f, h * 0.57f + bh);
        exitBtn.set(w / 2f - bw / 2f, h * 0.72f, w / 2f + bw / 2f, h * 0.72f + bh);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        sounds.release();   // free the sound memory when the screen closes
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
                try { drawGame(c); } finally { holder.unlockCanvasAndPost(c); }
            }

            long spentMs = (System.nanoTime() - now) / 1_000_000;
            long frameMs = (state == PAUSED) ? 33 : 16;   // ~30 FPS while paused saves battery
            if (spentMs < frameMs) sleep(frameMs - spentMs);
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
        if (state == PAUSED) {                       // taps on the pause menu
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                if (resumeBtn.contains(e.getX(), e.getY())) {
                    sounds.click();
                    resumeGame();
                } else if (settingsBtn.contains(e.getX(), e.getY())) {
                    sounds.click();
                    // Same Settings screen as the main menu. The game stays paused underneath it.
                    getContext().startActivity(new Intent(getContext(), SettingsActivity.class));
                } else if (exitBtn.contains(e.getX(), e.getY())) {
                    sounds.click();
                    ((Activity) getContext()).finish();      // leave the run, back to the main menu
                }
            }
            return true;
        }
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int i = e.getActionIndex();
            if (pauseBtn.contains(e.getX(i), e.getY(i))) {   // pause button is checked before the joystick
                sounds.click();
                pauseGame(false);
                return true;
            }
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

    // ------------------------------------------------------------ waves
    private static boolean isBossWave(int w) {
        return w % BOSS_EVERY == 0;
    }

    private void startWave(int n) {
        wave = n;
        spawnTimer = 0.5f;
        if (isBossWave(n)) spawnBoss();
        else showBanner("WAVE " + n, Color.WHITE, 2.5f);
    }

    private void showBanner(String text, int color, float seconds) {
        bannerText = text;
        bannerColor = color;
        bannerTime = seconds;
    }

    // ------------------------------------------------------------ weapon
    /** 1 bullet at the start, +2 after every 3 level ups (level 4, 7, 10 ...). */
    private static int bulletsForLevel(int level) {
        int tier = (level - 1) / LEVELS_PER_WEAPON_BUFF;
        return Math.min(MAX_BULLETS, 1 + BULLETS_PER_BUFF * tier);
    }

    /** Fills targets[] with the nearest DIFFERENT enemies (up to 'want') and returns how many were found. */
    private int pickTargets(int want, float maxDist) {
        int found = 0;
        for (int k = 0; k < want; k++) {
            Enemy best = null;
            float bestD = maxDist * maxDist;
            for (Enemy en : enemies) {
                boolean taken = false;
                for (int t = 0; t < found; t++) {
                    if (targets[t] == en) { taken = true; break; }
                }
                if (taken) continue;
                float dx = en.x - player.x, dy = en.y - player.y;
                float d = dx * dx + dy * dy;
                if (d < bestD) { bestD = d; best = en; }
            }
            if (best == null) break;
            targets[found++] = best;
        }
        return found;
    }

    private void shootAngle(float angle) {
        shots.add(new Projectile(player.x, player.y,
                (float) Math.cos(angle), (float) Math.sin(angle), 900f, Math.round(BULLET_DAMAGE * damageMult)));
    }

    /**
     * One volley. Each bullet goes to a different enemy, so with several bullets you hit
     * enemies on different sides. If there are fewer enemies than bullets, the spare bullets
     * fan out around the nearest enemy.
     */
    private void fireVolley() {
        int bullets = bulletsForLevel(player.level);
        int found = pickTargets(bullets, 1000f);
        if (found == 0) return;

        for (int i = 0; i < found; i++) {
            shootAngle((float) Math.atan2(targets[i].y - player.y, targets[i].x - player.x));
        }
        int extra = bullets - found;
        if (extra > 0) {
            float base = (float) Math.atan2(targets[0].y - player.y, targets[0].x - player.x);
            for (int k = 0; k < extra; k++) {
                float side = (k % 2 == 0) ? 1f : -1f;           // alternate left / right of the target
                shootAngle(base + side * (k / 2 + 1) * BULLET_SPREAD);
            }
        }
        sounds.shoot();
        fireTimer = FIRE_INTERVAL;
    }

    // ------------------------------------------------------------ update
    private void update(float dt) {
        if (state != PLAYING) return;
        time += dt;
        waveTime += dt;
        if (waveTime >= WAVE_SECONDS) {
            waveTime -= WAVE_SECONDS;
            startWave(wave + 1);
        }
        if (levelUpFlash > 0) levelUpFlash -= dt;
        if (weaponFlash > 0) weaponFlash -= dt;
        if (bannerTime > 0) bannerTime -= dt;

        player.update(dt, joystick.dirX, joystick.dirY, worldW, worldH);

        // spawn enemies: faster every wave (slower during boss waves so the boss is the star)
        spawnTimer -= dt;
        if (spawnTimer <= 0) {
            float interval = Math.max(0.22f, 1.0f - 0.06f * (wave - 1));
            if (isBossWave(wave)) interval *= 1.5f;
            spawnTimer = interval;
            if (enemies.size() < 250) spawnEnemy();
        }

        // auto-fire
        fireTimer -= dt;
        if (fireTimer <= 0) fireVolley();

        // enemies chase + hurt the player
        for (Enemy en : enemies) {
            en.update(dt, player.x, player.y);
            float dx = player.x - en.x, dy = player.y - en.y;
            float r = player.radius + en.radius;
            if (dx * dx + dy * dy < r * r) {
                int before = player.hp;
                player.damage(en.damage);
                if (player.hp < before) sounds.hurt();   // only when damage really landed
            }
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
                            gems.add(new Gem(en.x, en.y, en.xpValue));
                            if (en.boss) { bossKills++; showBanner("BOSS DEFEATED!", 0xFFFFD54F, 3f); }
                            enemies.remove(j);
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
                if (player.addXp(g.value) > 0) {          // level up (HP is refilled inside addXp)
                    levelUpFlash = 1.5f;
                    sounds.levelUp();
                    int tier = (player.level - 1) / LEVELS_PER_WEAPON_BUFF;
                    if (tier > lastTier) {                // every 3rd level up: weapon buff
                        lastTier = tier;
                        int bullets = bulletsForLevel(player.level);
                        weaponText = "Bullets x" + bullets;
                        weaponFlashText = "WEAPON UP!  x" + bullets + " bullets";
                        weaponFlash = 2.5f;
                    }
                }
                sounds.pickup();
                gems.remove(i);
            }
        }

        if (player.hp <= 0) {
            goldEarned = (wave - 1) * GOLD_PER_WAVE + bossKills * GOLD_PER_BOSS;
            PlayerData.addGold(getContext(), goldEarned);     // spend it on the Convene banner
            state = GAME_OVER;
            sounds.gameOver();
        }
    }

    /** Picks a point just outside the screen, around the player (result in spawnX, spawnY). */
    private void pickSpawnPoint() {
        double angle = rng.nextDouble() * Math.PI * 2;
        float dist = (float) Math.hypot(getWidth(), getHeight()) / 2f + 120f;
        spawnX = clamp(player.x + (float) Math.cos(angle) * dist, 50, worldW - 50);
        spawnY = clamp(player.y + (float) Math.sin(angle) * dist, 50, worldH - 50);
    }

    private void spawnEnemy() {
        pickSpawnPoint();
        float hpScale = 1f + 0.25f * (wave - 1);   // enemies get tougher every wave
        float wolfChance = wave >= 2 ? Math.min(0.5f, 0.15f + 0.07f * (wave - 2)) : 0f;

        if (rng.nextFloat() < wolfChance) {
            enemies.add(new Enemy(wolfR, wolfL, spawnX, spawnY, (int) (30 * hpScale), 260f, 12, 45f, 2, false));
        } else {
            enemies.add(new Enemy(slimeR, slimeL, spawnX, spawnY, (int) (20 * hpScale), 150f, 8, 45f, 1, false));
        }
    }

    private void spawnBoss() {
        int n = wave / BOSS_EVERY;                    // 1 for the first boss, 2 for the second ...
        int idx = (n - 1) % BOSS_FRAMES.length;       // the 4 bosses take turns
        if (bossR[idx] == null) {                     // load the art the first time it is needed
            bossR[idx] = Assets.frames(getContext(), BOSS_FRAMES[idx], 6, BOSS_SCALE);
            bossL[idx] = Assets.flipAll(bossR[idx]);
        }
        pickSpawnPoint();
        int hp = (int) (BOSS_BASE_HP * (1f + 0.75f * (n - 1)));
        enemies.add(new Enemy(bossR[idx], bossL[idx], spawnX, spawnY, hp, 120f, 18, 95f, 15, true));
        showBanner("BOSS WAVE: " + BOSS_NAMES[idx], 0xFFFF5252, 3f);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static boolean onScreen(float sx, float sy, int w, int h, float margin) {
        return sx > -margin && sx < w + margin && sy > -margin && sy < h + margin;
    }

    // ------------------------------------------------------------ draw
    private void drawGame(Canvas c) {
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

        // only draw what is on screen (plus a margin for big sprites)
        final float margin = 300f;
        for (Gem g : gems) {
            if (onScreen(g.x - camX, g.y - camY, w, h, margin)) g.draw(c, camX, camY, shapePaint);
        }
        for (Enemy en : enemies) {
            if (onScreen(en.x - camX, en.y - camY, w, h, margin)) {
                en.draw(c, camX, camY, spritePaint);
                en.drawBossBar(c, camX, camY, shapePaint);
            }
        }
        player.draw(c, camX, camY, spritePaint);
        for (Projectile s : shots) {
            if (onScreen(s.x - camX, s.y - camY, w, h, margin)) s.draw(c, camX, camY, shapePaint);
        }

        joystick.draw(c, shapePaint);
        drawHud(c, w, h);
        if (state == PAUSED) drawPauseMenu(c, w, h);
        if (state == GAME_OVER) drawGameOver(c, w, h);
    }

    private void drawPauseButton(Canvas c) {
        shapePaint.setStyle(Paint.Style.FILL);
        shapePaint.setColor(0x99000000);
        c.drawRoundRect(pauseBtn, 16f, 16f, shapePaint);
        shapePaint.setColor(Color.WHITE);
        float s = pauseBtn.width();
        float barW = s * 0.14f, barH = s * 0.5f;
        float cx = pauseBtn.centerX(), cy = pauseBtn.centerY();
        c.drawRect(cx - barW * 1.5f, cy - barH / 2f, cx - barW * 0.5f, cy + barH / 2f, shapePaint);
        c.drawRect(cx + barW * 0.5f, cy - barH / 2f, cx + barW * 1.5f, cy + barH / 2f, shapePaint);
    }

    private void drawPauseMenu(Canvas c, int w, int h) {
        shapePaint.setStyle(Paint.Style.FILL);
        shapePaint.setColor(0xCC000000);
        c.drawRect(0, 0, w, h, shapePaint);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(h * 0.12f);
        c.drawText("PAUSED", w / 2f, h * 0.26f, textPaint);
        textPaint.setTextSize(h * 0.04f);
        c.drawText(pauseReason, w / 2f, h * 0.34f, textPaint);

        drawMenuButton(c, resumeBtn, "RESUME", h);
        drawMenuButton(c, settingsBtn, "SETTINGS", h);
        drawMenuButton(c, exitBtn, "EXIT", h);
    }

    private void drawMenuButton(Canvas c, RectF r, String label, int h) {
        shapePaint.setStyle(Paint.Style.FILL);
        shapePaint.setColor(0xEE1B1F4B);
        c.drawRoundRect(r, 20f, 20f, shapePaint);
        shapePaint.setStyle(Paint.Style.STROKE);
        shapePaint.setStrokeWidth(4f);
        shapePaint.setColor(0xFFFFD54F);
        c.drawRoundRect(r, 20f, 20f, shapePaint);
        shapePaint.setStyle(Paint.Style.FILL);
        textPaint.setTextSize(h * 0.055f);
        c.drawText(label, r.centerX(), r.centerY() + h * 0.02f, textPaint);
    }

    private void drawHud(Canvas c, int w, int h) {
        float m = h * 0.03f;
        float left = pauseBtn.right + w * 0.015f;   // HP bar starts right of the pause button
        textPaint.setTextAlign(Paint.Align.LEFT);
        shapePaint.setStyle(Paint.Style.FILL);

        // XP bar (top of the screen)
        shapePaint.setColor(0x88000000);
        c.drawRect(0, 0, w, h * 0.02f, shapePaint);
        shapePaint.setColor(0xFF4DD0FF);
        c.drawRect(0, 0, w * player.xp / (float) player.xpNeeded, h * 0.02f, shapePaint);

        drawPauseButton(c);

        // HP bar + level + weapon
        float barW = w * 0.22f, barH = h * 0.035f, top = h * 0.05f;
        shapePaint.setColor(0x88000000);
        c.drawRect(left, top, left + barW, top + barH, shapePaint);
        shapePaint.setColor(0xFFE53935);
        c.drawRect(left, top, left + barW * Math.max(0, player.hp) / (float) player.maxHp, top + barH, shapePaint);
        if (player.level != lastLevel) { lastLevel = player.level; levelText = "Lv " + lastLevel; }
        textPaint.setTextSize(h * 0.05f);
        c.drawText(levelText, left + barW + m, top + barH, textPaint);
        textPaint.setTextSize(h * 0.04f);
        c.drawText(weaponText, left, top + barH + h * 0.06f, textPaint);

        // wave number + countdown (top centre)
        int remaining = Math.max(0, (int) Math.ceil(WAVE_SECONDS - waveTime));
        if (wave != lastWave) {
            lastWave = wave;
            waveText = isBossWave(wave) ? "WAVE " + wave + "  -  BOSS" : "WAVE " + wave;
        }
        if (remaining != lastTimerSec) {
            lastTimerSec = remaining;
            timerText = (remaining / 60) + ":" + (remaining % 60 < 10 ? "0" : "") + (remaining % 60);
        }
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(h * 0.06f);
        textPaint.setColor(isBossWave(wave) ? 0xFFFF5252 : Color.WHITE);
        c.drawText(waveText, w / 2f, h * 0.095f, textPaint);
        textPaint.setTextSize(h * 0.055f);
        textPaint.setColor(remaining <= 5 ? 0xFFFFD54F : Color.WHITE);
        c.drawText(timerText, w / 2f, h * 0.16f, textPaint);
        textPaint.setColor(Color.WHITE);

        // big banner (wave start, boss, boss defeated), fades out at the end
        if (bannerTime > 0) {
            textPaint.setTextSize(h * 0.09f);
            textPaint.setColor(bannerColor);
            textPaint.setAlpha((int) (255 * Math.min(1f, bannerTime / 0.6f)));
            c.drawText(bannerText, w / 2f, h * 0.30f, textPaint);
            textPaint.setColor(Color.WHITE);
        }

        textPaint.setTextSize(h * 0.06f);
        if (levelUpFlash > 0) c.drawText("LEVEL UP!  HP restored", w / 2f, h * 0.38f, textPaint);
        if (weaponFlash > 0) {
            textPaint.setColor(0xFFFFD54F);
            c.drawText(weaponFlashText, w / 2f, h * 0.45f, textPaint);
            textPaint.setColor(Color.WHITE);
        }
    }

    private void drawGameOver(Canvas c, int w, int h) {
        shapePaint.setColor(0xAA000000);
        c.drawRect(0, 0, w, h, shapePaint);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(h * 0.12f);
        c.drawText("GAME OVER", w / 2f, h * 0.42f, textPaint);
        textPaint.setTextSize(h * 0.05f);
        if (gameOverText == null) {   // built once, not every frame
            int sec = (int) time;
            gameOverText = String.format("Reached wave %d   |   Survived %02d:%02d", wave, sec / 60, sec % 60);
        }
        c.drawText(gameOverText, w / 2f, h * 0.55f, textPaint);
        textPaint.setColor(0xFFFFD54F);
        if (goldText == null) goldText = "Gold earned: +" + goldEarned;   // built once
        c.drawText(goldText, w / 2f, h * 0.63f, textPaint);
        textPaint.setColor(Color.WHITE);
        c.drawText("Tap to return to the menu", w / 2f, h * 0.76f, textPaint);
    }
}
