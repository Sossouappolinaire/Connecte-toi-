package com.wifimanager.app;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Couleurs, composants et animations de l'interface (tout est dessiné en code, aucun fichier de ressources). */
public final class Ui {

    private Ui() {
    }

    // ---------- Palette ----------
    public static final int BG1 = 0xFF0B1020;
    public static final int BG2 = 0xFF1B1646;
    public static final int CARD = 0xFF1A2238;
    public static final int STROKE = 0xFF2E3A59;
    public static final int TEXT = 0xFFF1F5F9;
    public static final int MUTED = 0xFF94A3B8;
    public static final int PRIMARY1 = 0xFF6366F1;
    public static final int PRIMARY2 = 0xFF8B5CF6;
    public static final int CYAN = 0xFF22D3EE;
    public static final int BLUE = 0xFF3B82F6;
    public static final int GREEN = 0xFF22C55E;
    public static final int AMBER = 0xFFF59E0B;
    public static final int RED = 0xFFEF4444;
    public static final int PINK = 0xFFF43F5E;
    public static final int LAVENDER = 0xFFA5B4FC;

    // ---------- Outils ----------

    public static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static GradientDrawable shape(Context c, int color, float radiusDp, int strokeColor, float strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        if (strokeDp > 0) g.setStroke(dp(c, strokeDp), strokeColor);
        return g;
    }

