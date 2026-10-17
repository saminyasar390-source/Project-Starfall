package com.starfall.game;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * Idle animation for the heroine in the menu artwork.
 *
 * The artwork is one flat picture, so the character is animated by warping a patch of it: the patch is
 * cut out once, laid over a grid ("mesh"), and every frame each grid point is nudged a little. Each part
 * of the character has a weight map saying how much it moves:
 *
 *   sword   swings around the hand
 *   cape    ripples; waves travel from the shoulder out to the hem
 *   hair    streams in the wind, same idea, faster and finer
 *   body    gentle breathing of the head and upper body
 *
 * Weights are 0 on the border of the patch, so the warped patch blends invisibly into the untouched
 * image. A second, much finer patch over the face squashes the eye shut for a blink.
 *
 * All coordinates below are in "reference pixels" of the 1536x1024 artwork and are scaled to the real
 * bitmap size at start-up.
 */
final class CharacterAnim {
    private static final float REF_W = 1536f;

    // Patch that holds the character, and the grid resolution.
    private static final float[] REGION = {60f, 345f, 780f, 930f};   // x0, y0, x1, y1
    private static final float CELL = 14f;
    private static final float EDGE = 30f;                            // fade-to-zero distance at the patch border

    // Sword: pivot at the gloved hand, tip of the blade.
    private static final float PIVOT_X = 534f, PIVOT_Y = 650f, TIP_X = 657.5f, TIP_Y = 863.5f;

    // Cape and hair: where they attach, and how far they reach.
    private static final float CAPE_AX = 445f, CAPE_AY = 560f, CAPE_REACH = 350f;
    private static final float HAIR_AX = 455f, HAIR_AY = 440f, HAIR_REACH = 262f;

    // Outlines (x,y pairs) of the cape and the flowing hair; edges are feathered so they melt into the background.
    private static final float[] CAPE_POLY = {
        455,545, 405,548, 345,572, 295,595, 250,618, 200,647, 152,673, 116,692, 118,703, 150,710, 190,718,
        222,740, 240,765, 262,752, 292,738, 322,752, 348,785, 372,815, 386,835, 400,800, 420,745, 440,690,
        455,640, 466,590};
    private static final float[] HAIR_POLY = {
        468,382, 446,392, 430,425, 412,452, 368,466, 322,478, 272,490, 218,494, 197,506, 206,542, 230,570,
        272,590, 332,602, 392,600, 432,588, 460,565, 476,525, 480,470, 486,420};

    // Face patch (blink): a small, finely meshed window around the eye.
    private static final float[] FACE_REGION = {470f, 408f, 512f, 446f};
    private static final float EYE_X = 487f, EYE_Y = 425f;

    private static final int SWORD = 0, CAPE = 1, HAIR = 2, BODY = 3, NW = 4;

    private final float k0;
    private final float pivotX, pivotY, ux, uy, bladeLen, swordSigma;
    private final float[] cape, hair;

    // body patch
    private final Bitmap bodyBmp;
    private final int bodyX0, bodyY0, bodyCols, bodyRows;
    private final float[] bodyBase, bodyW, bodyVerts;
    // face patch
    private final Bitmap faceBmp;
    private final int faceX0, faceY0, faceCols, faceRows;
    private final float[] faceBase, faceW, faceEdge, faceVerts;

    private final float[] tmpW = new float[NW];
    private float accX, accY;

