package com.fanfold.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small helpers for building the UI in code (no XML layouts). */
final class Ui {
    static final int BG = 0xFFF1F3F6;
    static final int CARD = 0xFFFFFFFF;
    static final int PRIMARY = 0xFF1F4E79;
    static final int ACCENT = 0xFFE8772E;
    static final int TEXT = 0xFF1B1F24;
    static final int TEXT2 = 0xFF5B6573;
    static final int LINE = 0xFFDDE2E8;
    static final int OK = 0xFF1E7B3A;
    static final int ERR = 0xFFB3261E;

    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    static GradientDrawable rounded(int fill, float radiusPx, int stroke, int strokePx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radiusPx);
        if (strokePx > 0) g.setStroke(strokePx, stroke);
        return g;
    }

    static Drawable ripple(Drawable content, int rippleColor, float radiusPx) {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, rounded(Color.WHITE, radiusPx, 0, 0));
    }

    static TextView text(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    /** kind: 0 = primary filled, 1 = accent filled (big action), 2 = outlined, 3 = text-only */
    static Button button(Context c, String label, int kind) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, kind == 1 ? 18 : 15);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setStateListAnimator(null);
        float r = dp(c, 12);
        int padH = dp(c, 14), padV = dp(c, kind == 1 ? 14 : 8);
        b.setPadding(padH, padV, padH, padV);
        b.setMinHeight(dp(c, kind == 1 ? 58 : 44));
        b.setMinimumHeight(dp(c, kind == 1 ? 58 : 44));
        switch (kind) {
            case 0:
                b.setTextColor(Color.WHITE);
                b.setBackground(ripple(rounded(PRIMARY, r, 0, 0), 0x40FFFFFF, r));
                break;
            case 1:
                b.setTextColor(Color.WHITE);
                b.setBackground(ripple(rounded(ACCENT, r, 0, 0), 0x40FFFFFF, r));
                break;
            case 2:
                b.setTextColor(PRIMARY);
                b.setBackground(ripple(rounded(Color.WHITE, r, PRIMARY, dp(c, 1.5f)), 0x201F4E79, r));
                break;
            default:
                b.setTextColor(PRIMARY);
                b.setBackground(ripple(rounded(0x00FFFFFF, r, 0, 0), 0x201F4E79, r));
                break;
        }
        return b;
    }

    static LinearLayout card(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        l.setBackground(rounded(CARD, dp(c, 16), LINE, dp(c, 1)));
        l.setElevation(dp(c, 1));
        return l;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    static LinearLayout.LayoutParams matchWrap(Context c, int topMarginDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(c, topMarginDp);
        return p;
    }

    static LinearLayout.LayoutParams weighted(Context c, float weight, int sideMarginDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        p.leftMargin = dp(c, sideMarginDp);
        p.rightMargin = dp(c, sideMarginDp);
        return p;
    }

    static View spacer(Context c, int dpHeight) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, dpHeight)));
        return v;
    }

    static TextView sectionTitle(Context c, String s) {
        TextView t = text(c, s.toUpperCase(), 12, TEXT2, true);
        t.setLetterSpacing(0.08f);
        return t;
    }
}
