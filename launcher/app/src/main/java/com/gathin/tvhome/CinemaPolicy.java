package com.gathin.tvhome;

/** Deterministic geometry and bounded resource policy; no Android or device state. */
final class CinemaPolicy {
    static final float FOCUS_SCALE = CinemaCarousel.FOCUS_SCALE, OVERSCAN = 1.06f, PARALLAX = .015f;
    static final long TRANSITION_MS = 140;
    static final long MAX_DECODE_PIXELS = 4_194_304L;
    private CinemaPolicy() { }
    static float offset(float normalized, int width) {
        return Math.max(-1f, Math.min(1f, normalized)) * width * PARALLAX;
    }
    static int sampleSize(int w, int h, int targetW, int targetH) {
        if (w <= 0 || h <= 0) return 1;
        long budget=Math.min(MAX_DECODE_PIXELS,Math.max(65536L,Math.max(1,targetW)*(long)Math.max(1,targetH)*2));
        int n = 1;
        while (((long) w + n - 1) / n * (((long) h + n - 1) / n) > budget
                || w / n > 4096 || h / n > 4096
                || (w / n > Math.max(1,targetW) * 2L && h / n > Math.max(1,targetH) * 2L)) {
            if (n >= 1 << 28) break;
            n *= 2;
        }
        return n;
    }
    static int horizontal(int key, int current, float[] x, boolean[] eligible) {
        return TopBarFocusNavigation.horizontalTarget(key, current, x, eligible);
    }
    static int nearest(float center, float[] x, boolean[] eligible) {
        int best=-1; float distance=Float.POSITIVE_INFINITY;
        for(int i=0;i<x.length;i++) if(eligible[i] && Float.isFinite(x[i])) {
            float d=Math.abs(x[i]-center);
            if(d<distance){distance=d;best=i;}
        }
        return best;
    }
    static boolean mayLaunch(long last, long now, boolean inFlight) {
        return !inFlight && (last < 0 || now-last >= 500);
    }
}
