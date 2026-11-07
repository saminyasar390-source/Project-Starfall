package com.starfall.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;
import java.util.Random;

/**
 * The Convene (recruitment) banner screen.
 *
 * The banner artwork is a single picture, so the screen draws it letter-boxed and puts the live parts
 * on top: the gold amount, a BACK button, tap zones over 1x PULL / 10x PULL / VIEW RATES, the pull
 * animation, the result cards and the rates window.
 */
public class ConveneView extends View {

    public interface Listener { void onExit(); }

    // ---- rules shown on the banner ----
    private static final int COST_ONE = 1000, COST_TEN = 10000;

    // ---- screen states ----
    private static final int IDLE = 0, SUMMON = 1, RESULT = 2, RATES = 3;
    private static final float SUMMON_SECONDS = 1.6f;

    // ---- tap zones: left, top, right, bottom as fractions of the banner image ----
    private static final int Z_BACK = 0, Z_ONE = 1, Z_TEN = 2, Z_RATES = 3;
    private static final float[][] ZONES = {
            {0.1408f, 0.0440f, 0.2266f, 0.0952f},   // BACK (added by us, next to the SHOP title)
            {0.3942f, 0.7668f, 0.6105f, 0.9304f},   // 1x PULL
            {0.6298f, 0.7668f, 0.8427f, 0.9304f},   // 10x PULL
            {0.8640f, 0.8547f, 0.9897f, 0.9243f},   // VIEW RATES
    };
    // the dark gold bar (inside its border) where the painted "15,335" is replaced by the real amount
    private static final float[] GOLD_BAR = {0.7541f, 0.0415f, 0.9725f, 0.0879f};
    private static final float GOLD_RIGHT = 0.9650f, GOLD_CENTER_Y = 0.0647f;

    private final Listener listener;
    private final SoundManager sounds;
    private final Random rng = new Random();

    private final Bitmap banner, backdrop;
    private final Bitmap[][] heroFrames = new Bitmap[PlayerData.HERO_COUNT][];
    private final int barColor;

    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint spritePaint = new Paint();                       // no filter = crisp pixel art
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dst = new RectF(), r = new RectF(), r2 = new RectF();

    private int state = IDLE;
    private long stateStart = SystemClock.uptimeMillis();
    private int pressed = -1;
    private int goldShown = -1;
    private String goldText = "";
    private String message;
    private long messageUntil;

    // ---- results of the last pull ----
    private int resultCount;
    private final int[] resHero = new int[10];
    private final String[] resTag = new String[10];       // short label on a card: NEW / +1% / MAX
    private final int[] resTagColor = new int[10];
    private String resultLine1 = "", resultLine2 = "", summaryText = "";
    private int resultColor = Color.WHITE;

    public ConveneView(Context c, Listener l) {
        super(c);
        listener = l;
        PlayerData.load(c);
        sounds = new SoundManager(c);

        banner = Assets.load(c, R.drawable.convene_banner);
        backdrop = Bitmap.createScaledBitmap(banner, 24, 14, true);   // tiny copy, stretched = soft background
        barColor = banner.getPixel((int) (banner.getWidth() * 0.769f), (int) (banner.getHeight() * 0.0635f));

        for (int h = 0; h < PlayerData.HERO_COUNT; h++) {
            heroFrames[h] = new Bitmap[6];
            for (int f = 0; f < 6; f++) {
                int id = c.getResources().getIdentifier(Heroes.FRAMES[h] + f, "drawable", c.getPackageName());
                heroFrames[h][f] = Assets.load(c, id);
            }
        }

        text.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        text.setShadowLayer(3f, 2f, 2f, 0xAA000000);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        glow.setShader(new RadialGradient(0, 0, 100,
                new int[]{0xFFFFFFFF, 0xAAFFE9A8, 0x55B58CFF, 0x00B58CFF},
                new float[]{0f, 0.25f, 0.6f, 1f}, Shader.TileMode.CLAMP));
    }

    // =====================================================================================
    //  Pulling
    // =====================================================================================

