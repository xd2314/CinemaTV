# Cinema TV v73 Beta2 · 保留原圖的公開測試版

版本7.0.0-beta.2-original-art、versionCode73。原預設圖片已恢復，不以程序插畫代替。

## 當次通過

- 16個原始圖片檔與v71來源及實際APK逐位元組相同；PO已確認圖片恢復、Home正常。
- PO確認原圖為專案生成／自有並同意MIT公開，聲明與檔案SHA256已記錄。
- 原導航程式未更動，v72的實體DPAD／OK／Back／設定與App清單PASS保留；v73另確認Home及四App基本播放、聲音、換台、返回與Home全部正常。
- 同簽章普通更新72→73一次；HOME resolver、UID、原授予權限、OEM與boot_id保留，只有本App版本改變。
- 960項App測試、Lint0error；APK原憑證、版本、Manifest、權限與對齊核對通過。
- 程序範例產生器改為只輸出build預覽目錄，拒絕覆蓋App原圖，另有發布工具回歸測試。

## 判定與界線

人工結果見BETA2-ACCEPTANCE.md；機器、乾淨來源重建及封裝的最終數據見validation.json。960項App測試、10項發布工具測試、乾淨來源重建及簽署APK內容一致性均通過，支持小範圍側載Beta，不等於正式穩定版或所有機型通用。

沒有重新開機、待機、Root、HOME指派、影音長測或原廠App／網路修改。其他機型、待機／重啟、長期、通用Root素材與韌體救援維持NOT TESTED。GitHub CI與上傳未執行，不自動公開。

Beta1圖片外觀FAIL／NO_GO保留於BETA1-ACCEPTANCE.md，原APK／來源封存不改寫；Beta2修正結果另記。安裝與更新參考BETA2-INSTALLATION.md，保留原廠Launcher，不清資料或盲目降版。

## 收尾讀值限制

PO完成原圖／Home／四App確認後，ADB連線已不在當次transport列表。停止設備操作，沒有掃網段、重新啟用ADB、重裝或重啟。收尾錯誤歷史與記憶體為NOT OBTAINED，不能宣稱本輪沒有新增ANR或完成長期記憶體驗收。安裝後的身份／版本／APK雜湊／權限／OEM／HOME／boot_id快照已在失聯前核對通過；人工功能結果分開保留。這個限制不把HOME或四App的已回報PASS改成ADB恢復PASS。
