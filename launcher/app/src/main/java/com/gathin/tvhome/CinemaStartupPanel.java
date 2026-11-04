package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Draft-only editor. The verified v47 startup controller is unchanged. */
final class CinemaStartupPanel extends CinemaSetupPanel implements AutoCloseable {
    final CinemaCatalogue catalogue;
    final LinearLayout delays;
    final ImageView preview;
    final TextView previewName,previewMode,delayTitle,explanation,footnote;
    private final String original;
    private final int originalSeconds;
    private String draft;
    private int seconds;
    private boolean interacted;
    CinemaStartupPanel(Activity activity,Runnable dismiss){
        super(activity,dismiss,"開機自動開啟 App","讓開機後的第一站，由你決定。");
        original=draft=CinemaStartup.target(activity);originalSeconds=seconds=CinemaStartup.delay(activity);
        cancel.setTag("startup:cancel");save.setTag("startup:save");discard.setTag("startup:discard");
        listTitle.setText("選擇開機後的畫面");listHint.setText("讀取應用程式中…");
        previewMode=text("下次開機",20,0xFF9CD8D7);details.addView(previewMode);
        preview=new ImageView(activity);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);details.addView(preview);
        previewName=text("",38,Color.WHITE);previewName.setMaxLines(2);previewName.setEllipsize(android.text.TextUtils.TruncateAt.END);details.addView(previewName);
        explanation=text("",24,0xFFD1DDEA);explanation.setMaxLines(3);details.addView(explanation);
        delayTitle=text("開啟前等待",24,Color.WHITE);details.addView(delayTitle);
        delays=new LinearLayout(activity);details.addView(delays);
        for(int delay:CinemaStartup.DELAYS){TextView b=button(delay==0?"立即":delay+" 秒","startup:delay:"+delay);delays.addView(b);CinemaUiSounds.bindClick(b,v->{interacted=true;seconds=delay;changed();});}
        footnote=text("",21,0xFFAEBFD4);details.addView(footnote);
        addChoice("","停留在 Cinema TV 首頁",null);refresh();
        post(()->{if(!closed&&!interacted)selectedRow().requestFocus();});
        catalogue=new CinemaCatalogue(activity);catalogue.load(new HashSet<>(Config.load(activity).libraryExcludedApps),this::setApps,message->{if(!closed)listHint.setText("暫時無法讀取 App，請返回後再試。");});
    }
    private void addChoice(String pkg,String label,android.graphics.drawable.Drawable art){AppRow row=new AppRow(pkg,label,art);list.addView(row);CinemaUiSounds.bindClick(row,v->{interacted=true;draft=pkg;changed();});}
    void setApps(List<AppAdapter.Entry> apps){
        if(closed)return;View focused=findFocus();String tag=focused==null?null:String.valueOf(focused.getTag());
        while(list.getChildCount()>1){AppRow row=(AppRow)list.getChildAt(1);buttons.remove(row);fonts.remove(row.name);fonts.remove(row.mark);list.removeViewAt(1);}
        Set<String> seen=new HashSet<>();for(AppAdapter.Entry app:apps)if(!app.pkg.equals(activity.getPackageName())&&seen.add(app.pkg))addChoice(app.pkg,app.label,app.icon);
        listHint.setText(seen.isEmpty()?"尚無可選 App · 可選擇停留首頁":"上下瀏覽 · OK 選擇 · 左右切換區域");refresh();requestLayout();
        if(!interacted)selectedRow().requestFocus();else if(tag!=null&&tag.startsWith("startup:app:")){View restore=list.findViewWithTag(tag);(restore==null?selectedRow():restore).requestFocus();}
    }
    private AppRow selectedRow(){AppRow r=list.findViewWithTag("startup:app:"+draft);return r==null?(AppRow)list.getChildAt(0):r;}
    @Override boolean dirty(){return !original.equals(draft)||originalSeconds!=seconds;}
    @Override void saveDraft(){
        if(!dirty()){dismiss.run();return;}
        if(CinemaStartup.save(activity,draft,seconds)){Toast.makeText(activity,draft.isEmpty()?"已設定為停留首頁":"已儲存，下次開機生效",Toast.LENGTH_SHORT).show();dismiss.run();}
        else{confirmingExit=false;refreshExit();status.setText("未能儲存，原設定保留。\n請確認 App 仍可開啟後再試。");}
    }
    @Override void refresh(){
        if(preview==null)return;AppRow selected=list.findViewWithTag("startup:app:"+draft);boolean off=draft.isEmpty();
        preview.setImageDrawable(copy(selected==null?null:selected.icon.getDrawable()));previewName.setText(off?"Cinema TV 首頁":selected!=null?selected.label:CinemaStartup.label(activity,draft));
        previewMode.setText(dirty()?"下次開機 · 尚未儲存":"目前設定 · 下次開機");
        explanation.setText(off?"開機後停留在首頁，\n自由選擇想看的內容。":(seconds==0?"進入首頁後立即開啟。":"進入首頁後等待 "+seconds+" 秒，再開啟 App。")+"\n每次開機只啟動一次。");
        delays.setVisibility(off?GONE:VISIBLE);delayTitle.setVisibility(off?GONE:VISIBLE);
        footnote.setText(off?(list.getChildCount()>1?"選擇左側任一 App，即可設定自動開啟。":"可用 App 會顯示在左側；目前保持開機停留首頁。"):"建議保留 5 秒；網路較慢可選 10 或 30 秒。\n倒數時按任意鍵，可取消這次自動開啟。\n按 Home 返回不會再次啟動。");
        if(!confirmingExit)status.setText(dirty()?"尚未儲存\n儲存後，下次開機生效。":"設定已儲存\nCinema TV 需為預設首頁。");
        save.setText(dirty()?"儲存設定":"完成");
        for(int i=0;i<list.getChildCount();i++){AppRow row=(AppRow)list.getChildAt(i);row.chosen(row.key.equals(draft));}
        for(int i=0;i<delays.getChildCount();i++){TextView b=(TextView)delays.getChildAt(i);int d=CinemaStartup.DELAYS[i];b.setSelected(d==seconds);b.setText((d==seconds?"✓ ":"")+(d==0?"立即":d+" 秒"));style(b);}
        requestLayout();
    }
    @Override View detailFocus(){return draft.isEmpty()?save:delays.findViewWithTag("startup:delay:"+seconds);}
    @Override View navigateDetail(View f,int key){if(f.getParent()==delays){if(key==19)return listFocus();if(key==20)return save;if(key==21||key==22){View n=horizontal(delays,f,key);if(n==f&&!forward(key))return listFocus();return n;}}return f;}
    @Override public boolean dispatchKeyEvent(KeyEvent e){if(e.getAction()==KeyEvent.ACTION_DOWN)interacted=true;return super.dispatchKeyEvent(e);}
    @Override void layoutDetails(){
        place(details,previewMode,32,24,830,34);place(details,preview,32,80,112,112);place(details,previewName,170,78,682,116);place(details,explanation,32,213,830,96);place(details,delayTitle,32,331,830,38);place(details,delays,26,380,840,70);place(details,footnote,32,draft.isEmpty()?355:466,830,110);
        for(int i=0;i<delays.getChildCount();i++){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(px(6),0,px(6),0);delays.getChildAt(i).setLayoutParams(p);}
    }
    @Override public void close(){closed=true;catalogue.close();}
}
