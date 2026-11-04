# 人工發布程序 / Publication

這份目錄只在本機完成準備，沒有建立GitHub儲存庫、設定遠端或執行push。CI不簽正式APK、不授予設備權限、不建立release。

## 首次公開前

1. 閱讀LICENSE、NOTICE、THIRD_PARTY_NOTICES及assets/manifest.json，確認願意以MIT授權自己的程式與原創素材，並保留第三方條件。
2. 確認GitHub擁有者／組織、儲存庫名稱、公開範圍、Git提交姓名／信箱及品牌名稱。此模板不替你虛構帳號、網站或私密聯絡信箱。
3. 建議先建立空的私人儲存庫，確認自己的提交身分與GitHub私密信箱設定，設定Security私人漏洞回報與社群聯絡管道。
4. 執行下列本機檢查，人工檢視`git diff --cached`及所有圖片，再決定首次commit與push。Gitignore不能清除已被提交的秘密。
5. 首次在GitHub執行CI，確認Linux環境通過；本機macOS通過不等於雲端CI已執行。
6. 檢查README中預覽、支援範圍、簽章與Root說明，再由擁有者明確決定切換為公開。

## 本機檢查與來源ZIP

```sh
python3 tools/check_public_tree.py
python3 -m unittest discover -s tests -v
python3 tools/build.py --unsigned
python3 tools/make_source_release.py ../CinemaTV-source-review.zip
python3 tools/make_source_release.py --verify ../CinemaTV-source-review.zip
```

封裝使用固定白名單`tools/public-files.txt`，輸出必須在儲存庫外，既有ZIP不覆寫，附每檔SHA256並獨立讀回驗證。來源ZIP不含SDK／快取／APK／簽章或設備備份。從ZIP解壓後可直接執行檢查；附帶的SOURCE-SHA256.json會額外驗證原始內容，若開始修改，先完成核對再移除這份產物清單。來源清單有合法變更時，先審查新增路徑，再人工更新白名單；不要為了通過掃描而任意忽略警告。

## 正式APK發布

維護者另行決定套件名稱、版本及正式金鑰，依docs/BUILDING.md私下簽章、核對APK憑證與SHA256，並在獨立授權的測試設備驗收。私鑰與密碼不公開。Release附件的簽署APK與來源ZIP應清楚區分，附對應來源版本、建置條件及未驗證項目。

測試機或現有使用者安裝不會因你公開來源就自動更新，也不能保證自建簽章APK能覆蓋私人原型。保留OEM Launcher與可靠回退入口，不把公開原始碼當作Root／刷機／長期留置授權。
