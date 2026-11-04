# 來源與版本沿革

Cinema TV原型來自CleanYourTV的Java Android launcher，之後加入Cinema Lite/Cinema TV視覺、焦點、桌布、最近項目、操作音效、開機App與受控開發工具。

本公開來源以私人v71工程快照為起點，versionCode71、公開versionName`7.0-cinema-tv.oss.1`。原快照的精確上游commit沒有記錄，不能把本輪查閱授權時的upstream main當作匯入版本。

公開化改動：

- 補齊MIT、上游／第三方聲明及建置／相容／安全文件。
- 保留Java namespace及歷史預設applicationId，重新整理可選發布簽章環境；不含私人憑證。
- 以可重建的原創插畫取代來源紀錄不足的照片，改用原創vector品牌icon／banner。
- 保留可重建且雜湊吻合的原創合成音效、OFL字型與已附MIT聲明的Emoji。
- 排除原安裝腳本、全部設備證據／備份；舊事故候選採用綁定改為合成測試值，保留交易守衛。
- 新增離線隱私／素材檢查、白名單來源ZIP與人工發布程序，CI不連設備或自動發布。

私人原型曾完成核心HOME／影音驗收，其他階段有NOT COMPLETED及歷史ANR，沒有因開源整理改寫。本公開變體尚未實機測試，不能繼承私人版完整PASS。正式公開前需由維護者確認身分與GitHub設定，實機測試另外批准。

## Beta1 / v72

在OSS1來源整理後，增加versionCode72、versionName7.0.0-beta.1與Cinema TV Beta標籤，沿用歷史套件及維護者原憑證，準備可安裝的側載Beta。除版本／標籤外沒有更改首頁或Root交易邏輯。OSS1結果保留於RELEASE-READINESS-OSS1.md與validation-oss1.json；當次Beta機器與人工結果另記，不回寫歷史PASS。

## Beta2 / v73 · 原圖恢復

PO確認Beta1操作正常，但拒收程序插畫取代原圖的外觀；Beta1保持NO_GO，不改成整體PASS。依PO要求，v73把16個原始內建圖片資源逐位元組恢復至v71內容，沒有更改導航或Root交易邏輯。PO聲明原圖為專案生成／自有並同意MIT公開，記錄於assets/original-artwork-permission.json。

普通同簽章更新72→73後，PO已確認「原本圖片已恢復，Home 正常」。四App當次精簡冒煙另記；未回覆前不推定PASS。程序範例工具改為只輸出build預覽目錄，不能覆蓋預設素材。
