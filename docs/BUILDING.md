# 建置、簽章與套件識別 / Building

## 環境

| 項目 | 固定值 |
| --- | --- |
| Java | JDK21；原始碼相容Java17 |
| Gradle Wrapper | 8.9，發行ZIP及Wrapper JAR均有SHA256核對 |
| Android Gradle Plugin | 8.5.2 |
| Android SDK | Platform34、Build Tools34.0.0 |
| 最低系統 | API26；不等於所有廠牌均已實測 |
| Python | 3.9以上；檢查／封裝工具只用標準函式庫 |

先安裝JDK與Android SDK，設定`JAVA_HOME`及`ANDROID_HOME`，或在Android Studio選取SDK。macOS建置工具可找本機JDK21及使用者的標準SDK位置，不會讀取Keychain。Windows可直接使用`launcher\gradlew.bat`。

```sh
python3 tools/check_public_tree.py
python3 -m unittest discover -s tests -v
python3 tools/build.py --unsigned
```

等價Gradle操作：進入`launcher`後執行`./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease`。依賴已快取時可加`--offline`。首次建置仍需官方Google／Maven Central／Gradle來源；離線來源ZIP不包含SDK、JDK、Gradle發行版或依賴快取。

輸出位於`launcher/app/build/outputs/apk/release/`。預設release沒有簽章，只供核對，不能直接安裝。建置過程不執行ADB、Root或HOME變更。

## 開發者本機Debug

```sh
python3 tools/build.py assembleDebug
```

使用Android標準本機debug key，不是私人Cinema TV發布憑證。勿把這個APK當成原部署版的直接更新。請用測試設備，或在了解限制後指定自己的applicationId。

## 正式簽章

金鑰應由發布維護者自行建立與保管，放在儲存庫外；其他貢獻者不需要正式金鑰。

Gradle只在以下四個環境變數全部明確提供時簽署release：

- `CINEMA_KEYSTORE_PATH`：外部金鑰的完整路徑。
- `CINEMA_KEYSTORE_PASSWORD`：儲存庫密碼。
- `CINEMA_KEY_ALIAS`：你的金鑰alias。
- `CINEMA_KEY_PASSWORD`：該alias密碼。

密碼使用互動式隱藏輸入或私密CI環境，不寫進命令列、Git、Gradle properties、一般ZIP或Issue。四項不完整時停止建置；全部未提供時產生未簽章release。`--unsigned`會從建置子程序移除這四項設定，不讀取私人金鑰。

保管金鑰的加密離線副本。公開SHA256憑證指紋不等於公開私鑰。此儲存庫沒有私人版本的憑證、密碼、Keychain帳號或簽章自動移轉工具。

## Package identity

Java namespace保留`com.gathin.tvhome`；歷史applicationId為`com.gathin.tvhome.cinemalite.dev`，與私人原型相同，但本機自行簽章通常不同。Android不允許不同憑證覆蓋同套件；不要以移除目前HOME／清資料解決。

現有Gradle參數`-PlauncherApplicationId=<你選定的唯一套件名稱>`可建立獨立安裝。這不會自動移轉資料／權限／HOME；Root開機素材的套件守衛仍只接受歷史指定ID，自訂ID下會拒絕整組開機外觀工具（包含系統過場桌布），不可為了安裝而放寬守衛。

目前Beta versionCode73，versionName`7.0.0-beta.2-original-art`。本輪維護者簽署APK沿用既有原憑證，Java namespace／套件識別不變；原憑證私鑰與密碼仍只在私人環境。正式維護者須在首次發布前確認長期套件名稱、版本策略及正式簽章；不能用此來源ZIP推論任何既有安裝可直接升級。

## 依賴與素材

執行`./gradlew :app:publicDependencyReport`可在`launcher/app/build/public-dependencies.json`取得依賴座標。缺少POM授權資料時，可對授權彙整工具加`--fetch-missing`，只向官方Google／Maven Central取得公開POM。依賴授權見THIRD_PARTY_NOTICES.md。重建原創桌布及音效：`python3 tools/regenerate_assets.py`（需要JDK21，僅輸出至build預覽目錄，不更改原圖）。素材更新後需重新人工審查並更新assets/manifest.json，不可盲目把未知素材標為MIT。