    CharacterAnim(Bitmap menu) {
        int mw = menu.getWidth(), mh = menu.getHeight();
        k0 = mw / REF_W;
        cape = scaled(CAPE_POLY);
        hair = scaled(HAIR_POLY);

        pivotX = PIVOT_X * k0;
        pivotY = PIVOT_Y * k0;
        float dx = TIP_X * k0 - pivotX, dy = TIP_Y * k0 - pivotY;
        bladeLen = (float) Math.hypot(dx, dy);
        ux = dx / bladeLen;
        uy = dy / bladeLen;
        swordSigma = 17f * k0;

        // ---- body patch ----
        bodyX0 = Math.max(0, Math.round(REGION[0] * k0));
        bodyY0 = Math.max(0, Math.round(REGION[1] * k0));
        int x1 = Math.min(mw, Math.round(REGION[2] * k0)), y1 = Math.min(mh, Math.round(REGION[3] * k0));
        int bw = x1 - bodyX0, bh = y1 - bodyY0;
        bodyBmp = Bitmap.createBitmap(menu, bodyX0, bodyY0, bw, bh);
        bodyCols = Math.max(1, Math.round(bw / (CELL * k0)));
        bodyRows = Math.max(1, Math.round(bh / (CELL * k0)));
        int n = (bodyCols + 1) * (bodyRows + 1);
        bodyBase = new float[n * 2];
        bodyW = new float[n * NW];
        bodyVerts = new float[n * 2];
        for (int j = 0; j <= bodyRows; j++) {
            for (int i = 0; i <= bodyCols; i++) {
                int idx = j * (bodyCols + 1) + i;
                float px = bodyX0 + bw * i / (float) bodyCols, py = bodyY0 + bh * j / (float) bodyRows;
                bodyBase[idx * 2] = px;
                bodyBase[idx * 2 + 1] = py;
                computeWeights(px, py, bodyW, idx * NW);
                float border = Math.min(Math.min(px - bodyX0, x1 - px), Math.min(py - bodyY0, y1 - py));
                float edge = smooth(0f, EDGE * k0, border);
                for (int c = 0; c < NW; c++) bodyW[idx * NW + c] *= edge;
            }
        }

        // ---- face patch ----
        faceX0 = Math.round(FACE_REGION[0] * k0);
        faceY0 = Math.round(FACE_REGION[1] * k0);
        int fx1 = Math.round(FACE_REGION[2] * k0), fy1 = Math.round(FACE_REGION[3] * k0);
        int fw = fx1 - faceX0, fh = fy1 - faceY0;
        faceBmp = Bitmap.createBitmap(menu, faceX0, faceY0, fw, fh);
        faceCols = Math.max(1, Math.round(fw / (2f * k0)));
        faceRows = Math.max(1, Math.round(fh / (2f * k0)));
        int fn = (faceCols + 1) * (faceRows + 1);
        faceBase = new float[fn * 2];
        faceW = new float[fn * NW];
        faceEdge = new float[fn];
        faceVerts = new float[fn * 2];
        for (int j = 0; j <= faceRows; j++) {
            for (int i = 0; i <= faceCols; i++) {
                int idx = j * (faceCols + 1) + i;
                float px = faceX0 + fw * i / (float) faceCols, py = faceY0 + fh * j / (float) faceRows;
                faceBase[idx * 2] = px;
                faceBase[idx * 2 + 1] = py;
                computeWeights(px, py, faceW, idx * NW);      // same field as the body patch: no seam
                float border = Math.min(Math.min(px - faceX0, fx1 - px), Math.min(py - faceY0, fy1 - py));
                faceEdge[idx] = smooth(0f, 6f * k0, border);   // only the blink fades out at the border
            }
        }
    }

    // =====================================================================================
    //  Per-frame drawing
    // =====================================================================================

