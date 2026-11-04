# 預設素材、來源與重建界線

## 保留原本圖片

v73恢復原本預設圖片，沒有換成新生成、簡易插畫或色相替代圖。16個內建圖片資源與原v71來源及APK逐位元組一致，包含山湖日出／日落、OLED、秋色、極光、四季及原圖示／banner。

PO於2026-11-03明確回覆：「都是專案生成／自有素材，同意 MIT 公開」。聲明及逐張SHA256見`assets/original-artwork-permission.json`，完整素材清單見`assets/manifest.json`。此權利基礎為PO聲明，未假稱已獨立重建每張圖片歷史生成紀錄；繼承的上游作者聲明仍保留。

Beta1曾使用程序插畫，PO確認操作正常但拒收外觀，因此Beta1維持NO_GO。v73更新後PO已確認「原本圖片已恢復，Home 正常」。後續不得為了開源整理擅自更換、重生或改色預設圖片。

## 其他素材

- `launcher/tools/generate_cinema_sounds.py`：原創合成聲，固定seed、沒有外部取樣；與原v71音檔逐位元組一致，採MIT。
- Outfit字型：OFL-1.1，原聲明保留於LICENSES/Outfit-OFL-1.1.txt及launcher/FONT-LICENSE-OFL.txt。
- Halloween Fluent Emoji衍生WebP：Microsoft MIT，原縮放與亮暗南瓜修改說明保留於LICENSES/Fluent-Emoji-MIT.txt。
- Gradle Wrapper8.9：官方SHA256已核對，內嵌授權保存於LICENSES/Gradle-wrapper-LICENSE.txt。
- 純色MP4／SPS測試檔：程式產生、MIT，測試專用，不隨APK包含。
- docs/images：原生View預覽，使用原始授權圖片與合成範例App，不是設備截圖。

外部App圖示由安裝套件取得，不包含影音App、訂閱或媒體內容；使用者自行選取的私人圖片與設備備份不公開。

## 可選範例工具不影響預設圖片

```sh
python3 tools/regenerate_assets.py
```

只在`launcher/build/generated-art-preview`產生可選程序範例；禁止輸出到App資源目錄或其父目錄，不會再覆蓋內建原圖。範例不會自動匯入App或取代fallback。

若只需重建原有合成音效：`python3 tools/regenerate_assets.py --sounds-only`。不處理任何圖片。更新素材需明確產品決策、權利聲明、雜湊與回歸驗證。
