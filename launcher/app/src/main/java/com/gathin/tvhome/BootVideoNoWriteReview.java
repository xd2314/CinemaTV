package com.gathin.tvhome;

import org.json.JSONObject;

/** A strict review of the old exit20 receipt plus a separate completed read-only probe. */
final class BootVideoNoWriteReview {
    private BootVideoNoWriteReview(){}
    private static String lineAfter(String output,String marker){
        String[] lines=output.split("\n");String found=null;
        for(int i=0;i<lines.length;i++)if(lines[i].equals(marker)){
            if(found!=null||i+1>=lines.length)return "";found=lines[i+1];
        }
        return found==null?"":found;
    }
    static boolean verified(String receipt,String probe,BootAssetStore.Asset asset,int uid){
        try{
            if(!BootVideoSourceProbe.eligible(receipt,asset,uid))return false;
            JSONObject original=new JSONObject(receipt),p=new JSONObject(probe);
            if(!p.getString("state").equals("FINISHED")||p.getInt("exit")!=0||p.getBoolean("timeout")||!p.getBoolean("readOnly")||!p.getString("transaction").equals(original.getString("transaction")))return false;
            String out=p.getString("output"),source=asset.output.getCanonicalPath();
            // Reuse the fixed private-path/UUID/UID validation, without running it.
            BootVideoRoot.script("",new BootVideoTransactions.Request(true,original.getString("transaction"),asset.sha,source,uid));
            BootVideoTransactions.Result values=new BootVideoTransactions.Result(0,false,out);
            if(!values.value("CLVS_UID").equals("0")||!values.value("CLVS_CLIENT_UID").equals(Integer.toString(uid))||!values.value("CLVS_TX").equals(original.getString("transaction"))
                ||!values.value("CLVS_CANDIDATE_SHA").equals(asset.sha)||!values.value("CLVS_SOURCE").equals(source)||!values.value("CLVS_REGULAR").equals("1")
                ||!values.value("CLVS_SYMLINK").equals("0")||!values.value("CLVS_COMPLETE").equals("1")||!lineAfter(out,"CLVS_CANONICAL").equals(source))return false;
            String[] meta=lineAfter(out,"CLVS_METADATA").split(":");
            if(meta.length!=4||!meta[0].equals(Integer.toString(uid))||!meta[1].equals(Integer.toString(uid))||!meta[2].equals("600"))return false;
            long bytes=Long.parseLong(meta[3]);
            return bytes>0&&bytes<=BootAssetStore.MAX_BYTES&&lineAfter(out,"CLVS_SHA256").equals(asset.sha+"  "+source);
        }catch(Exception invalid){return false;}
    }
}
