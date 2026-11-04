package com.gathin.tvhome;
/** Renderer-only controls. Strengths in [0,1]; width ratios may exceed 1; lengths are design px * scene unit. */
final class CinemaLighting {
 static final boolean LIGHTING_ENABLED=true,REFLECTION_ENABLED=false;
 static final float FOCUS_BORDER_STRENGTH=.95f,FOCUS_CONTACT_LIGHT_STRENGTH=1f,FOCUS_GLOW_STRENGTH=1f;
 static final float CONTACT_LIGHT_WIDTH_RATIO=.56f,CONTACT_LIGHT_HEIGHT=5f;
 static final float GLOW_WIDTH_RATIO=1.65f,GLOW_HEIGHT_RATIO=.75f;
 static final float VERTICAL_LIGHT_STRENGTH=.70f,VERTICAL_LIGHT_HEIGHT=.90f,SPILL_STRENGTH=.15f;
 static final float NONFOCUS_GLOW_STRENGTH=.25f,CONTACT_SPREAD_STRENGTH=.88f,CONTACT_SPREAD_WIDTH_RATIO=.90f;
 static final float REFLECTION_ALPHA=.18f,REFLECTION_FADE_RATIO=.32f;
 static final long FOCUS_ANIMATION_MS=200,GLOW_TRANSITION_MS=220,REFLECTION_SETTLE_MS=80;
 // Fixed production renderer D; ignore archived A–E preference values.
 static int stage(CinemaSettings o){return 3;}
 static boolean border(CinemaSettings o){return LIGHTING_ENABLED&&o.ambient()&&stage(o)>=2;}
 static boolean pool(CinemaSettings o){return LIGHTING_ENABLED&&o.ambient()&&stage(o)>=3;}
 static boolean reflection(CinemaSettings o){return o.reflection()&&stage(o)==4;}
 static boolean animation(CinemaSettings o){return o.motion()&&stage(o)>0;}
 private CinemaLighting(){}
}
