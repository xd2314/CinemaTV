package com.gathin.tvhome;

import org.json.JSONObject;
import java.util.UUID;

/** One foreground consent, bounded steps, no replay across Activity/process recreation. */
final class BootLogoApplyFlow {
    interface Store { boolean write(String json); }
    interface Host {
        JSONObject snapshot();
        boolean allowed();
        boolean busy();
        boolean prepare();
        boolean restore();
        boolean apply();
        void verifyBackup(JSONObject initial);
        void exportBackup(String runId);
        void changed(String message);
    }
    private final Host host;
    private final Store store;
    private final String sourceSha;
    private JSONObject initial, record;
    private String step="IDLE", expectedTx="", message="";
    private boolean active;
    BootLogoApplyFlow(Host host,Store store,String sourceSha){this.host=host;this.store=store;this.sourceSha=sourceSha;}
    boolean active(){return active;}
    String message(){return message;}
    String id(){return record==null?"":record.optString("run");}
    String receipt(){return record==null?"":record.toString();}
    private boolean save(String next,String text){
        try{
            step=next;message=text;
            record.put("step",next).put("expectedTransaction",expectedTx).put("message",text).put("updatedAt",System.currentTimeMillis());
            if(!store.write(record.toString()))throw new IllegalStateException();
            return true;
        }catch(Exception e){active=false;step="STOPPED";message="操作紀錄無法保存，已停止後續步驟；不自動重送。";host.changed(message);return false;}
    }
    boolean start(){
        if(record!=null||!host.allowed()||host.busy())return false;
        try{
            initial=new JSONObject(host.snapshot().toString());
            String state=initial.optString("state");
            if(!state.matches("IDLE|RESTORED|READY|APPLIED"))return false;
            if(state.equals("APPLIED")&&sourceSha.equals(initial.optString("sourceSha")))return false;
            record=new JSONObject().put("schema",1).put("run",UUID.randomUUID().toString()).put("sourceSha",sourceSha)
                .put("initialTransaction",initial.optString("transaction")).put("initialState",state).put("consentAt",System.currentTimeMillis());
            active=true;
            if(!save("START","正在開始套用…"))return false;
            if(state.equals("APPLIED"))dispatch("RESTORE");
            else if(state.equals("READY")&&sourceSha.equals(initial.optString("sourceSha"))){expectedTx=initial.optString("transaction");verify();}
            else dispatch("READ");
            return true;
        }catch(Exception e){stop("無法建立這次操作，沒有自動重送。");return false;}
    }
    private boolean sameReady(){JSONObject s=host.snapshot();return s.optString("state").equals("READY")&&s.optString("transaction").equals(expectedTx)&&s.optString("sourceSha").equals(sourceSha);}
    private void dispatch(String action){
        if(!active||!host.allowed()||host.busy()){stop("狀態已改變，停止後續步驟。");return;}
        String previous=host.snapshot().optString("transaction");
        if(!action.equals("READ"))expectedTx=previous;
        String text=action.equals("RESTORE")?"正在還原上一張圖片，接著準備新圖…":action.equals("READ")?"正在備份目前圖片並準備新圖…":"正在套用新圖片並讀回核對，請勿斷電…";
        // Persist the claim BEFORE entering any model which can dispatch Root.
        if(!save(action,text))return;
        boolean sent=action.equals("RESTORE")?host.restore():action.equals("READ")?host.prepare():host.apply();
        if(!sent){stop("這一步未能開始，已停止；不自動重送。");return;}
        if(action.equals("READ")){
            expectedTx=host.snapshot().optString("transaction");
            if(expectedTx.isEmpty()||expectedTx.equals(previous)){stop("新備份交易無法確認，已停止。");return;}
            if(!save(action,text))return;
        }
        host.changed(text);
    }
    // Called only by observed model completion, never by a timer or persisted run replay.
    void advance(){
        if(!active||host.busy())return;
        if(!host.allowed()){stop("頁面或設備條件已改變，停止後續步驟。");return;}
        JSONObject s=host.snapshot();String state=s.optString("state");
        if(step.equals("RESTORE")){
            if(!state.equals("RESTORED")||!s.optString("transaction").equals(expectedTx)){stop("還原未明確成功，停止後續步驟。");return;}
            dispatch("READ");
        }else if(step.equals("READ")){
            if(!sameReady()){stop("備份未明確成功，停止後續步驟。");return;}
            verify();
        }else if(step.equals("APPLY")){
            if(!state.equals("APPLIED")||!s.optString("transaction").equals(expectedTx)||!s.optString("sourceSha").equals(sourceSha)){stop("套用未明確成功，已停止；請保留備份與紀錄。");return;}
            active=false;save("COMPLETE","已套用並讀回核對。開機顯示請由你確認；不會自行重啟。");host.changed(message);
        }
    }
    private void verify(){
        if(!active||!sameReady()){stop("候選已改變，停止後續步驟。");return;}
        if(save("VERIFY","正在核對可沿用的備份…"))host.verifyBackup(initial);
    }
    void verified(boolean usable,boolean needsExport){
        if(!active||!step.equals("VERIFY"))return;
        if(!host.allowed()||!sameReady()||!usable){stop("備份無法核對，已停止套用。");return;}
        if(needsExport){if(save("EXPORT","請選擇新備份的儲存位置；核對完成後會接續套用。")){host.changed(message);host.exportBackup(id());}}
        else if(host.snapshot().optBoolean("backupExported"))dispatch("APPLY");
        else stop("備份匯出紀錄無法確認，已停止。");
    }
    void exported(String runId,boolean success){
        if(!active||!step.equals("EXPORT")||!id().equals(runId))return;
        if(!success||!host.allowed()||!sameReady()||!host.snapshot().optBoolean("backupExported")){stop("備份儲存取消或核對未通過，已停止後續套用。已完成的步驟不自動回復。");return;}
        dispatch("APPLY");
    }
    void stop(String reason){if(!active)return;active=false;save("STOPPED",reason);host.changed(message);}
    void close(){stop("頁面已離開，停止尚未開始的步驟；進行中的操作仍以其收據為準。重新開頁不會自動接續。");}
}
