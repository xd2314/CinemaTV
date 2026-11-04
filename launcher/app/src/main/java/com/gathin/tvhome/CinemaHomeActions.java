package com.gathin.tvhome;

import android.content.ComponentName;
import java.util.ArrayList;
import java.util.List;

/** UI actions are separate from the installed-app catalogue and launch history. */
final class CinemaHomeActions {
    static final String ALL_APPS="cinema.internal.all_apps";
    private CinemaHomeActions(){}
    static List<AppAdapter.Entry> carousel(List<AppAdapter.Entry> apps){
        List<AppAdapter.Entry> result=new ArrayList<>(apps);
        result.add(new AppAdapter.Entry("所有應用程式",new ComponentName(ALL_APPS,"AllApps"),null,new CinemaIcon(CinemaIcon.GRID)));
        return result;
    }
    static boolean isAllApps(AppAdapter.Entry entry){return ALL_APPS.equals(entry.pkg);}
}
