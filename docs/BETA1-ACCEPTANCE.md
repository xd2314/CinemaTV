# Beta1 當次驗收

- 本機960項App測試、Lint0error、原簽章與機器核對：PASS。
- PO實體Home／DPAD／OK／Back／設定與App清單：PASS_PO。
- PO原始回覆：「操作都正常，為何我的圖片都不見了，應該維持我原本的預設圖片上去，並非簡易圖」。
- 原始預設圖片外觀：FAIL，Beta1將原圖替換為程序插畫，PO要求恢復。
- 四App基本播放／聲音／返回：NOT_STARTED，先處理圖片恢復。
- Beta1散布：NO_GO，不能因導航PASS忽略圖片外觀被拒收。

後續改為v73 Beta2，逐位元組恢復原v71的16個內建圖片資源，另行記錄驗收。
