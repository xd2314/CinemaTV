package com.gathin.tvhome;

/** Local-clock intervals; no alarm, network, location or astronomical sunrise claim. */
final class CinemaSchedule {
    private CinemaSchedule() { }
    static boolean valid(int day,int night){return day>=0&&day<1440&&night>=0&&night<1440&&day!=night;}
    static boolean daylight(int minute,int day,int night){
        if(!valid(day,night))throw new IllegalArgumentException("Distinct times required");
        int now=Math.floorMod(minute,1440);
        return day<night?now>=day&&now<night:now>=day||now<night;
    }
    static String label(int minute){return String.format(java.util.Locale.ROOT,"%02d:%02d",minute/60,minute%60);}
}
