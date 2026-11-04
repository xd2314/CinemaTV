package com.gathin.tvhome;
import org.json.JSONObject;
/** PO-only observation binding. Never a transaction state, approval or retry guard. */
final class BootViewingRecord {
 static String binding(BootAssetStore.Target t,BootAssetStore.Asset a,JSONObject r){
  if(a==null||a.target!=t||!r.optString("state").matches("APPLIED|RESTORED"))return "";
  String id=r.optString(t==BootAssetStore.Target.VIDEO?"lastId":"transaction");
  String sha=r.optString(t==BootAssetStore.Target.LOGO?"sourceSha":t==BootAssetStore.Target.VIDEO?"lastSha":"candidateSha");
  if(!BootVideoRoot.uuid(id)||!BootVideoRoot.sha(sha)||!sha.equals(a.sha))return "";
  return t.name()+":"+id+":"+r.optString("state")+":"+sha;
 }
 private BootViewingRecord(){}
}
