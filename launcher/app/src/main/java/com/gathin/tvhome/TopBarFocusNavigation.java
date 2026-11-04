package com.gathin.tvhome;

/** Physical horizontal edge policy. Native Android focus movement remains responsible for transfers. */
final class TopBarFocusNavigation {
    private static final int LEFT = 21;   // KeyEvent.KEYCODE_DPAD_LEFT
    private static final int RIGHT = 22;  // KeyEvent.KEYCODE_DPAD_RIGHT

    private TopBarFocusNavigation() { }

    static boolean eligible(boolean shown, boolean enabled, boolean focusable, boolean visibleBounds) {
        return shown && enabled && focusable && visibleBounds;
    }

    /** Returns the nearest eligible index on that physical side, current at an edge, or -1 if unhandled. */
    static int horizontalTarget(int keyCode, int current, float[] centers, boolean[] eligible) {
        if (keyCode != LEFT && keyCode != RIGHT) return -1;
        if (centers.length != eligible.length || current < 0 || current >= centers.length
                || !eligible[current] || !Float.isFinite(centers[current])) return -1;
        int target = current;
        float nearest = Float.POSITIVE_INFINITY;
        for (int i = 0; i < centers.length; i++) {
            if (i == current || !eligible[i] || !Float.isFinite(centers[i])) continue;
            float distance = keyCode == LEFT ? centers[current] - centers[i] : centers[i] - centers[current];
            if (distance > 0 && distance < nearest) {
                nearest = distance;
                target = i;
            }
        }
        return target;
    }

    static boolean atHorizontalEdge(int keyCode, int current, float[] centers, boolean[] eligible) {
        int target = horizontalTarget(keyCode, current, centers, eligible);
        return target >= 0 && target == current;
    }
}
