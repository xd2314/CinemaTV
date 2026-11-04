package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class CinemaScheduleTest {
 @Test public void exactDefaultBoundaries(){assertFalse(CinemaSchedule.daylight(359,360,1080));assertTrue(CinemaSchedule.daylight(360,360,1080));assertTrue(CinemaSchedule.daylight(1079,360,1080));assertFalse(CinemaSchedule.daylight(1080,360,1080));}
 @Test public void midnightUsesNightUntilMorning(){assertFalse(CinemaSchedule.daylight(0,360,1080));assertFalse(CinemaSchedule.daylight(1439,360,1080));}
 @Test public void customDayCanSpanMidnight(){assertTrue(CinemaSchedule.daylight(1380,1200,240));assertTrue(CinemaSchedule.daylight(0,1200,240));assertFalse(CinemaSchedule.daylight(240,1200,240));assertTrue(CinemaSchedule.daylight(1200,1200,240));}
 @Test public void equalAndOutOfRangeRejected(){assertFalse(CinemaSchedule.valid(360,360));assertFalse(CinemaSchedule.valid(-1,1080));assertFalse(CinemaSchedule.valid(360,1440));assertTrue(CinemaSchedule.valid(0,1439));}
 @Test public void wholeDayHasExactlyOneMode(){for(int minute=0;minute<1440;minute++)assertEquals(minute>=360&&minute<1080,CinemaSchedule.daylight(minute,360,1080));}
 @Test public void stableTwentyFourHourLabels(){assertEquals("06:00",CinemaSchedule.label(360));assertEquals("18:00",CinemaSchedule.label(1080));assertEquals("00:05",CinemaSchedule.label(5));assertEquals("23:59",CinemaSchedule.label(1439));}
}