    private void doPull(int count, int cost) {
        if (!PlayerData.trySpend(getContext(), cost)) {
            showMessage("Not enough gold!");
            sounds.hurt();
            return;
        }
        resultCount = count;
        int newOnes = 0, upgrades = 0;
        for (int i = 0; i < count; i++) {
            int hero = rng.nextInt(PlayerData.HERO_COUNT);        // every hero has the same chance
            int code = PlayerData.addCopy(getContext(), hero);
            int dupes = PlayerData.dupes(hero);
            resHero[i] = hero;
            if (code == PlayerData.NEW) {
                resTag[i] = "NEW!";
                resTagColor[i] = 0xFFFFD54F;
                newOnes++;
            } else if (code == PlayerData.DUPLICATE) {
                resTag[i] = "+" + dupes + "%";
                resTagColor[i] = 0xFF7FE3FF;
                upgrades++;
            } else {
                resTag[i] = "MAX";
                resTagColor[i] = 0xFFB0B8D0;
            }
            if (count == 1) {                                       // detailed text for the single result
                if (code == PlayerData.NEW) {
                    resultLine1 = "NEW HERO UNLOCKED!";
                    resultLine2 = "Added to your collection";
                    resultColor = 0xFFFFD54F;
                } else if (code == PlayerData.DUPLICATE) {
                    resultLine1 = "Duplicate!  HP / ATK / SPD +1%";
                    resultLine2 = "Bonus: +" + dupes + "% of +" + PlayerData.MAX_DUPES + "%";
                    resultColor = 0xFF7FE3FF;
                } else {
                    resultLine1 = "MAX BONUS REACHED";
                    resultLine2 = "This hero is already at +" + PlayerData.MAX_DUPES + "%";
                    resultColor = 0xFFB0B8D0;
                }
            }
        }
        summaryText = newOnes + " new hero" + (newOnes == 1 ? "" : "es") + "   |   "
                + upgrades + " bonus upgrade" + (upgrades == 1 ? "" : "s");
        sounds.click();
        setState(SUMMON);
    }

    private void showMessage(String m) {
        message = m;
        messageUntil = SystemClock.uptimeMillis() + 1800;
        invalidate();
    }

    private void setState(int s) {
        state = s;
        stateStart = SystemClock.uptimeMillis();
        invalidate();
    }

    private float elapsed() {
        return (SystemClock.uptimeMillis() - stateStart) / 1000f;
    }

    // =====================================================================================
    //  Input
    // =====================================================================================

    /** Back key. @return true if it closed a window, false if the screen itself should close. */
    public boolean handleBack() {
        if (state == IDLE) return false;
        tapOverlay(true);
        return true;
    }

    private void tapOverlay(boolean force) {
        float t = elapsed();
        if (state == SUMMON) {
            if (force || t > 0.5f) { setState(RESULT); sounds.levelUp(); }
        } else if (state == RESULT) {
            if (force || t > 0.45f) setState(IDLE);
        } else if (state == RATES) {
            setState(IDLE);
        }
    }

    private int zoneAt(float x, float y) {
        if (dst.width() <= 0) return -1;
        float fx = (x - dst.left) / dst.width(), fy = (y - dst.top) / dst.height();
        for (int i = 0; i < ZONES.length; i++) {
            float[] z = ZONES[i];
            if (fx >= z[0] && fx <= z[2] && fy >= z[1] && fy <= z[3]) return i;
        }
        return -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (state != IDLE) {
            if (a == MotionEvent.ACTION_UP) tapOverlay(false);
            return true;
        }
        switch (a) {
            case MotionEvent.ACTION_DOWN:
                pressed = zoneAt(e.getX(), e.getY());
                invalidate();
                break;
            case MotionEvent.ACTION_MOVE:
                if (pressed >= 0 && zoneAt(e.getX(), e.getY()) != pressed) { pressed = -1; invalidate(); }
                break;
            case MotionEvent.ACTION_UP:
                int z = zoneAt(e.getX(), e.getY());
                int was = pressed;
                pressed = -1;
                invalidate();
                if (z >= 0 && z == was) activate(z);
                break;
            case MotionEvent.ACTION_CANCEL:
                pressed = -1;
                invalidate();
                break;
        }
        return true;
    }

    private void activate(int zone) {
        if (zone == Z_BACK) { sounds.click(); listener.onExit(); }
        else if (zone == Z_ONE) doPull(1, COST_ONE);
        else if (zone == Z_TEN) doPull(10, COST_TEN);
        else if (zone == Z_RATES) { sounds.click(); setState(RATES); }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        sounds.release();
    }

    // =====================================================================================
    //  Drawing
    // =====================================================================================

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();

        if (state == SUMMON && elapsed() >= SUMMON_SECONDS) {      // animation finished: show the result
            setState(RESULT);
            sounds.levelUp();
        }

