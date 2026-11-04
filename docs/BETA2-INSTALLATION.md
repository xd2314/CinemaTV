# Cinema TV Beta2 · 安裝與更新

版本：v73／7.0.0-beta.2-original-art；顯示名稱Cinema TV Beta；套件`com.gathin.tvhome.cinemalite.dev`。

APK：`CinemaTV-v73-7.0.0-beta.2-original-art.apk`

SHA256：`d39ac24036d5ef7e15fa6de26320c7a85ad08f3c79ba3b6e02e9ecf0620403ed`

維護者原憑證SHA256：`e698b07fba7c263d9b92218b75262407f6874c3886146794395c5daad1345046`

這是維護者簽署、debuggable=false的側載Beta，不代表Google Play上架或所有TV支援已驗證。請核對最新RELEASE-READINESS與validation.json，未知項目不算PASS。

## 使用者

- 新使用者可透過信任的檔案管理器開啟APK，依Android安裝畫面明確確認。普通Launcher使用不需要Root，不必授予開發工具權限。
- 已有相同套件且相同憑證時可普通更新，不清資料；本版會取代該套件的舊版，並非獨立共存App。
- 若提示憑證不同、版本不相容或安裝失敗，先停止；不以卸載、清資料、降版或停用原廠Launcher解決。
- 開發者自行建置的APK可能是另一憑證，不能假設可覆蓋此維護者APK。
- 公開版恢復原本預設圖片，與v71原資源逐位元組相同，PO已允許MIT公開。原有使用者自訂圖／設定會依現有儲存機制保留。

使用者自行授權並已核對當次唯一設備後，可普通ADB更新（先將佔位符換成自己的已批准端點與APK實際路徑）：

```sh
adb -s <approved-endpoint> install -r <path-to-CinemaTV-v73-7.0.0-beta.2-original-art.apk>
```

沒有`-g`／`-d`，不自動指派HOME。若系統出現首頁選擇器或原偏好失效，先記錄，不把重新選取首頁當作自動保留的PASS。原廠Launcher必須保留。

## 最小驗收

實體Home直接返回、DPAD／OK／Back、設定與App清單、自己使用的影音App啟動／基本播放／聲音及返回。沒有最低播放時間要求。沒有安裝的App記NOT AVAILABLE；沒檢查則NOT TESTED。

## 問題與回退

發生卡住、首頁異常、聲音／影音啟動回歸或無法操作時停止，記錄版本及不含私人資料的原始現象。優先使用系統預設首頁入口選回仍保留的OEM。ADB失聯與無離線入口時不能保證立即回退，不能盲目掃網段、Root或重啟。

v73不能視為可直接普通降回v71；需要修復時由維護者提供較高版本的修正版，或在確認OEM可用後另議清理，不擅自清除使用者資料。

最早開機圖片／影片、系統過場桌布與無線ADB仍是分開確認的進階能力，本次一般Beta冒煙不驗證或授權這些操作。
