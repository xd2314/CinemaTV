package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Edit a detached draft; only Save changes the shared existing Order store. */
final class CinemaOrderPanel extends CinemaSetupPanel {
    final AppAdapter adapter;
    final List<String> original=new ArrayList<>();
    final LinearLayout moves;
    final ImageView preview;
    final TextView position,previewName,instruction,note;
    boolean carrying;
    AppRow selected;
    CinemaOrderPanel(Activity activity,AppAdapter adapter,Runnable dismiss){
        super(activity,dismiss,"首頁捷徑排序","把最常看的 App，放在最順手的位置。");this.adapter=adapter;
        cancel.setTag("order:cancel");save.setTag("order:save");discard.setTag("order:discard");
        listTitle.setText("首頁 App · 由前到後");listHint.setText("OK 拿起 · 上下移動 · 再按 OK 放下");
        position=text("",22,0xFF9CD8D7);details.addView(position);preview=new ImageView(activity);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);details.addView(preview);
        previewName=text("",38,Color.WHITE);previewName.setMaxLines(2);previewName.setEllipsize(android.text.TextUtils.TruncateAt.END);details.addView(previewName);
        instruction=text("",25,0xFFE0EAF5);details.addView(instruction);
        moves=new LinearLayout(activity);details.addView(moves);
        TextView earlier=button("往前移","order:earlier"),later=button("往後移","order:later");moves.addView(earlier);moves.addView(later);
        CinemaUiSounds.bindClick(earlier,v->move(-1));CinemaUiSounds.bindClick(later,v->move(1));
        note=text("「所有應用程式」保留在最後。\n下方最近項目依使用紀錄排列，不受影響。\n儲存後立即更新首頁。",22,0xFFAEBFD4);details.addView(note);
        for(AppAdapter.Entry app:adapter.cinemaEntries()){
            String key=app.component.flattenToShortString();original.add(key);AppRow row=new AppRow(key,app.label,app.icon);row.setTag("order:app:"+key);list.addView(row);
            row.setOnFocusChangeListener((v,f)->{style(v);if(f){selected=row;lastListFocus=row;refresh();row.post(()->{if(!closed&&row.hasFocus()){android.graphics.Rect rect=new android.graphics.Rect();row.getDrawingRect(rect);row.requestRectangleOnScreen(rect,true);}});}});
            CinemaUiSounds.bindClick(row,v->{selected=row;carrying=!carrying;changed();});
        }
        selected=list.getChildCount()==0?null:(AppRow)list.getChildAt(0);refresh();post(()->{if(!closed)listFocus().requestFocus();});
    }
    List<String> keys(){List<String> out=new ArrayList<>();for(int i=0;i<list.getChildCount();i++)out.add(((AppRow)list.getChildAt(i)).key);return out;}
    @Override boolean dirty(){return !keys().equals(original);}
    void move(int delta){
        if(selected==null)return;int from=list.indexOfChild(selected),to=from+delta;if(to<0||to>=list.getChildCount()){status.setText(to<0?"已經是第一個捷徑":"已經是最後一個 App 捷徑");return;}
        boolean focus=selected.hasFocus();AppRow row=selected;list.removeView(row);list.addView(row,to);selected=row;lastListFocus=row;changed();if(focus)row.requestFocus();
    }
    @Override void back(){if(carrying){carrying=false;refresh();return;}super.back();}
    @Override void saveDraft(){
        carrying=false;
        if(!dirty()){dismiss.run();return;}
        if(adapter.saveCinemaOrder(keys())){Toast.makeText(activity,"首頁捷徑順序已儲存",Toast.LENGTH_SHORT).show();dismiss.run();}
        else{confirmingExit=false;refreshExit();status.setText("未能儲存，原順序保留。\nApp 清單可能已變更，請返回後再試。");}
    }
    @Override void refresh(){
        if(position==null)return;int at=selected==null?-1:list.indexOfChild(selected);
        position.setText(at<0?"尚無可排序的 App":"第 "+(at+1)+" 個 ／ 共 "+list.getChildCount()+" 個");previewName.setText(selected==null?"先將 App 加入首頁":selected.label);preview.setImageDrawable(copy(selected==null?null:selected.icon.getDrawable()));
        instruction.setText(carrying?"正在移動 · 按上下調整位置\n按 OK 放下，Back 停止移動。":"選擇左側 App，按 OK 拿起。\n或使用下方按鈕調整位置。");
        for(int i=0;i<list.getChildCount();i++){AppRow row=(AppRow)list.getChildAt(i);row.setSelected(row==selected&&carrying);row.mark.setText(row==selected&&carrying?"↕":String.valueOf(i+1));row.setContentDescription(row.label+"，第 "+(i+1)+" 個"+(row==selected&&carrying?"，正在移動":""));style(row);}
        if(!confirmingExit)status.setText(dirty()?"尚未儲存\n儲存後更新首頁順序。":"順序已儲存\n選取 App 不會啟動它。");save.setText(dirty()?"儲存順序":"完成");moves.setVisibility(selected==null?GONE:VISIBLE);
    }
    @Override View listFocus(){return selected==null?save:selected;}
    @Override View detailFocus(){return selected==null?save:moves.getChildAt(0);}
    @Override View navigateDetail(View f,int key){if(f.getParent()==moves){if(key==19)return listFocus();if(key==20)return save;if(key==21||key==22){View n=horizontal(moves,f,key);return n==f&&!forward(key)?listFocus():n;}}return f;}
    @Override public boolean dispatchKeyEvent(KeyEvent e){if(carrying&&e.getAction()==KeyEvent.ACTION_DOWN&&findFocus()==selected){int k=e.getKeyCode();if(k==19||k==20){move(k==19?-1:1);return true;}if(k==21||k==22){carrying=false;refresh();}}return super.dispatchKeyEvent(e);}
    @Override void layoutDetails(){place(details,position,32,24,830,40);place(details,preview,32,86,112,112);place(details,previewName,170,82,682,120);place(details,instruction,32,234,830,90);place(details,moves,26,350,840,76);place(details,note,32,461,830,112);for(int i=0;i<moves.getChildCount();i++){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(px(6),0,px(6),0);moves.getChildAt(i).setLayoutParams(p);}}
}
