package com.starfall.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import java.util.Random;

/**
 * Animated fantasy main menu.
 *
 * The artwork is a single flat image, so everything that moves is layered on top of it:
 *  - sky:       particles orbiting the swirling rings, sparks climbing the beacon, twinkling stars
 *  - character: sword sway, fluttering cape and hair, breathing and blinking (see CharacterAnim),
 *               plus a blade shimmer, glint and embers and a few magic glints
 *  - foreground: drifting blossom petals and fireflies near the cliff
 *  - buttons:   glowing rims that react to touch
 *
 * All animation is a pure function of time and uses pre-built sprites, so onDraw() allocates nothing.
 */
public class MenuView extends View {
    public interface Listener { void onButton(int index); }
    public static final int PLAY = 0, CONVENE = 1, SHOP = 2, LEADERBOARD = 3, SETTINGS = 4, EXIT = 5;

    private static final float[][] BUTTONS = {
        {0.768f,0.314f,0.945f,0.373f}, {0.768f,0.403f,0.945f,0.461f},
        {0.768f,0.499f,0.945f,0.558f}, {0.768f,0.593f,0.945f,0.653f},
        {0.768f,0.688f,0.945f,0.748f}, {0.768f,0.787f,0.945f,0.846f}
    };

    // ---- Landmarks in the artwork (fractions of the image; ring radii are fractions of WIDTH) ----
    private static final float STAR_X = 0.5817f, STAR_Y = 0.1206f;   // bright star on the beacon
    private static final float BEAM_BOTTOM = 0.46f;                  // where the beam meets the castle
    private static final float RING_CX = 0.604f, RING_CY = 0.092f;   // centre of the swirling rings
    private static final float RING_RX = 0.158f, RING_RY = 0.057f;
    private static final float RING_TILT = (float) Math.toRadians(-5.5);
    // ---- Particle counts ----
    private static final int RING_N = 64, SPARK_N = 26, STAR_N = 44, PETAL_N = 16, FLY_N = 14, EMBER_N = 8;

