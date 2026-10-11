package com.fanfold.core;

/**
 * 2D affine transform, y-down screen coordinates.
 *   x' = a*x + c*y + tx
 *   y' = b*x + d*y + ty
 */
public final class Affine {
    public final double a, b, c, d, tx, ty;

    public Affine(double a, double b, double c, double d, double tx, double ty) {
        this.a = a; this.b = b; this.c = c; this.d = d; this.tx = tx; this.ty = ty;
    }

    public static Affine identity() { return new Affine(1, 0, 0, 1, 0, 0); }
    public static Affine translate(double dx, double dy) { return new Affine(1, 0, 0, 1, dx, dy); }
    public static Affine scale(double s) { return new Affine(s, 0, 0, s, 0, 0); }

    /** Rotate clockwise (on screen) about the origin by quarterTurns * 90 degrees. */
    public static Affine rotateQuarter(int quarterTurns) {
        switch (((quarterTurns % 4) + 4) % 4) {
            case 1: return new Affine(0, 1, -1, 0, 0, 0);   // (x,y) -> (-y, x)
            case 2: return new Affine(-1, 0, 0, -1, 0, 0);  // (x,y) -> (-x,-y)
            case 3: return new Affine(0, -1, 1, 0, 0, 0);   // (x,y) -> (y, -x)
            default: return identity();
        }
    }

    /** Returns the transform that applies this first, then next. */
    public Affine then(Affine n) {
        return new Affine(
                n.a * a + n.c * b,
                n.b * a + n.d * b,
                n.a * c + n.c * d,
                n.b * c + n.d * d,
                n.a * tx + n.c * ty + n.tx,
                n.b * tx + n.d * ty + n.ty);
    }

    public double[] map(double x, double y) {
        return new double[]{a * x + c * y + tx, b * x + d * y + ty};
    }

    public Affine inverse() {
        double det = a * d - b * c;
        double ia = d / det, ib = -b / det, ic = -c / det, id = a / det;
        double itx = -(ia * tx + ic * ty);
        double ity = -(ib * tx + id * ty);
        return new Affine(ia, ib, ic, id, itx, ity);
    }

    /** Values in android.graphics.Matrix order (row-major 3x3). */
    public float[] toMatrixValues() {
        return new float[]{(float) a, (float) c, (float) tx, (float) b, (float) d, (float) ty, 0f, 0f, 1f};
    }

    @Override public String toString() {
        return "Affine[a=" + a + " b=" + b + " c=" + c + " d=" + d + " tx=" + tx + " ty=" + ty + "]";
    }
}