    /** Draws the animated character patch. {@code left/top/k} map artwork pixels to screen pixels. */
    void draw(Canvas cv, float t, float left, float top, float k, Paint paint) {
        int n = (bodyCols + 1) * (bodyRows + 1);
        for (int idx = 0; idx < n; idx++) {
            float px = bodyBase[idx * 2], py = bodyBase[idx * 2 + 1];
            displace(bodyW, idx * NW, px, py, t);
            bodyVerts[idx * 2] = left + (px + accX) * k;
            bodyVerts[idx * 2 + 1] = top + (py + accY) * k;
        }
        cv.drawBitmapMesh(bodyBmp, bodyCols, bodyRows, bodyVerts, 0, null, 0, paint);

        // Face: follows the same motion, plus the eye squashing shut during a blink.
        float blink = blinkAmount(t);
        float sx = 5.5f * k0, sy = 6f * k0, eyeX = EYE_X * k0, eyeY = EYE_Y * k0;
        int fn = (faceCols + 1) * (faceRows + 1);
        for (int idx = 0; idx < fn; idx++) {
            float px = faceBase[idx * 2], py = faceBase[idx * 2 + 1];
            displace(faceW, idx * NW, px, py, t);
            float ey = py - eyeY, ex = px - eyeX;
            float wEye = (float) Math.exp(-(ex * ex) / (sx * sx) - (ey * ey) / (sy * sy)) * faceEdge[idx];
            float squash = -ey * 0.95f * blink * wEye;
            faceVerts[idx * 2] = left + (px + accX) * k;
            faceVerts[idx * 2 + 1] = top + (py + accY + squash) * k;
        }
        cv.drawBitmapMesh(faceBmp, faceCols, faceRows, faceVerts, 0, null, 0, paint);
    }

    /** Position (in artwork pixels) of a point on the blade, {@code frac} = 0 at the hand, 1 at the tip. */
    void bladePoint(float frac, float t, float[] out) {
        float s = bladeLen * frac;
        pointAt(pivotX + ux * s, pivotY + uy * s, t, out);
    }

    /** Where a point given in reference pixels ends up this frame (artwork pixels). */
    void pointRef(float refX, float refY, float t, float[] out) {
        pointAt(refX * k0, refY * k0, t, out);
    }

    private void pointAt(float px, float py, float t, float[] out) {
        computeWeights(px, py, tmpW, 0);
        displace(tmpW, 0, px, py, t);
        out[0] = px + accX;
        out[1] = py + accY;
    }

    // =====================================================================================
    //  Motion
    // =====================================================================================

    /** Sets accX/accY to the displacement of the point (px, py) at time t, given its weights. */
    private void displace(float[] w, int o, float px, float py, float t) {
        accX = 0f;
        accY = 0f;

        float wb = w[o + BODY];
        if (wb > 0.001f) accY -= 2.0f * k0 * (float) Math.sin(t * 1.7f) * wb;          // breathing

        float ws = w[o + SWORD];
        if (ws > 0.0005f) {
            float a = ws * swordAngle(t);
            float c = (float) Math.cos(a), s = (float) Math.sin(a);
            float vx = px - pivotX, vy = py - pivotY;
            accX += (c - 1f) * vx - s * vy;
            accY += s * vx + (c - 1f) * vy;
        }

        float wc = w[o + CAPE];
        if (wc > 0.001f) swing(px, py, CAPE_AX * k0, CAPE_AY * k0, CAPE_REACH * k0, wc, 9f, 3.5f, 2.5f, 2.0f, 0.020f, t);

        float wh = w[o + HAIR];
        if (wh > 0.001f) swing(px, py, HAIR_AX * k0, HAIR_AY * k0, HAIR_REACH * k0, wh, 8f, 4f, 2f, 2.7f, 0.034f, t);
    }

    /**
     * Cloth/hair motion about an anchor: the further from the anchor, the more a point moves, and a wave
     * runs outwards (the phase depends on distance), so the tips lag behind the root like real fabric.
     * Amplitudes are in reference pixels.
     */
    private void swing(float px, float py, float ax, float ay, float reach, float w,
                       float tang, float rad, float ripple, float speed, float waveK, float t) {
        float vx = px - ax, vy = py - ay;
        float d = (float) Math.sqrt(vx * vx + vy * vy);
        if (d < 1e-3f) return;
        float r = Math.min(d / reach, 1.1f);
        float amp = w * r * (float) Math.sqrt(r);                       // r^1.5: still at the root, full at the tips
        float rx = vx / d, ry = vy / d;                                 // outward direction
        float dRef = d / k0;
        float side = tang * (float) Math.sin(speed * t - dRef * waveK)
                + ripple * (float) Math.sin(speed * 1.35f * t + px / k0 * 0.045f + py / k0 * 0.035f);
        float out = rad * (float) Math.sin(speed * 0.8f * t - dRef * waveK * 0.8f + 1.1f);
        float m = amp * k0;
        accX += m * (side * -ry + out * rx);
        accY += m * (side * rx + out * ry);
    }

