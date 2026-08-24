# Invoice OCR 服務部署指南

FastAPI + RapidOCR 發票識別服務，支持：
- **QR 碼解碼**：pyzbar + opencv (支持多個 QR)
- **文字 OCR**：RapidOCR（中文效果最好）
- **結構化字段抽取**：坐標定位 + 正則匹配
- **支持格式**：PDF / JPG / PNG / BMP / WebP / TIFF

## 系統需求

### 依賴安裝（Linux）
```bash
# Debian/Ubuntu
sudo apt update
sudo apt install -y python3 python3-pip python3-venv libzbar0 libopencv-dev

# CentOS/RHEL
sudo yum install -y python3 python3-pip zbar-devel opencv-devel
```

### 推薦配置
- **CPU**：4 核+（RapidOCR 可多線程利用）
- **內存**：2GB+（模型加載）
- **磁盤**：500MB+（Python 依賴 + 模型權重，快速盤優佳）

## 部署步驟

### 1. 準備目錄和用戶
```bash
sudo useradd -r -s /bin/false ocr
sudo mkdir -p /opt/invoice-ocr
sudo chown -R ocr:ocr /opt/invoice-ocr
cd /opt/invoice-ocr
```

### 2. 複製源碼和依賴
```bash
# 從項目根目錄
cp python/{app.py,invoice_ocr.py,requirements.txt} /opt/invoice-ocr/
sudo chown ocr:ocr /opt/invoice-ocr/*.py /opt/invoice-ocr/requirements.txt
```

### 3. 安裝 Python 環境（可選但推薦用 venv）
```bash
# 不用 venv（直接系統 Python）
cd /opt/invoice-ocr
pip3 install -r requirements.txt

# 或用 venv（隔離依賴）
cd /opt/invoice-ocr
python3 -m venv venv
source venv/bin/activate
pip install --upgrade pip
pip install -r requirements.txt
# 然後編輯 invoice-ocr.service，取消註釋 Environment="PATH=..." 行
```

### 4. 配置環境變量
```bash
# 生成 API Key（用於 Java 後端認證）
python3 -c "import secrets; print(secrets.token_urlsafe(32))"
# 輸出例如：WIKqE9YU2bZlh9gZjZrHvR5v3NjM7L4xPqWxYzZ1234

# 建立配置文件
sudo cp python/ocr-service.env.example /etc/invoice-ocr.env
sudo chown root:root /etc/invoice-ocr.env
sudo chmod 600 /etc/invoice-ocr.env

# 編輯 /etc/invoice-ocr.env
sudo nano /etc/invoice-ocr.env
```

編輯內容範例：
```
OCR_HOST=127.0.0.1
OCR_PORT=9000
OCR_API_KEY=WIKqE9YU2bZlh9gZjZrHvR5v3NjM7L4xPqWxYzZ1234
PYTHONUNBUFFERED=1
```

**重要**：
- `OCR_API_KEY` 必須與 Java 後端的 `OCR_API_KEY` 環境變量完全一致
- `OCR_HOST=127.0.0.1` 表示只監聽本機（推薦，避免公網暴露）
- 如要允許遠程調用，改成 `OCR_HOST=0.0.0.0`，但必須配合防火牆

### 5. 安裝 systemd 服務
```bash
sudo cp python/invoice-ocr.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable invoice-ocr
sudo systemctl start invoice-ocr

# 檢查狀態
sudo systemctl status invoice-ocr

# 查看日誌
sudo journalctl -u invoice-ocr -f
```

### 6. 驗證服務
```bash
# 本機測試（健康檢查）
curl http://127.0.0.1:9000/api/health
# 輸出：{"status":"ok"}

# 測試識別（需要真實發票文件）
curl -X POST \
  -H "Authorization: Bearer WIKqE9YU2bZlh9gZjZrHvR5v3NjM7L4xPqWxYzZ1234" \
  -H "Content-Type: application/pdf" \
  --data-binary @invoice.pdf \
  http://127.0.0.1:9000/api/recognize
```

## 與 Java 後端整合

### Java 環境變量配置
在 `/etc/invoice-management.env` 中設置：
```
OCR_ENABLED=true
OCR_ENDPOINT=http://127.0.0.1:9000/api/recognize
OCR_API_KEY=WIKqE9YU2bZlh9gZjZrHvR5v3NjM7L4xPqWxYzZ1234
OCR_TIMEOUT=30s
OCR_WORKER_ENABLED=true
OCR_POLL_INTERVAL=5s
```

