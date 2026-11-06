package com.starfall.game;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.graphics.Typeface;

/** Three-step startup: Powered By splash, Starfall icon reveal, then a timed safety warning. */
public class IntroActivity extends Activity {
    private SoundPool soundPool;
    private int revealSound = 0;
    private boolean soundReady = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiUtil.goFullscreen(this);
        try {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
            soundPool = new SoundPool.Builder().setMaxStreams(2).setAudioAttributes(attributes).build();
            soundPool.setOnLoadCompleteListener((pool, sampleId, status) -> {
                if (status == 0 && sampleId == revealSound) soundReady = true;
            });
            revealSound = soundPool.load(this, R.raw.sfx_android_reveal, 1);
        } catch (Exception ignored) { /* Intro still works silently on devices without audio. */ }
        setContentView(new IntroView());
    }

    private void openMainMenu() {
        Intent intent = new Intent(IntroActivity.this, MainMenuActivity.class);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override protected void onDestroy() {
        if (soundPool != null) { soundPool.release(); soundPool = null; }
        super.onDestroy();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) UiUtil.goFullscreen(this);
    }

    private class IntroView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Bitmap studioLogo;
        private final Bitmap cleanStudioLogo;
        private final Bitmap androidMascot;
        private final Bitmap starfallIcon;
        private final long startedAt = SystemClock.uptimeMillis();
        private int stage = 0;
        private long stageStarted = startedAt;
        private boolean soundPlayed = false;
        private boolean launchedMenu = false;
        private float downX, downY;

        IntroView() {
            super(IntroActivity.this);
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
            studioLogo = BitmapFactory.decodeResource(getResources(), R.drawable.intro_android_studio);
            cleanStudioLogo = makeCleanStudioLogo(studioLogo);
            androidMascot = extractAndroidMascot(studioLogo);
            starfallIcon = BitmapFactory.decodeResource(getResources(), R.drawable.intro_starfall_icon);
            setFocusable(true);
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            long now = SystemClock.uptimeMillis();
            long elapsed = now - stageStarted;
            if (stage == 0 && elapsed >= 2500) changeStage(1, now);
            else if (stage == 1 && elapsed >= 2500) changeStage(2, now);

            if (stage == 0) drawPoweredBy(c, elapsed);
            else if (stage == 1) drawStarfall(c, elapsed);
            else drawWarning(c, elapsed);

            if (stage == 0 && elapsed >= 900 && !soundPlayed) {
                soundPlayed = true;
                if (soundPool != null && soundReady && revealSound != 0) {
                    try { soundPool.play(revealSound, 0.85f, 0.85f, 1, 0, 1f); } catch (Exception ignored) { }
                }
            }
            postInvalidateDelayed(16);
        }

        private void changeStage(int next, long now) { stage = next; stageStarted = now; invalidate(); }

        private void drawPoweredBy(Canvas c, long ms) {
            c.drawColor(Color.WHITE);
            float w = getWidth(), h = getHeight();
            if (cleanStudioLogo != null) {
                float scale = Math.min(w / cleanStudioLogo.getWidth(), h / cleanStudioLogo.getHeight());
                float dw = cleanStudioLogo.getWidth() * scale, dh = cleanStudioLogo.getHeight() * scale;
                RectF dst = new RectF((w-dw)/2f, (h-dh)/2f, (w+dw)/2f, (h+dh)/2f);
                p.setAlpha(255); c.drawBitmap(cleanStudioLogo, null, dst, p);
                // Reveal the real green Android mascot pixels extracted from the supplied logo.
                // The blue Android Studio compass and lettering remain untouched.
                if (androidMascot != null) {
                    float t = Math.max(0f, Math.min(1f, (ms - 350f) / 650f));
                    float bounce;
                    if (t <= 0f) bounce = 0f;
                    else if (t < 0.78f) bounce = (float)(1.12 * Math.sin((t / 0.78) * Math.PI / 2));
                    else bounce = (float)(1 + 0.12 * Math.cos(((t - 0.78) / 0.22) * Math.PI));
                    float mascotScale = scale * bounce;
                    float cx = (w - dw) / 2f + 1400f * scale;
                    float cy = (h - dh) / 2f + 430f * scale;
                    float mw = androidMascot.getWidth() * mascotScale;
                    float mh = androidMascot.getHeight() * mascotScale;
                    c.save();
                    c.rotate(-3f * (1f - t), cx, cy);
                    RectF mascotDst = new RectF(cx - mw/2f, cy - mh/2f, cx + mw/2f, cy + mh/2f);
                    p.setAlpha(255);
                    c.drawBitmap(androidMascot, null, mascotDst, p);
                    c.restore();
                }
            }
            p.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            p.setTextAlign(Paint.Align.CENTER); p.setTextSize(Math.max(20, h*.045f));
            p.setColor(Color.rgb(40, 48, 58)); p.setShadowLayer(5, 0, 2, 0x33000000);
            c.drawText("Powered By", w*.5f, h*.12f, p); p.clearShadowLayer();
        }

        private Bitmap makeCleanStudioLogo(Bitmap source) {
            if (source == null) return null;
            Bitmap clean = source.copy(Bitmap.Config.ARGB_8888, true);
            int left = Math.max(0, (int)(source.getWidth() * 0.78f));
            int top = Math.max(0, (int)(source.getHeight() * 0.32f));
            int right = Math.min(source.getWidth(), (int)(source.getWidth() * 0.97f));
            int bottom = Math.min(source.getHeight(), (int)(source.getHeight() * 0.67f));
            for (int y = top; y < bottom; y++) for (int x = left; x < right; x++) {
                int color = source.getPixel(x, y);
                int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
                if (isAndroidGreen(r, g, b)) clean.setPixel(x, y, Color.WHITE);
            }
            return clean;
        }

        private Bitmap extractAndroidMascot(Bitmap source) {
            if (source == null) return null;
            int left = Math.max(0, (int)(source.getWidth() * 0.78f));
            int top = Math.max(0, (int)(source.getHeight() * 0.32f));
            int right = Math.min(source.getWidth(), (int)(source.getWidth() * 0.97f));
            int bottom = Math.min(source.getHeight(), (int)(source.getHeight() * 0.67f));
            Bitmap cut = Bitmap.createBitmap(source, left, top, right-left, bottom-top).copy(Bitmap.Config.ARGB_8888, true);
            for (int y = 0; y < cut.getHeight(); y++) for (int x = 0; x < cut.getWidth(); x++) {
                int color = cut.getPixel(x, y);
                if (!isAndroidGreen(Color.red(color), Color.green(color), Color.blue(color))) cut.setPixel(x, y, Color.TRANSPARENT);
            }
            return cut;
        }

        private boolean isAndroidGreen(int r, int g, int b) {
            return g > 105 && g > r * 1.20 && g > b * 1.03 && (g - r) > 25;
        }

        private void drawStarfall(Canvas c, long ms) {
            c.drawColor(Color.rgb(4, 10, 30));
            float w=getWidth(), h=getHeight();
            p.setColor(0x553C8CFF); p.setStyle(Paint.Style.FILL);
            float pulse=.5f+.5f*(float)Math.sin(ms/240.0);
            c.drawCircle(w/2f,h/2f,Math.min(w,h)*(.28f+.015f*pulse),p);
            if (starfallIcon != null) {
                float max=Math.min(h*.82f,w*.65f), scale=Math.min(max/starfallIcon.getWidth(),max/starfallIcon.getHeight());
                float size=starfallIcon.getWidth()*scale;
                float pop=Math.min(1f,ms/650f); pop=(float)(1-Math.pow(1-pop,3));
                float shown=size*pop;
                RectF dst=new RectF(w/2f-shown/2f,h/2f-shown/2f,w/2f+shown/2f,h/2f+shown/2f);
                p.setAlpha(255); c.drawBitmap(starfallIcon,null,dst,p);
            }
            p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.create("serif",Typeface.BOLD));
            p.setTextSize(Math.max(20,h*.045f)); p.setColor(Color.WHITE); p.setShadowLayer(12,0,0,0xFF4B91FF);
            c.drawText("PROJECT STARFALL",w/2f,h*.92f,p); p.clearShadowLayer();
            // Quiet drifting sparkles; no rapid flashing.
            p.setColor(0x99B9D8FF);
            for(int i=0;i<24;i++) { float x=(i*137f+ms*.018f)%w; float y=(i*71f+ms*.009f)%h; c.drawCircle(x,y,1.2f+(i%3),p); }
        }

        private void drawWarning(Canvas c, long ms) {
            c.drawColor(Color.BLACK);
            float w=getWidth(), h=getHeight();
            p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
            p.setTextSize(Math.max(30, Math.min(46, h*.052f))); p.setColor(Color.rgb(245,239,222));
            c.drawText("Photosensitive Seizure Warning",w/2f,h*.16f,p);
            p.setStrokeWidth(1.5f); p.setColor(0xFF333333); c.drawLine(w*.08f,h*.205f,w*.92f,h*.205f,p);
            String warning="When exposed to certain visual stimuli or flashing lights, a very small percentage of people (including those with epilepsy and some undiagnosed individuals) may experience seizures, with symptoms such as dizziness, facial discoloration, eye or face twitching, foaming at the mouth, confusion or loss of consciousness, or uncontrollable muscle spasms. If you, your family, or other household members experience similar symptoms, please consult a doctor before playing the game. If you experience any of the above symptoms while playing, immediately stop playing, seek medical attention as soon as possible, and follow your doctor's advice.";
            p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD)); p.setTextSize(Math.max(24,Math.min(40,h*.043f))); p.setColor(0xFFE0E0E0);
            java.util.ArrayList<String> lines=new java.util.ArrayList<>(); StringBuilder line=new StringBuilder();
            for(String word:warning.split(" ")) {
                String candidate=line.length()==0?word:line+" "+word;
                if(p.measureText(candidate)>w*.90f && line.length()>0) { lines.add(line.toString()); line.setLength(0); line.append(word); }
                else { if(line.length()>0) line.append(' '); line.append(word); }
            }
            if(line.length()>0) lines.add(line.toString());
            float lineH=p.getTextSize()*1.30f, y=h*.275f;
            for(String text:lines) { c.drawText(text,w/2f,y,p); y+=lineH; }
            long remaining=Math.max(0,7000-ms);
            float cx=w/2f, cy=h*.815f, r=Math.min(28,h*.042f);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(4,r*.24f)); p.setColor(0xFF3A3A3A); c.drawCircle(cx,cy,r,p);
            p.setColor(0xFFE2D5B4); p.setStrokeCap(Paint.Cap.ROUND);
            c.drawArc(cx-r,cy-r,cx+r,cy+r,(ms*.18f)%360,275,false,p); p.setStyle(Paint.Style.FILL);
            boolean ready=remaining==0;
            RectF button=new RectF(w*.39f,h*.885f,w*.61f,h*.97f);
            p.setColor(ready?0xFF244C80:0xFF292929); c.drawRoundRect(button,12,12,p);
            p.setTextSize(Math.max(18,h*.029f)); p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
            p.setColor(ready?Color.WHITE:0xFF777777);
            c.drawText(ready?"CONTINUE":"PLEASE READ  •  "+((remaining+999)/1000)+"s",w/2f,h*.91f,p);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction()==MotionEvent.ACTION_DOWN) { downX=e.getX(); downY=e.getY(); return true; }
            if (e.getAction()==MotionEvent.ACTION_UP) {
                long now = SystemClock.uptimeMillis();
                if (stage < 2) {
                    changeStage(stage + 1, now); // Tap anywhere to skip an intro card.
                } else if (now - stageStarted >= 7000) {
                    // After the mandatory reading interval, tapping anywhere continues.
                    if (!launchedMenu) openMainMenu();
                }
                return true;
            }
            return true;
        }
    }
}