    /** Idle sword sway: a slow swing plus a faster, smaller wobble (about +-2.3 degrees at most). */
    static float swordAngle(float t) {
        return 0.030f * (float) Math.sin(t * 1.45f) + 0.010f * (float) Math.sin(t * 3.1f + 1.3f);
    }

    /** 0 = eyes open, 1 = shut. A blink every few seconds, with an occasional quick double blink. */
    static float blinkAmount(float t) {
        final float cycle = 4.6f;
        int n = (int) (t / cycle);
        float c = t - n * cycle;
        float b = blinkPulse(c, 1.5f, 0.20f);
        if ((n & 1) == 1) b = Math.max(b, blinkPulse(c, 1.85f, 0.20f));
        b = Math.min(1f, b * 1.5f);
        return b * b * (3f - 2f * b);
    }

    private static float blinkPulse(float c, float start, float dur) {
        float u = (c - start) / dur;
        return (u < 0f || u > 1f) ? 0f : (float) Math.sin(Math.PI * u);
    }

    // =====================================================================================
    //  Weight maps
    // =====================================================================================

    /** Fills w[o..o+3] with how strongly the sword, cape, hair and body move at this artwork pixel. */
    private void computeWeights(float px, float py, float[] w, int o) {
        float vx = px - pivotX, vy = py - pivotY;
        float s = vx * ux + vy * uy, q = vx * uy - vy * ux;
        w[o + SWORD] = smooth(8f * k0, 70f * k0, s) * (float) Math.exp(-(q * q) / (swordSigma * swordSigma));

        float rx = px / k0, ry = py / k0;
        w[o + CAPE] = (rx > 80f && rx < 500f && ry > 520f && ry < 860f) ? polyMask(cape, px, py, 14f * k0) : 0f;
        w[o + HAIR] = (rx > 170f && rx < 510f && ry > 360f && ry < 630f) ? polyMask(hair, px, py, 8f * k0) : 0f;

        float bx = smooth(395f * k0, 425f * k0, px) * (1f - smooth(510f * k0, 535f * k0, px));
        float by = smooth(350f * k0, 372f * k0, py) * (1f - smooth(600f * k0, 690f * k0, py));
        w[o + BODY] = bx * by;
    }

    /** 1 inside the polygon, 0 outside, with a soft transition of +-feather across its outline. */
    private static float polyMask(float[] poly, float x, float y, float feather) {
        int n = poly.length / 2;
        boolean inside = false;
        float minD2 = Float.MAX_VALUE;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            float xi = poly[i * 2], yi = poly[i * 2 + 1], xj = poly[j * 2], yj = poly[j * 2 + 1];
            if (((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi)) inside = !inside;
            float ex = xj - xi, ey = yj - yi, l2 = ex * ex + ey * ey;
            float u = l2 == 0f ? 0f : ((x - xi) * ex + (y - yi) * ey) / l2;
            u = u < 0f ? 0f : (u > 1f ? 1f : u);
            float cx = xi + u * ex - x, cy = yi + u * ey - y;
            float d2 = cx * cx + cy * cy;
            if (d2 < minD2) minD2 = d2;
        }
        float d = (float) Math.sqrt(minD2);
        return smooth(-feather, feather, inside ? d : -d);
    }

    private float[] scaled(float[] ref) {
        float[] out = new float[ref.length];
        for (int i = 0; i < ref.length; i++) out[i] = ref[i] * k0;
        return out;
    }

    private static float smooth(float a, float b, float x) {
        float t = (x - a) / (b - a);
        t = t < 0f ? 0f : (t > 1f ? 1f : t);
        return t * t * (3f - 2f * t);
    }

    void recycle() {
        if (bodyBmp != null && !bodyBmp.isRecycled()) bodyBmp.recycle();
        if (faceBmp != null && !faceBmp.isRecycled()) faceBmp.recycle();
    }
}