**同步要點**：
- `OCR_ENDPOINT`：指向 OCR 識別接口（本機用 `http://127.0.0.1:9000/api/recognize`，遠程用實際 IP 和同一路徑）
- `OCR_API_KEY`：完全相同的密鑰字符串
- 如 OCR 和 Java 後端在不同機器，確保網絡連通（防火牆開放 9000 端口）

### 生產環境注意事項
- **Nginx 反代**（推薦）：在 Nginx 後面代理 OCR 服務，對 Java 後端隱藏真實 IP
  ```nginx
  location = /api/ocr {
      proxy_pass http://127.0.0.1:9000/api/recognize;
      proxy_set_header Authorization $http_authorization;
      proxy_pass_header Content-Type;
      client_max_body_size 50m;
  }
  ```
  此時 Java 配置 `OCR_ENDPOINT=http://nginx-internal-ip/api/ocr`

- **Rate Limit**：OCR 服務本身單線程處理（RapidOCR 占用 CPU），多個並發識別請求會排隊
  - 建議在 Nginx 層配置 `limit_req`
  - 或在 Java 層控制並發任務數

- **日誌**：所有日誌去 journald
  ```bash
  sudo journalctl -u invoice-ocr --since "10 minutes ago"
  sudo journalctl -u invoice-ocr -n 100 --no-pager
  ```

- **監控**：推薦監控 `/api/health` 端點和 OCR service 進程狀態

## 故障排查

### 啟動失敗 / 找不到模塊
```bash
# 確認 Python 版本
python3 --version  # 需 3.8+

# 確認依賴安裝
pip3 list | grep -i "rapidocr\|fastapi\|opencv"

# 重新安裝（清理舊版本）
pip3 install --force-reinstall -r requirements.txt
```

### 首次運行很慢（下載模型）
- RapidOCR 首次運行會自動下載 ONNX 模型（~300MB）到 `~/.cache/`
- 進度可用 `sudo journalctl -u invoice-ocr -f` 查看
- 等待完成後第二次請求會很快（模型已緩存）

### OCR 識別準確率低
- 檢查圖片清晰度（模糊圖片識別率低）
- 掃描 PDF 用 300DPI 渲染（參見 `app.py` 的 `recognize_pdf` 調用）
- 嘗試調整 OCR 的 confidence 閾值（代碼中 `FIELD_NAMES` 和 `make_field`）

### 服務掛死 / 無響應
```bash
# 重啟
sudo systemctl restart invoice-ocr

# 查看進程
ps aux | grep app.py

# 強制殺死並重啟
sudo systemctl kill invoice-ocr
sudo systemctl start invoice-ocr
```

### 許可權問題
```bash
# 確認 ocr 用戶可寫臨時目錄
sudo -u ocr touch /tmp/test && rm /tmp/test

# 檢查配置文件許可權
ls -l /etc/invoice-ocr.env  # 應該是 600，owner root:root
```

## 更新和升級

### 更新 OCR 模型 / 代碼
```bash
# 停止服務
sudo systemctl stop invoice-ocr

# 更新源代碼
cp python/app.py /opt/invoice-ocr/
cp python/invoice_ocr.py /opt/invoice-ocr/
sudo chown ocr:ocr /opt/invoice-ocr/*.py

# 更新依賴（如需要）
pip3 install --upgrade -r requirements.txt

# 啟動服務
sudo systemctl start invoice-ocr
```

### 清理 OCR 模型快取
```bash
# 重新下載模型（下次啟動時）
rm -rf ~/.cache/rapidocr/
# 或以 ocr 用戶執行
sudo -u ocr rm -rf /home/ocr/.cache/rapidocr/ 2>/dev/null || true
```

## 性能調優

### 多進程（生產推薦）
默認 `app.py` 單進程運行。要使用多進程（利用多核），改為：
```bash
# uvicorn 多進程（4 個工作進程）
/usr/bin/python3 -m uvicorn app:app --host 127.0.0.1 --port 9000 --workers 4
```

編輯 `invoice-ocr.service`：
```ini
ExecStart=/usr/bin/python3 -m uvicorn app:app --host 127.0.0.1 --port 9000 --workers 4
```

**注意**：RapidOCR 模型加載較重，多進程會占用更多內存。根據服務器內存調整 `--workers` 數量。

### 內存使用
```bash
# 監控內存（systemd 服務）
sudo systemctl status invoice-ocr | grep Memory
journalctl -u invoice-ocr --no-pager | grep -i memory
```

## 許可證和致謝

- **RapidOCR**：Apache 2.0，百度開源
- **pyzbar**：MIT
- **OpenCV**：Apache 2.0
- **PyMuPDF**：AGPL 3.0（商用注意）

如項目含專有代碼，請自行評估開源許可相容性。