    // ---- Sprite indices ----
    private static final int S_WHITE = 0, S_BLUE = 1, S_VIOLET = 2, S_GOLD = 3, S_PINK = 4, S_CYAN = 5;
    private static final int[] SPRITE_RGB = {0xD8F0FF, 0x5AA8FF, 0xA98BFF, 0xFFD27A, 0xFF9AD5, 0x7FF0FF};
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    private final Bitmap menu;
    private final Listener listener;
    private final SoundManager sounds;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint fx = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint add = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);   // additive sprites
    private final Paint addLine = new Paint(Paint.ANTI_ALIAS_FLAG);                          // additive lines
    private final Paint petalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dst = new RectF();
    private final RectF rect = new RectF();
    private final RectF btn = new RectF();
    private final float[] pt = new float[2];
    private int hovered = -1;
    private final long startTime = SystemClock.uptimeMillis();
    private boolean introPlayed;

    private final Bitmap[] sprites = new Bitmap[SPRITE_RGB.length];
    private Bitmap flareSprite, beamSprite;

    // particle parameters (filled once)
    private final float[] ringPhase = new float[RING_N], ringR = new float[RING_N], ringOmega = new float[RING_N],
            ringSize = new float[RING_N], ringTw = new float[RING_N];
    private final int[] ringSprite = new int[RING_N];
    private final float[] sparkPhase = new float[SPARK_N], sparkSpeed = new float[SPARK_N],
            sparkOff = new float[SPARK_N], sparkSize = new float[SPARK_N];
    private final int[] sparkSprite = new int[SPARK_N];
    private final float[] starX = new float[STAR_N], starY = new float[STAR_N], starSize = new float[STAR_N],
            starSpeed = new float[STAR_N], starPhase = new float[STAR_N];
    private final float[] petalX0 = new float[PETAL_N], petalLife = new float[PETAL_N], petalPhase = new float[PETAL_N],
            petalSize = new float[PETAL_N], petalSpin = new float[PETAL_N];
    private final int[] petalColor = new int[PETAL_N];
    private final float[] flyX = new float[FLY_N], flyY = new float[FLY_N], flySpeed = new float[FLY_N],
            flyPhase = new float[FLY_N], flySize = new float[FLY_N];
    private final int[] flySprite = new int[FLY_N];
    private final float[] emberS = new float[EMBER_N], emberPhase = new float[EMBER_N];

    private CharacterAnim anim;
    private final float[] ap = new float[2];
    private final float[] bp = new float[2];

    public MenuView(Context c, Listener l) {
        super(c);
        listener = l;
        sounds = new SoundManager(c);
        menu = Assets.load(c, R.drawable.menu_main);
        add.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        addLine.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        addLine.setStyle(Paint.Style.STROKE);
        addLine.setStrokeCap(Paint.Cap.ROUND);
        buildSprites();
        buildParticles();
        anim = new CharacterAnim(menu);
        setFocusable(true);
    }

    // =====================================================================================
    //  Setup
    // =====================================================================================

    private void buildSprites() {
        for (int i = 0; i < sprites.length; i++) sprites[i] = makeGlow(SPRITE_RGB[i]);
        flareSprite = makeFlare();
        beamSprite = makeBeam();
    }

    /** Soft round glow: bright whitened core fading to a coloured halo. */
    private static Bitmap makeGlow(int rgb) {
        final int n = 64;
        Bitmap b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        int r = Color.red(rgb), g = Color.green(rgb), bl = Color.blue(rgb);
        int core = Color.rgb(r + (255 - r) * 7 / 10, g + (255 - g) * 7 / 10, bl + (255 - bl) * 7 / 10);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(n / 2f, n / 2f, n / 2f,
                new int[]{core, Color.argb(210, r, g, bl), Color.argb(60, r, g, bl), Color.argb(0, r, g, bl)},
                new float[]{0f, 0.14f, 0.42f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(n / 2f, n / 2f, n / 2f, p);
        return b;
    }

    /** Four-point star flare (two thin crossing arms and a bright core). */
    private static Bitmap makeFlare() {
        final int n = 128;
        int[] px = new int[n * n];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float dx = (x - 63.5f) / 64f, dy = (y - 63.5f) / 64f;
                float h = 1f - Math.abs(dx); h = h < 0 ? 0 : h * h;
                float v = 1f - Math.abs(dy); v = v < 0 ? 0 : v * v;
                h *= (float) Math.exp(-(dy * dy) / 0.0025f);
                v *= (float) Math.exp(-(dx * dx) / 0.0025f);
                float core = (float) Math.exp(-(dx * dx + dy * dy) / 0.012f);
                float a = Math.min(1f, h + v + core);
                px[y * n + x] = Color.argb((int) (a * 255f), 235, 246, 255);
            }
        }
        Bitmap b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        b.setPixels(px, 0, n, 0, 0, n, n);
        return b;
    }

    /** Soft vertical column of light, fading out at both ends. */
    private static Bitmap makeBeam() {
        final int w = 32, h = 256;
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            float v = y / (float) (h - 1);
            float vert = (float) Math.sin(Math.PI * v);
            for (int x = 0; x < w; x++) {
                float dx = (x - 15.5f) / 16f;
                float a = (float) Math.exp(-(dx * dx) / 0.16f) * vert;
                px[y * w + x] = Color.argb((int) (a * 255f), 190, 228, 255);
            }
        }
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        b.setPixels(px, 0, w, 0, 0, w, h);
        return b;
    }

    private void buildParticles() {
        Random rng = new Random(20261017L);
        for (int i = 0; i < RING_N; i++) {
            ringPhase[i] = rng.nextFloat() * TWO_PI;
            ringR[i] = 0.34f + 0.66f * (float) Math.sqrt(rng.nextFloat());
            ringOmega[i] = 0.16f + 0.24f * (1f - ringR[i]);      // inner rings spin faster
            ringSize[i] = 0.0042f + 0.0046f * rng.nextFloat();
            ringTw[i] = rng.nextFloat();
            float r = rng.nextFloat();
            ringSprite[i] = r < 0.35f ? S_WHITE : (r < 0.65f ? S_CYAN : (r < 0.88f ? S_BLUE : S_VIOLET));
        }
        for (int i = 0; i < SPARK_N; i++) {
            sparkPhase[i] = rng.nextFloat();
            sparkSpeed[i] = 0.10f + 0.12f * rng.nextFloat();
            sparkOff[i] = (rng.nextFloat() - 0.5f) * 0.014f;
            sparkSize[i] = 0.0045f + 0.0050f * rng.nextFloat();
            float r = rng.nextFloat();
            sparkSprite[i] = r < 0.5f ? S_WHITE : (r < 0.8f ? S_CYAN : S_VIOLET);
        }
        for (int i = 0; i < STAR_N; i++) {
            starX[i] = 0.46f + 0.53f * rng.nextFloat();
            starY[i] = 0.30f * rng.nextFloat();
            starSize[i] = 0.0035f + 0.0040f * rng.nextFloat();
            starSpeed[i] = 0.8f + 1.8f * rng.nextFloat();
            starPhase[i] = rng.nextFloat() * TWO_PI;
        }
        int[] pinks = {0xFFFFA8D0, 0xFFFF8CC2, 0xFFFFC4DE};
        for (int i = 0; i < PETAL_N; i++) {
            petalX0[i] = -0.02f + 0.50f * rng.nextFloat();
            petalLife[i] = 13f + 10f * rng.nextFloat();
            petalPhase[i] = rng.nextFloat();
            petalSize[i] = 0.0055f + 0.0045f * rng.nextFloat();
            petalSpin[i] = 1.2f + 1.6f * rng.nextFloat();
            petalColor[i] = pinks[i % pinks.length];
        }
        int[] flyKinds = {S_GOLD, S_PINK, S_CYAN};
        for (int i = 0; i < FLY_N; i++) {
            flyX[i] = 0.03f + 0.46f * rng.nextFloat();
            flyY[i] = 0.60f + 0.36f * rng.nextFloat();
            flySpeed[i] = 0.35f + 0.55f * rng.nextFloat();
            flyPhase[i] = rng.nextFloat() * TWO_PI;
            flySize[i] = 0.0070f + 0.0070f * rng.nextFloat();
            flySprite[i] = flyKinds[i % flyKinds.length];
        }
        for (int i = 0; i < EMBER_N; i++) {
            emberS[i] = 0.25f + 0.75f * rng.nextFloat();
            emberPhase[i] = rng.nextFloat();
        }
    }

    // =====================================================================================
    //  Drawing
    // =====================================================================================

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!introPlayed) { sounds.menuIntro(); introPlayed = true; }
        postInvalidateOnAnimation();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (menu == null || menu.isRecycled()) return;
        canvas.drawColor(Color.rgb(3, 5, 22));
        float scale = Math.min(getWidth() / (float) menu.getWidth(), getHeight() / (float) menu.getHeight());
        float w = menu.getWidth() * scale, h = menu.getHeight() * scale;
        dst.set((getWidth() - w) / 2f, (getHeight() - h) / 2f, (getWidth() + w) / 2f, (getHeight() + h) / 2f);
        canvas.drawBitmap(menu, null, dst, paint);

        float t = (SystemClock.uptimeMillis() - startTime) / 1000f;
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 1.25f);

        drawSky(canvas, t, pulse);
        drawCharacter(canvas, t, pulse);
        drawPetals(canvas, t);
        drawFireflies(canvas, t);
        drawButtons(canvas, t);
        postInvalidateOnAnimation();
    }

    // ---- sky: haze, beam, orbiting ring particles, rising sparks, twinkling stars -------------

    private void drawSky(Canvas cv, float t, float pulse) {
        float W = dst.width(), H = dst.height();
        float bx = dst.left + W * STAR_X, sy = dst.top + H * STAR_Y;
        float botY = dst.top + H * BEAM_BOTTOM;

        // breathing atmosphere around the beacon
        glow(cv, S_BLUE, bx, dst.top + H * 0.26f, W * 0.13f, (int) (28 + 26 * pulse));

        // shimmering column of light
        rect.set(bx - W * 0.020f, dst.top, bx + W * 0.020f, botY);
        add.setAlpha((int) (40 + 50 * pulse));
        cv.drawBitmap(beamSprite, null, rect, add);

        // particles orbiting the swirling rings (head + two fading trail samples each)
        float cx = dst.left + W * RING_CX, cy = dst.top + H * RING_CY;
        float rx = W * RING_RX, ry = W * RING_RY;
        float ct = (float) Math.cos(RING_TILT), st = (float) Math.sin(RING_TILT);
        int trails = GameSettings.quality == GameSettings.LOW ? 1 : 3;   // trail samples per particle
        int ringCount = count(RING_N);
        for (int i = 0; i < ringCount; i++) {
            float tw = 0.5f + 0.5f * (float) Math.sin(t * (1.4f + 0.9f * ringTw[i]) + ringPhase[i] * 3f);
            float wob = 1f + 0.04f * (float) Math.sin(t * 0.6f + ringPhase[i] * 2f);
            for (int k = 0; k < trails; k++) {
                float a = ringPhase[i] + ringOmega[i] * (t - k * 0.11f);
                float lx = ringR[i] * wob * rx * (float) Math.cos(a);
                float ly = ringR[i] * wob * ry * (float) Math.sin(a);
                float depth = 0.5f + 0.5f * (float) Math.sin(a);          // 1 = nearest the viewer
                float trailSize = k == 0 ? 1f : (k == 1 ? 0.7f : 0.45f);
                float trailAlpha = k == 0 ? 1f : (k == 1 ? 0.45f : 0.2f);
                float size = W * ringSize[i] * (0.75f + 0.5f * depth) * trailSize;
                float alpha = (0.25f + 0.75f * tw) * (0.45f + 0.55f * depth) * trailAlpha;
                glow(cv, ringSprite[i], cx + lx * ct - ly * st, cy + lx * st + ly * ct, size, (int) (255 * alpha));
            }
        }

        // sparks climbing the beacon, through the star and off the top of the screen
        int sparkCount = count(SPARK_N);
        for (int i = 0; i < sparkCount; i++) {
            float u = fract(sparkPhase[i] + t * sparkSpeed[i]);            // 0 = castle, 1 = sky
            float y = botY + (dst.top - botY) * u;
            float x = bx + W * (sparkOff[i] + 0.004f * (float) Math.sin(t * 1.7f + sparkPhase[i] * 9f));
            float fade = (float) Math.sin(Math.PI * u);
            float tw = 0.65f + 0.35f * (float) Math.sin(t * 5f + i * 2.3f);
            glow(cv, sparkSprite[i], x, y, W * sparkSize[i], (int) (255 * fade * tw));
        }

        // the star itself: pulsing glow plus a slowly turning four-point flare
        glow(cv, S_WHITE, bx, sy, W * (0.032f + 0.008f * pulse), (int) (55 + 55 * pulse));
        float f = W * (0.042f + 0.010f * pulse);
        add.setAlpha((int) (80 + 80 * pulse));
        cv.save();
        cv.rotate(t * 5f, bx, sy);
        rect.set(bx - f, sy - f, bx + f, sy + f);
        cv.drawBitmap(flareSprite, null, rect, add);
        cv.restore();

        // twinkling stars across the sky (a few get a little flare)
        int starCount = count(STAR_N);
        for (int i = 0; i < starCount; i++) {
            float tw = 0.5f + 0.5f * (float) Math.sin(t * starSpeed[i] + starPhase[i]);
            tw = tw * tw * tw;
            float x = dst.left + W * starX[i], y = dst.top + H * starY[i];
            if (i % 9 == 0) {
                float fs = W * 0.016f * (0.5f + 0.7f * tw);
                add.setAlpha((int) (40 + 215 * tw));
                rect.set(x - fs, y - fs, x + fs, y + fs);
                cv.drawBitmap(flareSprite, null, rect, add);
            } else {
                glow(cv, S_WHITE, x, y, W * starSize[i] * (0.7f + 0.6f * tw), (int) (40 + 215 * tw));
            }
        }
    }

    // ---- character: sword, cape, hair, breathing and blink (see CharacterAnim) ---------------

    private void drawCharacter(Canvas cv, float t, float pulse) {
        float W = dst.width(), H = dst.height();
        float k = W / menu.getWidth();
        float ox = dst.left, oy = dst.top;

        // soft light aura around the character
        glow(cv, S_BLUE, ox + W * 0.30f, oy + H * 0.57f, W * 0.10f, (int) (26 + 22 * pulse));

        // the warped character patch itself
        anim.draw(cv, t, ox, oy, k, paint);

        // magic glints that ride on the moving parts: cape ornaments, shoulder gem, hair ornament
        sparkleAt(cv, 360f, 672f, t, 1.3f, 0f, W * 0.022f, k);
        sparkleAt(cv, 306f, 637f, t, 1.3f, 2.1f, W * 0.017f, k);
        sparkleAt(cv, 465f, 404f, t, 0.9f, 4.0f, W * 0.012f, k);
        anim.pointRef(493f, 502f, t, ap);
        glow(cv, S_CYAN, ox + ap[0] * k, oy + ap[1] * k, W * (0.010f + 0.003f * pulse), (int) (60 + 110 * pulse));

        // shimmer travelling up and down the blade
        addLine.setStrokeWidth(W * 0.0022f);
        float px = 0f, py = 0f;
        for (int seg = 0; seg <= 6; seg++) {
            anim.bladePoint(0.10f + 0.88f * seg / 6f, t, bp);
            float sx = ox + bp[0] * k, sy = oy + bp[1] * k;
            if (seg > 0) {
                float wave = 0.5f + 0.5f * (float) Math.sin(t * 3.2f - seg * 0.9f);
                addLine.setColor(0xFFBFE8FF);
                addLine.setAlpha((int) (25 + 110 * wave));
                cv.drawLine(px, py, sx, sy, addLine);
            }
            px = sx;
            py = sy;
        }

        // a glint that runs from the hilt to the tip every few seconds
        float gp = fract(t / 4.2f) * 1.5f;
        if (gp < 1f) {
            anim.bladePoint(0.10f + 0.90f * gp, t, bp);
            glow(cv, S_WHITE, ox + bp[0] * k, oy + bp[1] * k, W * 0.013f, (int) (230 * Math.sin(Math.PI * gp)));
        }

        // glowing tip with an occasional sparkle
        anim.bladePoint(1f, t, bp);
        float tx = ox + bp[0] * k, ty = oy + bp[1] * k;
        float tipPulse = 0.5f + 0.5f * (float) Math.sin(t * 2.4f);
        glow(cv, S_CYAN, tx, ty, W * (0.016f + 0.005f * tipPulse), (int) (70 + 90 * tipPulse));
        float spark = (float) Math.pow(Math.max(0f, Math.sin(t * 1.1f)), 6.0);
        if (spark > 0.02f) {
            float fs = W * 0.020f * (0.5f + 0.5f * spark);
            add.setAlpha((int) (230 * spark));
            rect.set(tx - fs, ty - fs, tx + fs, ty + fs);
            cv.drawBitmap(flareSprite, null, rect, add);
        }

        // embers lifting off the blade
        int emberCount = count(EMBER_N);
        for (int i = 0; i < emberCount; i++) {
            float u = fract(emberPhase[i] + t * 0.16f);
            anim.bladePoint(emberS[i], t, bp);
            float x = ox + bp[0] * k + W * 0.006f * (float) Math.sin(t * 1.1f + emberPhase[i] * TWO_PI);
            float y = oy + bp[1] * k - H * 0.07f * u;
            glow(cv, i % 2 == 0 ? S_CYAN : S_WHITE, x, y, W * 0.0055f, (int) (200 * Math.sin(Math.PI * u)));
        }
    }

    /** A twinkling four-point flare pinned to a spot of the artwork (reference pixels), following its motion. */
    private void sparkleAt(Canvas cv, float refX, float refY, float t, float speed, float phase, float size, float k) {
        float tw = (float) Math.max(0.0, Math.sin(t * speed + phase));
        tw = tw * tw * tw * tw;
        if (tw < 0.02f) return;
        anim.pointRef(refX, refY, t, ap);
        float x = dst.left + ap[0] * k, y = dst.top + ap[1] * k, f = size * (0.5f + 0.5f * tw);
        add.setAlpha((int) (235 * tw));
        rect.set(x - f, y - f, x + f, y + f);
        cv.drawBitmap(flareSprite, null, rect, add);
        glow(cv, S_WHITE, x, y, f * 0.6f, (int) (140 * tw));
    }

    // ---- foreground: blossom petals and fireflies ---------------------------------------------

    private void drawPetals(Canvas cv, float t) {
        float W = dst.width(), H = dst.height();
        int petalCount = count(PETAL_N);
        for (int i = 0; i < petalCount; i++) {
            float u = fract(petalPhase[i] + t / petalLife[i]);               // 0 = top, 1 = bottom
            float x = dst.left + W * (petalX0[i] + 0.22f * u + 0.018f * (float) Math.sin(t * 0.9f + petalPhase[i] * 20f));
            float y = dst.top + H * (-0.04f + 1.08f * u);
            float fade = Math.min(1f, Math.min(u * 8f, (1f - u) * 8f));
            float flip = 0.35f + 0.65f * Math.abs((float) Math.cos(t * petalSpin[i] + petalPhase[i] * 7f));
            float s = W * petalSize[i];
            petalPaint.setColor(petalColor[i]);
            petalPaint.setAlpha((int) (225 * fade));
            cv.save();
            cv.translate(x, y);
            cv.rotate(40f * (float) Math.sin(t * 0.8f + petalPhase[i] * 11f) + petalPhase[i] * 360f);
            rect.set(-s, -s * 0.5f * flip, s, s * 0.5f * flip);
            cv.drawOval(rect, petalPaint);
            cv.restore();
        }
    }

    private void drawFireflies(Canvas cv, float t) {
        float W = dst.width(), H = dst.height();
        int flyCount = count(FLY_N);
        for (int i = 0; i < flyCount; i++) {
            float sp = flySpeed[i];
            float x = dst.left + W * (flyX[i] + 0.012f * (float) Math.sin(t * sp + flyPhase[i]));
            float y = dst.top + H * (flyY[i] + 0.016f * (float) Math.sin(t * sp * 0.8f + flyPhase[i] * 1.7f));
            float p = 0.5f + 0.5f * (float) Math.sin(t * 1.6f * sp + flyPhase[i] * 3f);
            p *= p;
            glow(cv, flySprite[i], x, y, W * flySize[i], (int) (40 + 200 * p));
        }
    }

    // ---- buttons ---------------------------------------------------------------------------

    private void drawButtons(Canvas canvas, float t) {
        // Soft animated rims make the existing illustrated buttons feel responsive.
        for (int i = 0; i < BUTTONS.length; i++) {
            float[] b = BUTTONS[i];
            btn.set(dst.left + dst.width() * b[0], dst.top + dst.height() * b[1],
                    dst.left + dst.width() * b[2], dst.top + dst.height() * b[3]);
            boolean active = i == hovered;
            float a = active ? 0.9f : (float) (0.24 + 0.2 * (0.5 + 0.5 * Math.sin(t * 2.0 - i * 0.7)));
            fx.setColor(active ? 0xFFFFDCA0 : 0xFF8DDCFF);
            fx.setAlpha((int) (255 * a));
            fx.setStyle(Paint.Style.STROKE);
            fx.setStrokeWidth(active ? dst.width() * 0.0032f : dst.width() * 0.0011f);
            canvas.drawRoundRect(btn, dst.width() * 0.009f, dst.width() * 0.009f, fx);
            if (active) {
                fx.setStyle(Paint.Style.FILL);
                fx.setShader(new RadialGradient(btn.centerX(), btn.centerY(), btn.width() * 1.1f,
                        new int[]{0x2FFFF0B7, 0x00FFE4A1}, null, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(btn, dst.width() * 0.01f, dst.width() * 0.01f, fx);
                fx.setShader(null);
            }
            fx.setStyle(Paint.Style.FILL);
        }
    }

    // ---- helpers ---------------------------------------------------------------------------

    /** Draws an additive glow sprite centred on (x, y) with the given radius. */
    private void glow(Canvas cv, int sprite, float x, float y, float radius, int alpha) {
        if (alpha <= 0) return;
        rect.set(x - radius, y - radius, x + radius, y + radius);
        add.setAlpha(alpha > 255 ? 255 : alpha);
        cv.drawBitmap(sprites[sprite], null, rect, add);
    }

    private static float fract(float x) { return x - (float) Math.floor(x); }

    /** How many of {@code max} particles to draw at the quality chosen in Settings. */
    private static int count(int max) {
        return Math.max(1, Math.round(max * GameSettings.qualityFactor()));
    }

    // ---- touch -----------------------------------------------------------------------------

    private int hit(float x, float y) {
        if (dst.width() <= 0 || x < dst.left || x > dst.right || y < dst.top || y > dst.bottom) return -1;
        float fx = (x - dst.left) / dst.width(), fy = (y - dst.top) / dst.height();
        for (int i = 0; i < BUTTONS.length; i++) {
            float[] b = BUTTONS[i];
            if (fx >= b[0] && fx <= b[2] && fy >= b[1] && fy <= b[3]) return i;
        }
        return -1;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            hovered = hit(e.getX(), e.getY()); invalidate(); return true;
        }
        if (e.getAction() == MotionEvent.ACTION_MOVE) {
            int next = hit(e.getX(), e.getY());
            if (next != hovered) { hovered = next; if (next >= 0) sounds.hover(); invalidate(); }
            return true;
        }
        if (e.getAction() == MotionEvent.ACTION_UP) {
            int selected = hit(e.getX(), e.getY());
            hovered = -1; invalidate();
            if (selected >= 0) { sounds.click(); listener.onButton(selected); }
            performClick(); return true;
        }
        if (e.getAction() == MotionEvent.ACTION_CANCEL) { hovered = -1; invalidate(); return true; }
        return true;
    }

    @Override public boolean performClick() { super.performClick(); return true; }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(null);
        sounds.release();
        if (anim != null) anim.recycle();
        if (menu != null && !menu.isRecycled()) menu.recycle();
        super.onDetachedFromWindow();
    }
}
