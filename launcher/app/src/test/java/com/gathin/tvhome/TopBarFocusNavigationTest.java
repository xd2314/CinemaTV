package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** JVM policy tests with physical coordinates; not Android View or real remote integration tests. */
public class TopBarFocusNavigationTest {
    private static final int LEFT = 21, RIGHT = 22;

    private static boolean[] all(int count) {
        boolean[] result = new boolean[count];
        java.util.Arrays.fill(result, true);
        return result;
    }

    @Test public void ltrInputsLeftAllowsNativeMoveToAllApps() {
        float[] x = {40, 150, 260, 370}; // Library, Inputs, Network, Settings
        assertEquals(0, TopBarFocusNavigation.horizontalTarget(LEFT, 1, x, all(4)));
        assertFalse(TopBarFocusNavigation.atHorizontalEdge(LEFT, 1, x, all(4)));
    }

    @Test public void ltrAllAppsRightAllowsNativeMoveBackToInputs() {
        float[] x = {40, 150, 260, 370};
        assertEquals(1, TopBarFocusNavigation.horizontalTarget(RIGHT, 0, x, all(4)));
        assertFalse(TopBarFocusNavigation.atHorizontalEdge(RIGHT, 0, x, all(4)));
    }

    @Test public void trueLeftEdgeIsConsumedWithoutLeavingTopBar() {
        float[] x = {40, 150, 260, 370};
        assertEquals(0, TopBarFocusNavigation.horizontalTarget(LEFT, 0, x, all(4)));
        assertTrue(TopBarFocusNavigation.atHorizontalEdge(LEFT, 0, x, all(4)));
    }

    @Test public void trueRightEdgeIsConsumedWithoutLeavingTopBar() {
        float[] x = {40, 150, 260, 370};
        assertEquals(3, TopBarFocusNavigation.horizontalTarget(RIGHT, 3, x, all(4)));
        assertTrue(TopBarFocusNavigation.atHorizontalEdge(RIGHT, 3, x, all(4)));
    }

    @Test public void hiddenAllAppsMakesInputsTheActualLeftEdge() {
        float[] x = {40, 150, 260, 370};
        boolean[] enabled = {false, true, true, true};
        assertTrue(TopBarFocusNavigation.atHorizontalEdge(LEFT, 1, x, enabled));
        assertEquals(2, TopBarFocusNavigation.horizontalTarget(RIGHT, 1, x, enabled));
    }

    @Test public void hiddenInputsIsSkippedInBothDirections() {
        float[] x = {40, 150, 260, 370};
        boolean[] enabled = {true, false, true, true};
        assertEquals(2, TopBarFocusNavigation.horizontalTarget(RIGHT, 0, x, enabled));
        assertEquals(0, TopBarFocusNavigation.horizontalTarget(LEFT, 2, x, enabled));
    }

    @Test public void disabledButtonsCannotBecomeNeighboursOrEdges() {
        float[] x = {40, 150, 260, 370};
        boolean[] enabled = {
                TopBarFocusNavigation.eligible(true, false, true, true), true, true,
                TopBarFocusNavigation.eligible(true, false, true, true)};
        assertTrue(TopBarFocusNavigation.atHorizontalEdge(LEFT, 1, x, enabled));
        assertTrue(TopBarFocusNavigation.atHorizontalEdge(RIGHT, 2, x, enabled));
    }

    @Test public void nonFocusableClockAndButtonsAreExcluded() {
        float[] x = {0, 40, 150, 260}; // Non-focusable clock before the visible buttons
        boolean[] enabled = {TopBarFocusNavigation.eligible(true, true, false, true), true,
                TopBarFocusNavigation.eligible(true, true, false, true), true};
        assertTrue(TopBarFocusNavigation.atHorizontalEdge(LEFT, 1, x, enabled));
        assertEquals(3, TopBarFocusNavigation.horizontalTarget(RIGHT, 1, x, enabled));
    }

    @Test public void hiddenOrClippedControlsAreExcluded() {
        assertFalse(TopBarFocusNavigation.eligible(false, true, true, true));
        assertFalse(TopBarFocusNavigation.eligible(true, true, true, false));
        assertTrue(TopBarFocusNavigation.eligible(true, true, true, true));
    }

    @Test public void rtlUsesPhysicalCoordinatesWithoutDoubleReversal() {
        float[] x = {370, 260, 150, 40}; // Reading order is not physical left-to-right
        assertEquals(3, TopBarFocusNavigation.horizontalTarget(LEFT, 2, x, all(4)));
        assertEquals(2, TopBarFocusNavigation.horizontalTarget(RIGHT, 3, x, all(4)));
        assertTrue(TopBarFocusNavigation.atHorizontalEdge(LEFT, 3, x, all(4)));
        assertTrue(TopBarFocusNavigation.atHorizontalEdge(RIGHT, 0, x, all(4)));
    }