        // soft backdrop (a tiny copy of the banner stretched over the whole screen) fills the side bars
        r.set(0, 0, w, h);
        c.drawBitmap(backdrop, null, r, bmpPaint);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0x55000000);
        c.drawRect(r, fill);

        // the banner, letter-boxed
        float s = Math.min(w / (float) banner.getWidth(), h / (float) banner.getHeight());
        float bw = banner.getWidth() * s, bh = banner.getHeight() * s;
        dst.set((w - bw) / 2f, (h - bh) / 2f, (w + bw) / 2f, (h + bh) / 2f);
        c.drawBitmap(banner, null, dst, bmpPaint);

        drawGold(c);
        drawBackButton(c);
        drawPullStates(c);
        drawMessage(c, w, h);

        float t = elapsed();
        if (state == SUMMON) drawSummon(c, w, h, t);
        else if (state == RESULT) drawResult(c, w, h, t);
        else if (state == RATES) drawRates(c, w, h);

        if (state != IDLE || message != null) postInvalidateOnAnimation();   // idle screen costs no battery
    }

    /** Maps a zone (fractions of the banner) onto the screen rectangle r. */
    private void zoneRect(float[] z, RectF out) {
        out.set(dst.left + z[0] * dst.width(), dst.top + z[1] * dst.height(),
                dst.left + z[2] * dst.width(), dst.top + z[3] * dst.height());
    }

    private void drawGold(Canvas c) {
        zoneRect(GOLD_BAR, r);
        fill.setColor(barColor);
        c.drawRect(r, fill);                                         // cover the painted number

        int gold = PlayerData.gold();
        if (gold != goldShown) {
            goldShown = gold;
            goldText = String.format(Locale.US, "%,d", gold);
        }
        float u = dst.width() / 1456f;
        text.setTextAlign(Paint.Align.RIGHT);
        text.setTextSize(40f * u);
        text.setColor(Color.WHITE);
        c.drawText(goldText, dst.left + GOLD_RIGHT * dst.width(),
                dst.top + GOLD_CENTER_Y * dst.height() + 14f * u, text);
    }

    private void drawBackButton(Canvas c) {
        zoneRect(ZONES[Z_BACK], r);
        float u = dst.width() / 1456f;
        fill.setColor(0xDD0B1E44);
        c.drawRoundRect(r, 14f * u, 14f * u, fill);
        line.setColor(0xFF7FE3FF);
        line.setStrokeWidth(3f * u);
        c.drawRoundRect(r, 14f * u, 14f * u, line);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(26f * u);
        text.setColor(Color.WHITE);
        c.drawText("< BACK", r.centerX(), r.centerY() + 9f * u, text);
        if (pressed == Z_BACK) { fill.setColor(0x44FFFFFF); c.drawRoundRect(r, 14f * u, 14f * u, fill); }
    }

    /** Dims a pull button when the player can't afford it, and lights it up while it is pressed. */
    private void drawPullStates(Canvas c) {
        float u = dst.width() / 1456f;
        int gold = PlayerData.gold();
        for (int z = Z_ONE; z <= Z_RATES; z++) {
            zoneRect(ZONES[z], r);
            if ((z == Z_ONE && gold < COST_ONE) || (z == Z_TEN && gold < COST_TEN)) {
                fill.setColor(0x77000000);
                c.drawRoundRect(r, 18f * u, 18f * u, fill);
            }
            if (pressed == z) {
                fill.setColor(0x44FFFFFF);
                c.drawRoundRect(r, 18f * u, 18f * u, fill);
            }
        }
    }

    private void drawMessage(Canvas c, int w, int h) {
        if (message == null) return;
        if (SystemClock.uptimeMillis() > messageUntil) { message = null; return; }
        float u = dst.width() / 1456f;
        float cx = dst.left + dst.width() * 0.5f, cy = dst.top + dst.height() * 0.66f;
        float tw = 22f * u * message.length() * 0.62f + 60f * u;
        r.set(cx - tw / 2f, cy - 30f * u, cx + tw / 2f, cy + 30f * u);
        fill.setColor(0xEE8E1B2C);
        c.drawRoundRect(r, 18f * u, 18f * u, fill);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(30f * u);
        text.setColor(Color.WHITE);
        c.drawText(message, cx, cy + 10f * u, text);
    }

    // ------------------------------------------------------------ summon animation
    private void drawSummon(Canvas c, int w, int h, float t) {
        float p = Math.min(1f, t / SUMMON_SECONDS);
        fill.setColor(Color.argb((int) (235 * Math.min(1f, t / 0.35f)), 2, 6, 24));
        c.drawRect(0, 0, w, h, fill);

        float cx = w / 2f, cy = h * 0.46f;
        float rad = h * (0.12f + 0.62f * p * p);
        glow.setAlpha((int) (255 * Math.min(1f, p * 2.2f)));
        c.save();
        c.translate(cx, cy);
        c.scale(rad / 100f, rad / 100f);
        c.drawCircle(0, 0, 100, glow);
        c.restore();

        // spinning rays
        line.setStrokeWidth(h * 0.012f);
        line.setColor(Color.argb((int) (200 * Math.min(1f, p * 3f)), 255, 233, 168));
        c.save();
        c.translate(cx, cy);
        c.rotate(t * 110f);
        for (int k = 0; k < 12; k++) {
            c.rotate(30f);
            c.drawLine(rad * 0.35f, 0, rad * (0.9f + 0.7f * p), 0, line);
        }
        c.restore();

        if (p > 0.75f) {                                           // white flash right before the reveal
            fill.setColor(Color.argb((int) (255 * (p - 0.75f) / 0.25f), 255, 255, 255));
            c.drawRect(0, 0, w, h, fill);
        }
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(h * 0.05f);
        text.setColor(Color.argb((int) (255 * Math.min(1f, t / 0.4f)), 255, 255, 255));
        c.drawText("Summoning...", cx, h * 0.90f, text);
    }

    // ------------------------------------------------------------ result
    private static float easeOutBack(float x) {
        x = Math.max(0f, Math.min(1f, x));
        float c1 = 1.70158f, c3 = c1 + 1f;
        return 1f + c3 * (float) Math.pow(x - 1f, 3) + c1 * (float) Math.pow(x - 1f, 2);
    }

    private Bitmap frame(int hero) {
        return heroFrames[hero][(int) ((SystemClock.uptimeMillis() / 120) % 6)];
    }

    private void drawResult(Canvas c, int w, int h, float t) {
        fill.setColor(0xF0050818);
        c.drawRect(0, 0, w, h, fill);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(0xFFB8C7FF);
        text.setTextSize(h * 0.055f);
        c.drawText("RECRUITMENT", w / 2f, h * 0.10f, text);

        if (resultCount == 1) drawSingle(c, w, h, t);
        else drawGrid(c, w, h, t);

        if (t > 0.6f) {
            text.setTextSize(h * 0.04f);
            text.setColor(Color.argb((int) (140 + 115 * Math.sin(t * 4f)), 255, 255, 255));
            c.drawText("Tap to continue", w / 2f, h * 0.96f, text);
        }
    }

    private void drawSingle(Canvas c, int w, int h, float t) {
        int hero = resHero[0];
        float pop = easeOutBack(t / 0.45f);
        float cx = w / 2f, cy = h * 0.40f;

        float rad = h * 0.42f * (0.85f + 0.15f * pop);               // glow behind the hero
        glow.setAlpha(230);
        c.save();
        c.translate(cx, cy);
        c.scale(rad / 100f, rad / 100f);
        c.drawCircle(0, 0, 100, glow);
        c.restore();

        float size = h * 0.46f * pop;
        r.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f);
        c.drawBitmap(frame(hero), null, r, spritePaint);

        text.setColor(Color.WHITE);
        text.setTextSize(h * 0.085f);
        c.drawText(Heroes.NAMES[hero], cx, h * 0.73f, text);
        text.setColor(0xFF7FE3FF);
        text.setTextSize(h * 0.04f);
        c.drawText(Heroes.TITLES[hero], cx, h * 0.79f, text);
        text.setColor(resultColor);
        text.setTextSize(h * 0.055f);
        c.drawText(resultLine1, cx, h * 0.87f, text);
        text.setColor(0xFFD7DEFF);
        text.setTextSize(h * 0.04f);
        c.drawText(resultLine2, cx, h * 0.92f, text);
    }

    private void drawGrid(Canvas c, int w, int h, float t) {
        float cw = h * 0.26f, ch = h * 0.33f, gap = h * 0.02f;
        float startX = (w - (5 * cw + 4 * gap)) / 2f, startY = h * 0.15f;
        for (int i = 0; i < resultCount; i++) {
            float pop = easeOutBack((t - i * 0.10f) / 0.30f);
            if (pop <= 0f) continue;
            float x = startX + (i % 5) * (cw + gap), y = startY + (i / 5) * (ch + gap);
            float cx = x + cw / 2f, cy = y + ch / 2f;

            c.save();
            c.scale(pop, pop, cx, cy);
            r.set(x, y, x + cw, y + ch);
            fill.setColor(0xEE141A55);
            c.drawRoundRect(r, cw * 0.08f, cw * 0.08f, fill);
            line.setStrokeWidth(cw * 0.025f);
            line.setColor(resTag[i].equals("NEW!") ? 0xFFFFD54F : Heroes.COLORS[resHero[i]]);
            c.drawRoundRect(r, cw * 0.08f, cw * 0.08f, line);

            float sp = cw * 0.80f;
            r2.set(cx - sp / 2f, y + ch * 0.04f, cx + sp / 2f, y + ch * 0.04f + sp);
            c.drawBitmap(frame(resHero[i]), null, r2, spritePaint);

            text.setTextAlign(Paint.Align.CENTER);
            text.setColor(Color.WHITE);
            text.setTextSize(ch * 0.085f);
            c.drawText(Heroes.NAMES[resHero[i]], cx, y + ch * 0.80f, text);
            text.setColor(resTagColor[i]);
            text.setTextSize(ch * 0.10f);
            c.drawText(resTag[i], cx, y + ch * 0.93f, text);
            c.restore();
        }
        if (t > 0.10f * resultCount + 0.3f) {
            text.setTextAlign(Paint.Align.CENTER);
            text.setColor(0xFFFFE9A8);
            text.setTextSize(h * 0.045f);
            c.drawText(summaryText, w / 2f, h * 0.90f, text);
        }
    }

    // ------------------------------------------------------------ rates window
    private void drawRates(Canvas c, int w, int h) {
        fill.setColor(0xCC000000);
        c.drawRect(0, 0, w, h, fill);

        r.set(w * 0.14f, h * 0.07f, w * 0.86f, h * 0.93f);
        fill.setColor(0xF2101845);
        c.drawRoundRect(r, h * 0.03f, h * 0.03f, fill);
        line.setStrokeWidth(h * 0.006f);
        line.setColor(0xFFE9C46A);
        c.drawRoundRect(r, h * 0.03f, h * 0.03f, line);

        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(0xFFFFE9A8);
        text.setTextSize(h * 0.06f);
        c.drawText("RECRUITMENT RATES", w / 2f, h * 0.16f, text);

        float rowH = h * 0.108f, top = h * 0.21f;
        for (int i = 0; i < PlayerData.HERO_COUNT; i++) {
            float y = top + i * rowH;
            r2.set(w * 0.17f, y, w * 0.83f, y + rowH * 0.92f);
            fill.setColor(i % 2 == 0 ? 0x33FFFFFF : 0x1AFFFFFF);
            c.drawRoundRect(r2, h * 0.015f, h * 0.015f, fill);

            float sp = rowH * 0.86f;
            r.set(w * 0.185f, y + rowH * 0.03f, w * 0.185f + sp, y + rowH * 0.03f + sp);
            c.drawBitmap(heroFrames[i][0], null, r, spritePaint);

            text.setTextAlign(Paint.Align.LEFT);
            text.setColor(Color.WHITE);
            text.setTextSize(h * 0.05f);
            c.drawText(Heroes.NAMES[i], w * 0.185f + sp + w * 0.02f, y + rowH * 0.45f, text);
            text.setColor(0xFF7FE3FF);
            text.setTextSize(h * 0.034f);
            c.drawText(Heroes.TITLES[i], w * 0.185f + sp + w * 0.02f, y + rowH * 0.80f, text);

            text.setTextAlign(Paint.Align.CENTER);
            text.setColor(0xFFFFD54F);
            text.setTextSize(h * 0.05f);
            c.drawText("20%", w * 0.60f, y + rowH * 0.60f, text);

            text.setTextAlign(Paint.Align.RIGHT);
            text.setTextSize(h * 0.04f);
            if (PlayerData.isOwned(i)) {
                text.setColor(0xFF9CF5B8);
                c.drawText(PlayerData.dupes(i) >= PlayerData.MAX_DUPES ? "OWNED  MAX +20%"
                        : "OWNED  +" + PlayerData.dupes(i) + "%", w * 0.82f, y + rowH * 0.60f, text);
            } else {
                text.setColor(0xFF8C93B8);
                c.drawText("LOCKED", w * 0.82f, y + rowH * 0.60f, text);
            }
        }

        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(0xFFD7DEFF);
        text.setTextSize(h * 0.034f);
        c.drawText("Each pull gives a random hero. Cost: " + COST_ONE + " gold (10 pulls: " + COST_TEN + ").", w / 2f, h * 0.80f, text);
        c.drawText("Duplicate: +1% HP, ATK and SPD per copy, up to +20%.", w / 2f, h * 0.855f, text);
        text.setColor(0xFF8C93B8);
        c.drawText("Tap anywhere to close", w / 2f, h * 0.91f, text);
    }
}
