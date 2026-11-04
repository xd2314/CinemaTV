# Cinema TV

適合電視遙控器操作的輕量電影風格首頁。

[English](README.md) · [建置說明](docs/BUILDING.md) · [相容範圍](docs/COMPATIBILITY.md) · [權限與安全](docs/SAFETY-AND-PERMISSIONS.md)

**[下載 Cinema TV v73 APK](https://github.com/xd2314/CinemaTV/releases/download/v73-beta.2/CinemaTV-v73-7.0.0-beta.2-original-art.apk)** · [發布說明與 SHA-256](https://github.com/xd2314/CinemaTV/releases/tag/v73-beta.2)

## 安裝、切換與回到原廠首頁

1. **確認電視可切換首頁。** 需要 Android 8.0（API 26）以上。用遙控器確認系統設定中能開啟「預設應用程式 → 主畫面應用程式」或同類選擇頁，並認出目前的原廠首頁。路徑依韌體而異。找不到選擇頁時，先維持原廠為預設首頁。
2. **下載並安裝 APK。** 將上方的 `.apk` 傳到電視，例如用 USB 隨身碟。用電視的檔案管理器開啟，依 Android 畫面確認安裝。若系統要求允許該檔案管理器安裝未知應用程式，可在安裝完成後關閉該授權。安裝前，請比對發布頁的 `SHA256SUMS`。若已安裝 Cinema TV，請使用同簽章的維護者 APK 更新。遇到簽章衝突或安裝失敗，先保留現有安裝和資料。[版本與簽章](docs/BETA2-INSTALLATION.md)
3. **先試開，再設為預設。** 從原廠首頁的應用程式清單開啟「Cinema TV Beta」，用遙控器試方向鍵、OK、返回、設定和應用程式清單。確認可操作後，在系統首頁選擇頁選「Cinema TV Beta」。Cinema TV 內若有「選擇預設主畫面」，也可用它開啟同一個系統選擇頁。
4. **用遙控器確認。** 按實體 Home 應直接回 Cinema TV。開啟平常使用的 App，確認可播放、有聲音，返回後再按 Home。一般首頁使用無需 Root 或 ADB。
5. **要回原廠時，先切回首頁。** 在同一個系統選擇頁選回原廠首頁，按實體 Home 確認可操作。需要移除 Cinema TV 時，再到系統應用程式設定卸載，卸載會移除 Cinema TV 的本機資料。若系統選擇頁無法開啟，可先用下方提示詞辨別原因。[回退說明](docs/INSTALLATION.md)

這是從 [CleanYourTV](https://github.com/Gathin23/cleanyourtv) 衍生的獨立專案，保留原作者及第三方授權；不代表原專案、安博、Google 或影音服務的官方產品。

![Cinema TV public native preview](docs/images/home-native-preview.png)

原生View預覽，使用合成範例App與已恢復、經PO授權的原始圖片；不是實機截圖。

## 主要功能

- 七格可見 App Carousel、玻璃材質、輕微透視及 App 主色光暈。
- 明確的 DPAD 焦點區域、應用程式清單／搜尋、捷徑排序、最多四個真實最近項目。
- 日出／日落配對主題、時間切換、自訂首頁及最近項目背景。
- 原生設定介面、返回焦點／捲動記憶、可中斷短動畫及可重建的操作音效。
- 網路狀態捷徑與開機自動開啟 App。
- 預設收合的開發工具：啟動無線連接、實驗性開機素材管理。一般首頁不需要 Root；開發工具須分別確認條件與風險。

公開版保留原本的預設圖片；PO已確認這組圖片為專案生成／自有素材，並同意MIT公開散布。逐張原圖雜湊與來源聲明見[素材說明](docs/ASSETS.md)。設備備份、私人事故收據與使用者另選的私人圖不包含。

## AI agent 提示詞

複製一段到 ChatGPT、Codex 或其他 AI agent，將方括號換成自己的資訊。使用遙控器逐步配合即可。請先遮蔽 IP、序號、Wi-Fi 名稱和帳號。

**第一次安裝與判斷相容性**

```text
請協助我在自己的電視安裝 Cinema TV。設備型號是［型號］，Android 版本是［版本／未知］，目前首頁是［名稱／未知］。
以 https://github.com/xd2314/CinemaTV 的 README 和正式發布頁附件為準。先帶我用遙控器找「關於」與系統首頁選擇頁，確認原廠首頁仍可用。區分最低系統需求、這台韌體是否允許選擇第三方 HOME，以及已實測的範圍；未知項目請明說。
確認後，逐步引導我下載維護者簽章 APK、核對 SHA-256、手動安裝、先開啟試用，再由我決定是否設為預設。最後請我按實體 Home 並測方向鍵、OK、返回、設定和常用影音 App。每一步等我回報實際畫面再繼續。一般安裝使用電視介面，不啟用 ADB 或 Root。
```

**安裝失敗、Home 回錯首頁或恢復原廠**

```text
請協助診斷 Cinema TV。我原本想［安裝／更新／設為首頁／恢復原廠］，設備是［型號與 Android 版本］，目前畫面是［畫面或完整錯誤文字］，按實體 Home 會到［首頁／選擇器／其他］。若我沒提供某項，先詢問或引導我查看。
以 https://github.com/xd2314/CinemaTV 的文件為準。優先用遙控器確認 APK 是否安裝、現有版本、系統選擇頁、目前的 HOME 與原廠首頁是否可選。請區分簽章衝突、版本限制、系統不提供選擇頁及預設首頁被重設，不把推測寫成已確認。若我要回原廠，先指引我用系統選擇頁切回，再由我按實體 Home 驗證。
如果我已設定 ADB 並明確請你協助，先核對我指定的單一設備，只讀取當前版本、套件與 HOME。任何安裝、卸載、清資料或預設首頁變更都須由我逐項批准。保留原廠 Launcher 和資料；不掃描網路、不提權、不重啟、不改開機素材。找不到可用回退入口就停，請我回報電視畫面。
```

## 本機建置

需要 JDK21、Android SDK34／Build Tools34.0.0、Python3.9以上；Gradle8.9 Wrapper 已包含。第一次建置需要下載官方依賴，App 本身沒有 INTERNET 權限。

```sh
python3 tools/check_public_tree.py
python3 -m unittest discover -s tests -v
python3 tools/build.py --unsigned
```

預設執行測試、Lint 與 release 建置。release 預設未簽章，不能直接安裝。一般開發者可執行 `python3 tools/build.py assembleDebug` 取得本機 debug 簽章版。[簽章與套件識別說明](docs/BUILDING.md)

公開程式碼不需要你的私人 `.jks`／`.keystore` 或密碼。不同簽章不能直接覆蓋同套件的既有安裝；不要為此先移除目前正在使用的首頁或清除資料。

## 版本與驗證

目前公開下載為 v73，版本 `7.0.0-beta.2-original-art`，保留原本的預設桌布與四季圖片。已在當次實機確認實體 Home 及 TTV、UPLIVE、UPMOVIE、YouTube 的播放、聲音與返回。其他機型、待機、重啟、長期使用及開機素材工具仍待驗證。[實機驗收](docs/BETA2-ACCEPTANCE.md) · [相容範圍](docs/COMPATIBILITY.md)

## 授權與參與

程式碼及本專案原創素材採 [MIT](LICENSE)，保留原作者Gathin；Outfit字型採OFL，Fluent Emoji另附MIT聲明。[第三方聲明](THIRD_PARTY_NOTICES.md)

歡迎參考 [CONTRIBUTING.md](CONTRIBUTING.md) 與 Issue 範本提供問題；回報時請移除IP、序號、帳號、Wi-Fi名稱與私人素材。[SECURITY.md](SECURITY.md)