    @Test public void rtlWithMissingControlsStillUsesRealEdges() {
        float[] x = {370, 260, 150, 40};
        boolean[] enabled = {true, false, true, false};
        assertEquals(2, TopBarFocusNavigation.horizontalTarget(LEFT, 0, x, enabled));
        assertEquals(0, TopBarFocusNavigation.horizontalTarget(RIGHT, 2, x, enabled));
        assertTrue(TopBarFocusNavigation.atHorizontalEdge(LEFT, 2, x, enabled));
    }

    @Test public void childInsertionOrderDoesNotDefinePhysicalOrder() {
        float[] x = {260, 40, 370, 150};
        assertEquals(1, TopBarFocusNavigation.horizontalTarget(LEFT, 3, x, all(4)));
        assertEquals(0, TopBarFocusNavigation.horizontalTarget(RIGHT, 3, x, all(4)));
        assertTrue(TopBarFocusNavigation.atHorizontalEdge(RIGHT, 2, x, all(4)));
    }

    @Test public void dynamicallyInsertedAppPillsJoinTheEdgePolicy() {
        float[] x = {40, 150, 450, 240, 300}; // Settings is not the last stored index
        assertEquals(4, TopBarFocusNavigation.horizontalTarget(LEFT, 2, x, all(5)));
        assertEquals(2, TopBarFocusNavigation.horizontalTarget(RIGHT, 4, x, all(5)));
        assertTrue(TopBarFocusNavigation.atHorizontalEdge(RIGHT, 2, x, all(5)));
    }

    @Test public void changingVisibilityIsNotCachedBetweenKeys() {
        float[] x = {40, 150, 260};
        boolean[] enabled = all(3);
        assertFalse(TopBarFocusNavigation.atHorizontalEdge(LEFT, 1, x, enabled));
        enabled[0] = false;
        assertTrue(TopBarFocusNavigation.atHorizontalEdge(LEFT, 1, x, enabled));
        enabled[0] = true;
        assertFalse(TopBarFocusNavigation.atHorizontalEdge(LEFT, 1, x, enabled));
    }

    @Test public void loneSettingsButtonHasBothEdgesAndNoLoop() {
        float[] x = {40};
        assertTrue(TopBarFocusNavigation.atHorizontalEdge(LEFT, 0, x, all(1)));
        assertTrue(TopBarFocusNavigation.atHorizontalEdge(RIGHT, 0, x, all(1)));
    }

    @Test public void upAndDownAreNotHandledByHorizontalPolicy() {
        for (int key : new int[] {19, 20}) {
            assertEquals(-1, TopBarFocusNavigation.horizontalTarget(key, 0, new float[] {40}, all(1)));
            assertFalse(TopBarFocusNavigation.atHorizontalEdge(key, 0, new float[] {40}, all(1)));
        }
    }

    @Test public void okBackAndOtherKeysAreNotConsumed() {
        for (int key : new int[] {23, 66, 4, 111, 24, 25, 3}) {
            assertFalse(TopBarFocusNavigation.atHorizontalEdge(key, 0, new float[] {40}, all(1)));
        }
    }

    @Test public void absentOrIneligibleSourceDoesNotInventAnEdge() {
        assertFalse(TopBarFocusNavigation.atHorizontalEdge(LEFT, -1, new float[] {40}, all(1)));
        assertFalse(TopBarFocusNavigation.atHorizontalEdge(RIGHT, 0, new float[] {40}, new boolean[] {false}));
        assertFalse(TopBarFocusNavigation.atHorizontalEdge(LEFT, 0, new float[0], new boolean[0]));
    }

    @Test public void invalidCoordinatesCannotBecomeFocusTargets() {
        float[] x = {Float.NaN, 40, Float.POSITIVE_INFINITY, 150};
        assertTrue(TopBarFocusNavigation.atHorizontalEdge(LEFT, 1, x, all(4)));
        assertEquals(3, TopBarFocusNavigation.horizontalTarget(RIGHT, 1, x, all(4)));
        assertFalse(TopBarFocusNavigation.atHorizontalEdge(LEFT, 0, x, all(4)));
    }

    @Test public void coincidentCoordinatesDoNotCreateHorizontalCycles() {
        float[] x = {40, 40, 150};
        assertEquals(2, TopBarFocusNavigation.horizontalTarget(RIGHT, 0, x, all(3)));
        assertTrue(TopBarFocusNavigation.atHorizontalEdge(LEFT, 0, x, all(3)));
    }
}
