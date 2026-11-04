package com.gathin.tvhome;

import org.json.*;
import java.util.*;

/** Four stable slots, ordered in one existing cinema_lite preference record. */
final class QuickLaunchItem {
 final String id; String packageName,activityName,displayName,image;
 QuickLaunchItem(String id,String pkg,String activity,String name,String image){this.id=id;packageName=pkg;activityName=activity;displayName=name;this.image=image;}
 static List<QuickLaunchItem> defaults(List<AppAdapter.Entry> apps){
  List<QuickLaunchItem> out=new ArrayList<>();for(int i=0;i<4;i++){AppAdapter.Entry e=i<apps.size()?apps.get(i):null;out.add(new QuickLaunchItem("q"+i,e==null?"":e.pkg,e==null?"":e.component.getClassName(),"","season:"+i));}return out;
 }
 static List<QuickLaunchItem> read(CinemaSettings settings,List<AppAdapter.Entry> apps){
  try{JSONArray a=new JSONArray(settings.prefs.getString("quick_launch_v1",""));if(a.length()!=4)throw new JSONException("slots");List<QuickLaunchItem> out=new ArrayList<>();Set<String> ids=new HashSet<>();
   for(int i=0;i<4;i++){JSONObject o=a.getJSONObject(i);String id=o.getString("id");if(!id.matches("q[0-3]")||!ids.add(id))throw new JSONException("id");out.add(new QuickLaunchItem(id,o.optString("packageName"),o.optString("activityName"),o.optString("displayName"),o.optString("backgroundImageReference","season:"+id.substring(1))));}return out;
  }catch(JSONException|RuntimeException e){return defaults(apps);}
 }
 static void save(CinemaSettings settings,List<QuickLaunchItem> items){
  for(QuickLaunchItem old:read(settings,Collections.emptyList()))settings.preserveImage(old.image);
  if(items.size()!=4)throw new IllegalArgumentException("four slots");JSONArray a=new JSONArray();Set<String> ids=new HashSet<>();
  try{for(int i=0;i<items.size();i++){QuickLaunchItem q=items.get(i);if(!q.id.matches("q[0-3]")||!ids.add(q.id))throw new IllegalArgumentException("slot id");JSONObject o=new JSONObject();o.put("id",q.id).put("packageName",q.packageName).put("activityName",q.activityName).put("displayName",q.displayName.trim()).put("backgroundImageReference",q.image).put("order",i);a.put(o);}settings.prefs.edit().putString("quick_launch_v1",a.toString()).apply();}catch(JSONException e){throw new IllegalArgumentException(e);}
 }
}
