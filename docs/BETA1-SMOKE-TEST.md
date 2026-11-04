# Beta1 精簡實機驗收

候選：versionCode72、versionName7.0.0-beta.1，套件com.gathin.tvhome.cinemalite.dev，顯示名稱Cinema TV Beta。使用固定原憑證簽署release，debuggable=false；公開素材為可重建插畫。

## 一般使用者界線

這是小範圍Beta，不宣稱所有Android TV、待機／重啟、長期、Root開機素材或完整韌體救援均通過。先保留原廠Launcher；不為測試取得Root、修改網路、停用原廠App或清資料。

## 當次前置與一次更新

- 確認PO在電視前、實體遙控器可用，以及當次唯一設備身分、既有版本／憑證、OEM保留與HOME解析。
- 核對交付APK SHA256、版本、憑證、權限、HOME Manifest及本機測試。
- 保存當次boot_id、版本清單與授予權限，將任何原有問題記為原有問題。
- 同套件且同憑證才可普通更新一次；不-g／-d、不卸載／清資料。派送前持久保存一次性收據，逾時先唯讀核對，不重送。
- 本輪不指派HOME、不Root、不重啟、不改設定。若已在本App時鐘，可一次普通Home返回；Asleep則由PO正常喚醒，不注入Power／Wakeup。

## 機器與人工分開記錄

機器確認：安裝後版本72、原UID與憑證、HOME resolver保留、APK拉回雜湊相符、boot_id不變、僅本App版本變更、原授予權限與OEM保留。Role查詢若韌體不支援則記NOT OBTAINED，不假稱PASS。

人工A：實體Home直接進Cinema TV Beta；Carousel左右、UP／DOWN、OK／Back正常；設定與App清單可開啟／返回，從設定按Home仍回Beta。

人工B：TTV、UPLIVE、UPMOVIE、YouTube各開啟並確認實際播放、有聲、導航及返回；TTV／UPLIVE試換台；每個App最後按實體Home回Beta。不設最低播放分鐘、不重跑長矩陣；沒有安裝的App標NOT AVAILABLE，未測則NOT TESTED，不推定PASS。

## 停止與收尾

任一HOME、焦點、App啟動異常、ADB失聯或身份不符即停止。保留畫面與原始讀值，不盲目重送、不改HOME掩蓋結果、不自動降版／卸載。必要OEM回退須確認當次元件與授權，不在此提供私人固定端點。

通過後保留Beta安裝及既有HOME，不建立回退計時器。此結果僅支持小範圍Beta，不能延伸成正式永久版或自動公開上傳授權。
