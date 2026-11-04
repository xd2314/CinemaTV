package com.gathin.tvhome;

/** Carousel-only parameters. Lengths are 1920x1080 design px, multiplied by scene unit.
 * Light strengths are [0,1]; spreads are fractions of the unscaled card; time is ms. */
final class CinemaCarousel {
    static final int MAX_VISIBLE_ITEMS=7;
    static final float CARD_WIDTH=228, CARD_HEIGHT=174, GAP=14, FOCUS_SCALE=1.35f;
    private static final float[] SIDE_SCALE={FOCUS_SCALE,1,.96f,.93f,.90f};
    private static final float[] ROTATION_Y_BY_DISTANCE={0,3,5,8,10};
    static final float CAMERA_DISTANCE=1400, SAFE_PADDING=32, VIEWPORT_HEIGHT=410;
    static float rotation(int distance){int d=Math.min(4,Math.abs(distance));return distance<0?-ROTATION_Y_BY_DISTANCE[d]:ROTATION_Y_BY_DISTANCE[d];}
    static float scale(int distance){return SIDE_SCALE[Math.min(4,Math.abs(distance))];}
    static int count(int total){return Math.min(MAX_VISIBLE_ITEMS,Math.max(0,total));}
    static int first(int selected,int total){return Math.max(0,Math.min(selected-3,total-count(total)));}
    static float light(float dim,float bright,float focus){return dim+(bright-dim)*focus;}
    private CinemaCarousel(){}
}
