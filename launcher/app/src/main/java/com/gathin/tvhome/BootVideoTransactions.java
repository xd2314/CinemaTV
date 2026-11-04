package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import org.json.JSONObject;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** A durable transaction lane. Opening or cancelling never runs Root. No recovery auto-retry. */
final class BootVideoTransactions {
    interface Store{String read();boolean write(String json);}
    interface Runner{Result run(Request request)throws Exception;}
    interface NoWriteAudit{void verify()throws Exception;}
    interface SourceNoWriteAudit{JSONObject verify()throws Exception;}
    static final class Request {
        final boolean apply;final String id,sha,source,route,restoreBefore,restoreProperty,restoreStock;final int clientUid;
        Request(boolean apply,String id,String sha,String source,int clientUid){this(apply,id,sha,source,clientUid,BootVideoRoute.LOCAL,null,null,null);}
        Request(boolean apply,String id,String sha,String source,int clientUid,String route,String restoreBefore,String restoreProperty,String restoreStock){this.apply=apply;this.id=id;this.sha=sha;this.source=source;this.clientUid=clientUid;this.route=route;this.restoreBefore=restoreBefore;this.restoreProperty=restoreProperty;this.restoreStock=restoreStock;}
    }
    static final class Result {
        final int exit;final boolean timeout;final String output;
        Result(int exit,boolean timeout,String output){this.exit=exit;this.timeout=timeout;this.output=output;}
        String value(String key){String found=null;for(String line:output.split("\n"))if(line.startsWith(key+"=")){if(found!=null)return "";found=line.substring(key.length()+1);}return found==null?"":found;}
        boolean complete(Request request){
            if(timeout||exit!=0||!value("CLBV_UID").equals("0")||!value("CLBV_CLIENT_UID").equals(Integer.toString(request.clientUid))||!value("CLBV_TX").equals(request.id)||!value("CLBV_CANDIDATE_SHA").equals(request.sha))return false;
            String before=value("CLBV_BEFORE_SHA"),after=value("CLBV_AFTER_SHA");
            if(!BootVideoRoot.sha(before)||!value("CLBV_RESULT").equals(request.apply?"APPLIED":"RESTORED")||!after.equals(request.apply?request.sha:before))return false;
            String route=value("CLBV_ROUTE");
            // Historical local-file receipts did not have a route field. Never reinterpret them.
            if(route.isEmpty())return !output.contains("CLBV_ROUTE=")&&BootVideoRoute.LOCAL.equals(request.route);
            if(!BootVideoRoute.known(route)||!(BootVideoRoute.AUTO.equals(request.route)||request.route.equals(route)))return false;
            if(BootVideoRoute.LOCAL.equals(route))return true;
            String propertyBefore=value("CLBV_PROPERTY_BEFORE"),propertyAfter=value("CLBV_PROPERTY_AFTER");
            if(!BootVideoRoute.propertyPath(propertyBefore)||!BootVideoRoute.propertyPath(propertyAfter)||!BootVideoRoot.sha(value("CLBV_STOCK_SHA")))return false;
            if(request.apply&&output.contains("CLBV_READER_PROFILE=")&&!BootVideoReaderProfile.proof(this))return false;
            if(request.apply&&BootVideoRoute.STOCK.equals(propertyBefore)&&!before.equals(value("CLBV_STOCK_SHA")))return false;
            return request.apply?BootVideoRoute.ACTIVE.equals(propertyAfter):
                BootVideoRoute.ACTIVE.equals(propertyBefore)&&BootVideoRoot.sha(request.restoreBefore)&&before.equals(request.restoreBefore)
                    &&BootVideoRoute.propertyPath(request.restoreProperty)&&propertyAfter.equals(request.restoreProperty)&&value("CLBV_STOCK_SHA").equals(request.restoreStock);
        }
    }
    private static BootVideoTransactions instance;
    static synchronized BootVideoTransactions get(Context context){
        if(instance==null){
            Context app=context.getApplicationContext();SharedPreferences prefs=app.getSharedPreferences("boot_video_transaction_v1",0);Handler main=new Handler(Looper.getMainLooper());
            instance=new BootVideoTransactions(new BootVideoRoot(app),new Store(){public String read(){return prefs.getString("receipt","");}public boolean write(String text){return prefs.edit().putString("receipt",text).commit();}},Executors.newSingleThreadExecutor(),main::post,Process.myUid(),true);
        }
        return instance;
    }
    private final Runner runner;private final Store store;private final Executor worker,ui;private final int clientUid;private final boolean discoverRoute;
    private JSONObject receipt;private boolean busy,blocked;private Runnable observer;
    private String message="確認後以一次 su 0 檢查既有影片路徑，備份影片與路徑設定，再套用。支援既有 boot.mp4 或韌體 video_file 屬性；保留系統原廠影片，不自行重啟。";
    BootVideoTransactions(Runner runner,Store store,Executor worker,Executor ui,int clientUid){
        this(runner,store,worker,ui,clientUid,false);
    }
    BootVideoTransactions(Runner runner,Store store,Executor worker,Executor ui,int clientUid,boolean discoverRoute){
        this.runner=runner;this.store=store;this.worker=worker;this.ui=ui;this.clientUid=clientUid;this.discoverRoute=discoverRoute;
        try{
            String saved=store.read();receipt=saved.isEmpty()?new JSONObject().put("schema",1).put("state","IDLE"):new JSONObject(saved);
            if(receipt.getInt("schema")!=1)throw new IllegalStateException();
            String state=receipt.getString("state");
            if(!state.equals("IDLE")&&receipt.optInt("clientUid",-1)!=clientUid)throw new IllegalStateException("Foreign App UID receipt");
            if(!state.matches("IDLE|PENDING|APPLIED|RESTORED|UNKNOWN|FAILED_NO_TARGET_WRITE"))throw new IllegalStateException();
            if((state.equals("APPLIED")||state.equals("RESTORED"))&&!receipt.has("lastId"))throw new IllegalStateException();
            if(receipt.has("lastId")&&(!BootVideoRoot.uuid(receipt.getString("lastId"))||!BootVideoRoot.sha(receipt.getString("lastSha"))||!BootVideoRoot.sha(receipt.getString("lastBefore"))))throw new IllegalStateException();
            if(receipt.has("lastRoute")){
                String route=receipt.getString("lastRoute");if(!BootVideoRoute.known(route))throw new IllegalStateException("Unknown saved video route");
                if(BootVideoRoute.PROPERTY.equals(route)&&(!BootVideoRoute.propertyPath(receipt.getString("lastBeforeProperty"))||!BootVideoRoot.sha(receipt.getString("lastStockSha"))))throw new IllegalStateException("Invalid property route receipt");
                if(BootVideoRoute.PROPERTY.equals(route)&&BootVideoRoute.STOCK.equals(receipt.getString("lastBeforeProperty"))&&!receipt.getString("lastBefore").equals(receipt.getString("lastStockSha")))throw new IllegalStateException("Invalid stock restore binding");
            }
            if(receipt.has("lastReaderProfile")&&!receipt.getString("lastReaderProfile").equals(BootVideoReaderProfile.NAME))throw new IllegalStateException("Unknown reader contract");
            blocked=state.equals("PENDING")||state.equals("UNKNOWN")||state.equals("FAILED_NO_TARGET_WRITE");
            if(blocked)message="先前交易未完成或結果需人工核對，已鎖定派送。請保留紀錄，不要清資料、重送或重啟。";
            else if(state.equals("APPLIED"))message="先前動畫已套用並讀回核對；開機畫面仍需另外重啟人工驗收。可還原至此次套用前。";
            else if(state.equals("RESTORED"))message="先前動畫已還原並讀回核對；還原後的開機畫面仍待另行驗收。";
        }catch(Exception malformed){receipt=new JSONObject();blocked=true;message="交易紀錄無法驗證，已停用派送；請保留現場與備份。";}
    }
    synchronized boolean canApply(){return BootDeviceSupport.appUid(clientUid)&&!busy&&!blocked;}
    synchronized boolean canRestore(){return canApply()&&receipt.optString("state").equals("APPLIED")&&receipt.has("lastId");}
    synchronized boolean busy(){return busy;}
    synchronized String message(){return message;}
    synchronized String preparationNote(String sha){
        if(blocked)return "上次動畫交易需人工核對；套用／還原已鎖定。";
        if(receipt.optString("state").equals("APPLIED")&&receipt.optString("lastSha").equals(sha))return "上次操作紀錄：此動畫已套用並讀回核對；開機畫面仍待人工驗收。";
        return "這段素材尚未套用；可進入動畫套用／還原頁確認。";
    }
    synchronized void observe(Runnable callback){observer=callback;}
    synchronized void removeObserver(Runnable callback){if(observer==callback)observer=null;}
    synchronized String exportReceipt(){return receipt.toString();}
    // A single diagnosed v36 guard failure may be acknowledged only by a separate
    // explicit confirmation and a fresh non-Root audit. Never unlock UNKNOWN/PENDING.
    synchronized boolean canAcknowledgeNoWrite(){
        String id=receipt.optString("transaction");
        return !busy&&blocked&&clientUid==10106&&receipt.optString("state").equals("FAILED_NO_TARGET_WRITE")
            &&receipt.optString("action").equals("APPLY")&&receipt.optInt("clientUid")==10106
            &&receipt.optInt("exit",-1)==16&&!receipt.optBoolean("timeout",true)&&!receipt.has("lastId")
            &&BootVideoRoot.uuid(id)&&BootVideoRoot.sha(receipt.optString("candidateSha"))
            &&receipt.optString("diagnostic").equals("CLBV_UID=0\nCLBV_CLIENT_UID=10106\nCLBV_TX="+id+"\nCLBV_NO_TARGET_WRITE\n");
    }
    boolean acknowledgeNoWrite(NoWriteAudit audit){
        synchronized(this){if(!canAcknowledgeNoWrite())return false;busy=true;}
        boolean success=false;
        try{
            audit.verify();
            synchronized(this){
                JSONObject next=new JSONObject().put("schema",1).put("state","IDLE")
                    .put("acknowledgedNoWrite",new JSONObject(receipt.toString())).put("acknowledgedAt",System.currentTimeMillis());
                if(!store.write(next.toString()))throw new IllegalStateException();
                receipt=next;blocked=false;success=true;
                message="已唯讀核對舊影片未變、沒有交易目錄；上次未寫入紀錄已保留。\n尚未要求 Root；需要另行確認才可套用，不會自動重送。";
            }
        }catch(Exception rejected){synchronized(this){message="唯讀核對或紀錄保存未通過；維持鎖定，沒有要求 Root。";}}
        synchronized(this){busy=false;}return success;
    }
    synchronized boolean canReviewSourceNoWrite(String probe,BootAssetStore.Asset asset){
        return !busy&&blocked&&BootVideoNoWriteReview.verified(receipt.toString(),probe,asset,clientUid);
    }
    boolean acknowledgeSourceNoWrite(String probe,BootAssetStore.Asset asset,SourceNoWriteAudit audit){
        final String original;
        synchronized(this){if(!canReviewSourceNoWrite(probe,asset))return false;original=receipt.toString();busy=true;}
        boolean success=false;
        try{
            JSONObject snapshot=audit.verify();
            JSONObject source=snapshot.getJSONObject("source");
            if(!snapshot.getBoolean("readOnly")||!snapshot.getBoolean("newRootJournalAbsent")||!snapshot.getBoolean("dataTargetDirectoryAbsent")
                ||!snapshot.getString("property").equals(BootVideoRoute.STOCK)||!BootVideoRoot.sha(snapshot.getString("stockSha"))
                ||source.getInt("appUid")!=clientUid||source.getInt("uid")!=clientUid||source.getInt("gid")!=clientUid||!source.getString("mode").equals("600")
                ||!source.getBoolean("regular")||!source.getBoolean("canonicalMatches")||source.getInt("readlinkExit")!=0||!source.getString("actualSha").equals(asset.sha)
                ||!source.getString("javaCanonical").equals(asset.output.getCanonicalPath())||source.getLong("length")<=0||source.getLong("length")>BootAssetStore.MAX_BYTES)throw new IllegalStateException("Invalid source review audit");
            synchronized(this){
                if(!original.equals(receipt.toString())||!BootVideoNoWriteReview.verified(original,probe,asset,clientUid))throw new IllegalStateException();
                JSONObject next=new JSONObject().put("schema",1).put("state","IDLE").put("clientUid",clientUid)
                    .put("acknowledgedSourceNoWrite",new JSONObject(original)).put("sourceProbeEvidence",new JSONObject(probe))
                    .put("sourceReviewAudit",snapshot).put("acknowledgedAt",System.currentTimeMillis());
                if(!store.write(next.toString()))throw new IllegalStateException();
                receipt=next;blocked=false;success=true;
                message="未寫入紀錄已由你確認，原失敗、Root唯讀檢查及本次核對均已保留。\n沒有追加Root或套用；之後需另外確認才可更換影片。";
            }
        }catch(Exception rejected){synchronized(this){message="未寫入紀錄核對或保存未通過，維持鎖定；沒有要求Root，也未套用。";}}
        synchronized(this){busy=false;}return success;
    }
    private void notifyUi(){Runnable callback; synchronized(this){callback=observer;}if(callback!=null)callback.run();}
    synchronized boolean apply(BootAssetStore.Asset asset){
        if(!canApply()||asset==null||asset.target!=BootAssetStore.Target.VIDEO||!BootVideoRoot.uuid(asset.id)||!BootVideoRoot.sha(asset.sha))return false;
        // An applied transaction pins its route; rediscovery must not bypass drift guards.
        String route=receipt.optString("state").equals("APPLIED")?receipt.optString("lastRoute",BootVideoRoute.LOCAL):discoverRoute?BootVideoRoute.AUTO:BootVideoRoute.LOCAL;
        try{return submit(new Request(true,UUID.randomUUID().toString(),asset.sha,asset.output.getCanonicalPath(),clientUid,route,null,null,null));}
        catch(java.io.IOException invalid){message="準備素材的私有路徑無法驗證，未派送。";notifyUi();return false;}
    }
    synchronized boolean restore(){
        if(!canRestore())return false;
        return submit(new Request(false,receipt.optString("lastId"),receipt.optString("lastSha"),null,clientUid,receipt.optString("lastRoute",BootVideoRoute.LOCAL),receipt.optString("lastBefore"),receipt.optString("lastBeforeProperty"),receipt.optString("lastStockSha")));
    }
    private boolean submit(Request request){
        try{
            receipt.put("schema",1).put("state","PENDING").put("action",request.apply?"APPLY":"RESTORE").put("transaction",request.id).put("candidateSha",request.sha).put("clientUid",clientUid).put("route",request.route).put("at",System.currentTimeMillis());
            if(!store.write(receipt.toString()))throw new IllegalStateException();
        }catch(Exception storage){blocked=true;message="無法保存交易紀錄，未要求 Root，也未派送。";notifyUi();return false;}
        busy=true;message="正在要求本 App 的 Root，備份／核對並執行一次操作。請勿重複按鍵；系統不會自行重啟。";notifyUi();
        try{worker.execute(()->{
            Result result;try{result=runner.run(request);}catch(Exception failure){result=new Result(-1,false,"未完成Root交易；請保留紀錄並人工核對。");}
            Result finished=result;ui.execute(()->finish(request,finished));
        });}catch(RuntimeException dispatch){busy=false;blocked=true;message="工作派送未完成，已鎖定；請保留紀錄。";notifyUi();}
        return true;
    }
    private synchronized void finish(Request request,Result result){
        busy=false;boolean success=result.complete(request);
        String state=success?(request.apply?"APPLIED":"RESTORED"):(!result.timeout&&result.output.contains("CLBV_NO_TARGET_WRITE\n")&&!result.output.contains("CLBV_TARGET_WRITE_ATTEMPTED")?"FAILED_NO_TARGET_WRITE":"UNKNOWN");
        blocked=!success;
        try{
            receipt.put("state",state).put("exit",result.exit).put("timeout",result.timeout).put("diagnostic",result.output).put("finishedAt",System.currentTimeMillis());
            if(success&&request.apply){
                String route=result.value("CLBV_ROUTE");if(route.isEmpty())route=BootVideoRoute.LOCAL;
                receipt.put("lastId",request.id).put("lastSha",request.sha).put("lastBefore",result.value("CLBV_BEFORE_SHA")).put("lastRoute",route);
                if(BootVideoRoute.PROPERTY.equals(route))receipt.put("lastBeforeProperty",result.value("CLBV_PROPERTY_BEFORE")).put("lastStockSha",result.value("CLBV_STOCK_SHA"));
                else {receipt.remove("lastBeforeProperty");receipt.remove("lastStockSha");}
                if(BootVideoReaderProfile.proof(result))receipt.put("lastReaderProfile",BootVideoReaderProfile.NAME);else receipt.remove("lastReaderProfile");
            }
            if(!store.write(receipt.toString()))blocked=true;
        }catch(Exception storage){blocked=true;}
        message=success?(request.apply?"開機動畫已套用，檔案讀回核對通過。\n尚待另行重啟及人工觀看驗收；本頁不會自行重啟。":"已還原至此次套用前的動畫，讀回核對通過。\n還原後開機畫面仍待另行驗收；本頁不會自行重啟。"):
            state.equals("FAILED_NO_TARGET_WRITE")?"檢查未通過，沒有更換開機影片。已停止派送，請匯出紀錄並人工確認。":"交易結果不明或部分失敗，已鎖定派送。請保留備份與紀錄，不重送、不清資料、不自行重啟。";
        if(blocked&&success)message+="\n本機紀錄保存失敗，已鎖定後續派送。";
        notifyUi();
    }
}
