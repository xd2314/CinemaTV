package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.provider.Settings;

/** System-reported connectivity only: no probes, polling, identifiers or settings writes. */
final class CinemaNetwork {
    enum Link { NONE, WIFI, ETHERNET, OTHER, UNKNOWN }
    static final class State {
        final Link link;
        final boolean validated;
        State(Link link,boolean validated){this.link=link;this.validated=validated;}
        int icon(){
            if(link==Link.NONE)return CinemaIcon.NETWORK_OFFLINE;
            if(link==Link.WIFI)return validated?CinemaIcon.WIFI:CinemaIcon.WIFI_LIMITED;
            if(link==Link.ETHERNET)return validated?CinemaIcon.ETHERNET:CinemaIcon.ETHERNET_LIMITED;
            return validated?CinemaIcon.NETWORK:CinemaIcon.NETWORK_LIMITED;
        }
        String label(Context context){
            int id=link==Link.NONE?R.string.cinema_network_offline:link==Link.UNKNOWN?R.string.cinema_network_unknown:
                link==Link.WIFI?(validated?R.string.cinema_wifi_online:R.string.cinema_wifi_limited):
                link==Link.ETHERNET?(validated?R.string.cinema_ethernet_online:R.string.cinema_ethernet_limited):
                validated?R.string.cinema_network_online:R.string.cinema_network_limited;
            return context.getString(id);
        }
        String description(Context context){return label(context)+"，"+context.getString(R.string.cinema_network_settings);}
        @Override public boolean equals(Object value){return value instanceof State&&link==((State)value).link&&validated==((State)value).validated;}
        @Override public int hashCode(){return link.hashCode()*31+(validated?1:0);}
    }
    private CinemaNetwork(){}
    static State from(NetworkCapabilities caps){
        if(caps==null)return new State(Link.UNKNOWN,false);
        // A VPN's effective transport is deliberately generic; do not guess its underlying Wi-Fi.
        Link link=caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)?Link.OTHER:
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)?Link.ETHERNET:
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)?Link.WIFI:Link.OTHER;
        return new State(link,caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED));
    }
    static State read(Context context){
        try{
            ConnectivityManager manager=context.getSystemService(ConnectivityManager.class);
            if(manager==null)return new State(Link.UNKNOWN,false);
            Network active=manager.getActiveNetwork();
            return active==null?new State(Link.NONE,false):from(manager.getNetworkCapabilities(active));
        }catch(SecurityException|IllegalArgumentException unavailable){return new State(Link.UNKNOWN,false);}
    }
    static Intent settingsIntent(Context context){
        PackageManager pm=context.getPackageManager();
        for(String action:new String[]{Settings.ACTION_WIRELESS_SETTINGS,Settings.ACTION_WIFI_SETTINGS,Settings.ACTION_SETTINGS}){
            Intent intent=new Intent(action);
            ResolveInfo match=pm.resolveActivity(intent,PackageManager.MATCH_DEFAULT_ONLY);
            if(match==null||match.activityInfo==null)continue;
            android.content.pm.ActivityInfo info=match.activityInfo;
            if(info.enabled&&info.exported&&!info.name.contains("CTSDummy")&&
                (info.applicationInfo==null||info.applicationInfo.enabled))return intent;
        }
        // Existing vendor settings fallback; opening it never changes connectivity automatically.
        Intent vendor=SystemSettings.vendorIntent(context);
        if(vendor==null)return null;
        try{
            android.content.pm.ActivityInfo info=pm.getActivityInfo(vendor.getComponent(),0);
            return info.enabled&&info.exported&&(info.applicationInfo==null||info.applicationInfo.enabled)?vendor:null;
        }catch(PackageManager.NameNotFoundException|SecurityException unavailable){return null;}
    }
}