    public static GradientDrawable gradient(Context c, int c1, int c2, float radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{c1, c2});
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    public static Drawable ripple(Context c, Drawable content, float radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), content,
                shape(c, 0xFFFFFFFF, radiusDp, 0, 0));
    }

    /** Petit effet de rebond quand on appuie. */
    public static void pressable(View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        view.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
                        break;
                    default:
                        break;
                }
                return false;
            }
        });
    }

    /** Apparition en fondu + glissement, avec un léger décalage selon la position. */
    public static void reveal(View v, int index) {
        v.setAlpha(0f);
        v.setTranslationY(dp(v.getContext(), 16));
        v.animate().alpha(1f).translationY(0f)
                .setStartDelay(Math.min(index, 10) * 45L)
                .setDuration(350)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    public static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        l.setBackground(shape(c, CARD, 20, STROKE, 1));
        return l;
    }

    public static Button bigButton(Context c, String label, int c1, int c2, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextSize(15);
        b.setStateListAnimator(null);
        b.setElevation(dp(c, 3));
        b.setMinHeight(dp(c, 50));
        b.setBackground(ripple(c, gradient(c, c1, c2, 16), 16));
        b.setOnClickListener(l);
        pressable(b);
        return b;
    }

    public static TextView chip(Context c, String label, int color, View.OnClickListener l) {
        TextView t = text(c, label, 13, color, true);
        int h = dp(c, 14);
        int v = dp(c, 8);
        t.setPadding(h, v, h, v);
        t.setBackground(ripple(c, shape(c, (color & 0x00FFFFFF) | 0x26000000, 20, color, 1), 20));
        t.setClickable(true);
        t.setOnClickListener(l);
        pressable(t);
        return t;
    }

    public static TextView pill(Context c, String label, int color) {
        TextView t = text(c, label, 11, color, true);
        int h = dp(c, 9);
        int v = dp(c, 3);
        t.setPadding(h, v, h, v);
        t.setBackground(shape(c, (color & 0x00FFFFFF) | 0x26000000, 20, color, 1));
        return t;
    }

    public static View dot(Context c, int color) {
        View v = new View(c);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        v.setBackground(g);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 10), dp(c, 10)));
        return v;
    }

    public static void setDotColor(View v, int color) {
        ((GradientDrawable) v.getBackground()).setColor(color);
    }

    public static void pulse(View v) {
        ObjectAnimator a = ObjectAnimator.ofFloat(v, "alpha", 1f, 0.3f);
        a.setDuration(900);
        a.setRepeatCount(ValueAnimator.INFINITE);
        a.setRepeatMode(ValueAnimator.REVERSE);
        a.start();
    }

    /** Barre de progression animée en continu (visible pendant un scan ou un comptage). */
    public static ProgressBar progress(Context c, int color) {
        ProgressBar p = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        p.setIndeterminate(true);
        p.setIndeterminateTintList(ColorStateList.valueOf(color));
        p.setVisibility(View.INVISIBLE);
        p.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(c, 6)));
        return p;
    }

    // ---------- Vues personnalisées ----------

    /** 4 barres de signal, colorées selon la qualité. */
    public static class SignalBars extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d;
        private int level;

        public SignalBars(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
        }

        public void setLevel(int l) {
            level = l;
            invalidate();
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension((int) (30 * d), (int) (24 * d));
        }

        @Override
        protected void onDraw(Canvas c) {
            float bw = 5 * d;
            float gap = 2.5f * d;
            int color = level >= 3 ? GREEN : (level == 2 ? AMBER : RED);
            for (int i = 0; i < 4; i++) {
                float hgt = (8 + i * 5.3f) * d;
                float left = i * (bw + gap);
                p.setColor(i < level ? color : 0xFF33405F);
                c.drawRoundRect(left, getHeight() - hgt, left + bw, getHeight(), 2 * d, 2 * d, p);
            }
        }
    }

    /** Jauge circulaire animée (0 à 100 %). */
    public static class Gauge extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint big = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint small = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF box = new RectF();
        private final float d;
        private float value = 0f;
        private int target = -1;
        private ValueAnimator anim;

        public Gauge(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            track.setStyle(Paint.Style.STROKE);
            track.setStrokeWidth(10 * d);
            track.setStrokeCap(Paint.Cap.ROUND);
            track.setColor(0xFF2E3A59);
            arc.setStyle(Paint.Style.STROKE);
            arc.setStrokeWidth(10 * d);
            arc.setStrokeCap(Paint.Cap.ROUND);
            big.setColor(TEXT);
            big.setTextAlign(Paint.Align.CENTER);
            big.setTextSize(26 * d);
            big.setTypeface(Typeface.DEFAULT_BOLD);
            small.setColor(MUTED);
            small.setTextAlign(Paint.Align.CENTER);
            small.setTextSize(11 * d);
        }

        public void setPercent(int pct) {
            pct = Math.max(0, Math.min(100, pct));
            if (pct == target) return;
            target = pct;
            if (anim != null) anim.cancel();
            anim = ValueAnimator.ofFloat(value, pct);
            anim.setDuration(700);
            anim.setInterpolator(new DecelerateInterpolator());
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator a) {
                    value = (Float) a.getAnimatedValue();
                    invalidate();
                }
            });
            anim.start();
        }

        @Override
        protected void onMeasure(int w, int h) {
            int s = (int) (112 * d);
            setMeasuredDimension(s, s);
        }

        @Override
        protected void onDraw(Canvas c) {
            float pad = 10 * d;
            box.set(pad, pad, getWidth() - pad, getHeight() - pad);
            c.drawArc(box, 135, 270, false, track);
            arc.setColor(value >= 66 ? GREEN : (value >= 33 ? AMBER : RED));
            if (value > 0) c.drawArc(box, 135, 270 * value / 100f, false, arc);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            c.drawText((int) value + "%", cx, cy + 8 * d, big);
            c.drawText("signal", cx, cy + 26 * d, small);
        }
    }

    /** Barre de progression horizontale animée avec dégradé. */
    public static class Bar extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final float d;
        private float shown = 0f;
        private float target = -1f;
        private ValueAnimator anim;

        public Bar(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            track.setColor(0xFF2E3A59);
        }

        public void setFraction(float f) {
            f = Math.max(0f, Math.min(1f, f));
            if (f == target) return;
            target = f;
            if (anim != null) anim.cancel();
            anim = ValueAnimator.ofFloat(shown, f);
            anim.setDuration(700);
            anim.setInterpolator(new DecelerateInterpolator());
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator a) {
                    shown = (Float) a.getAnimatedValue();
                    invalidate();
                }
            });
            anim.start();
        }

        @Override
        protected void onMeasure(int w, int h) {
            setMeasuredDimension(View.MeasureSpec.getSize(w), (int) (8 * d));
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            fill.setShader(new LinearGradient(0, 0, w, 0, CYAN, PRIMARY2, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas c) {
            float h = getHeight();
            r.set(0, 0, getWidth(), h);
            c.drawRoundRect(r, h / 2, h / 2, track);
            if (shown > 0f) {
                r.set(0, 0, Math.max(h, getWidth() * shown), h);
                c.drawRoundRect(r, h / 2, h / 2, fill);
            }
        }
    }
}
