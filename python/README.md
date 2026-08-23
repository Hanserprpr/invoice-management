# Invoice OCR 服務

FastAPI + RapidOCR 發票識別微服務，獨立部署於 Java 後端，通過 HTTP API 通訊。

## 目錄結構

```
python/
├── app.py                      # FastAPI 應用入口
├── invoice_ocr.py             # OCR 核心邏輯（QR + RapidOCR + 字段抽取）
├── requirements.txt           # Python 依賴
├── invoice-ocr.service        # systemd unit（生產部署）
├── ocr-service.env.example    # 環境變量模板
├── DEPLOYMENT.md              # 詳細部署指南
└── README.md                  # 本文件
```

## 快速開始（開發）

### 本地運行
```bash
cd python

# 安裝依賴
pip install -r requirements.txt

# 設置 API Key（開發用，建議改成隨機值）
export OCR_API_KEY="CHANGE_ME"

# 啟動服務
python3 app.py
```

服務會監聽 `http://127.0.0.1:9000`。

### 測試識別
```bash
# 獲取一份測試發票（PDF/JPG）
# 假設文件名是 sample.pdf

curl -X POST \
  -H "Authorization: Bearer CHANGE_ME" \
  -H "Content-Type: application/pdf" \
  --data-binary @sample.pdf \
  http://127.0.0.1:9000/recognize
```

返回格式：
```json
{
  "rawText": "發票全文...",
  "qrRaw": "二維碼原文（如有）",
  "fields": {
    "invoiceCode": {"value": "150001199901", "confidence": 0.99},
    "invoiceNumber": {"value": "00000001", "confidence": 0.99},
    "date": {"value": "2023-01-15", "confidence": 0.98},
    "amount": {"value": "12345.67", "confidence": 0.95},
    "taxId": {"value": "91110101MA123456X1", "confidence": 0.97}
  }
}
```

## 生產部署

見 [DEPLOYMENT.md](DEPLOYMENT.md)——包含完整的安裝步驟、systemd 集成、故障排查。

核心步驟（簡略版）：
```bash
# 1. 系統依賴
sudo apt install python3 python3-pip libzbar0 libopencv-dev

# 2. 複製文件到 /opt/invoice-ocr
sudo cp {app.py,invoice_ocr.py,requirements.txt} /opt/invoice-ocr/
pip3 install -r /opt/invoice-ocr/requirements.txt

# 3. 配置環境變量
sudo cp ocr-service.env.example /etc/invoice-ocr.env
sudo nano /etc/invoice-ocr.env

# 4. 安裝 systemd 服務
sudo cp invoice-ocr.service /etc/systemd/system/
sudo systemctl enable --now invoice-ocr

# 5. 驗證
curl http://127.0.0.1:9000/health
```

## API 端點

### `GET /health`
健康檢查（存活探針）。

**響應**：
```json
{"status": "ok"}
```

### `POST /recognize`
發票識別主接口。

**請求頭**：
- `Authorization: Bearer {api_key}` — 必填，與環境變量 `OCR_API_KEY` 對應
- `Content-Type: {media_type}` — 必填，支持 `application/pdf`, `image/jpeg`, `image/png` 等

**請求體**：
- 二進制文件流（PDF / JPG / PNG / BMP / WebP / TIFF），最大 50MB

**響應** (200 OK)：
```json
{
  "rawText": "string or null",
  "qrRaw": "string or null",
  "fields": {
    "invoiceCode": {"value": "string or null", "confidence": 0.0 - 1.0},
    "invoiceNumber": {"value": "string or null", "confidence": 0.0 - 1.0},
    "date": {"value": "YYYY-MM-DD or null", "confidence": 0.0 - 1.0},
    "amount": {"value": "numeric string or null", "confidence": 0.0 - 1.0},
    "taxId": {"value": "string or null", "confidence": 0.0 - 1.0}
  }
}
```

**錯誤響應**：
- `401 Unauthorized` — API Key 缺失或錯誤
- `400 Bad Request` — 空請求體或 Content-Type 缺失
- `413 Payload Too Large` — 文件超過 50MB
- `415 Unsupported Media Type` — 不支持的文件格式
- `500 Internal Server Error` — 識別失敗

## 與 Java 後端整合

在 Java 後端的 `/etc/invoice-management.env` 配置：

```
OCR_ENABLED=true
OCR_ENDPOINT=http://127.0.0.1:9000/recognize
OCR_API_KEY=xxx  # 與 /etc/invoice-ocr.env 的 OCR_API_KEY 相同
OCR_TIMEOUT=30s
OCR_WORKER_ENABLED=true
OCR_POLL_INTERVAL=5s
```

Java 後端會異步調用此服務識別用戶上傳的發票文件。

## 技術棧

| 組件 | 用途 |
|---|---|
| **FastAPI** | HTTP 框架，輕量高效 |
| **RapidOCR** | ONNX 運行時的中文 OCR，快速準確 |
| **OpenCV** | 圖像處理 + QR 碼檢測 |
| **PyMuPDF** | PDF 渲染和文本提取 |
| **pyzbar** | QR 碼解碼（二維碼多個支持） |
| **uvicorn** | ASGI 應用服務器 |

## 識別流程

### 輸入：PDF / 圖片

1. **QR 碼解碼** → 提取票據信息（如有）
2. **文字 OCR** → 識別發票上的中文文字
3. **結構化抽取** → 坐標定位或正則匹配字段
4. **合併結果** → QR > 坐標 > 正則（優先級排序）

### 輸出：結構化字段 + 置信度

- `invoiceCode` — 發票代碼（10-12 位數字）
- `invoiceNumber` — 發票號碼（8-24 位數字）
- `date` — 開票日期（YYYY-MM-DD）
- `amount` — 價稅合計金額（數字，保留 2 位小數）
- `taxId` — 銷售方稅號（15-20 位數字/字母）

## 性能指標

- **單頁 PDF 識別時間**：2-5 秒（含模型首次加載）
- **圖片識別時間**：1-3 秒
- **首次啟動加載模型**：30-60 秒（自動下載 ONNX 模型）
- **內存占用**：200-500MB（取決於並發）

## 故障排查

### 啟動失敗
見 [DEPLOYMENT.md#故障排查](DEPLOYMENT.md#故障排查)

### 識別準確率低
- 確保發票圖片清晰（手機拍照需對焦）
- 掃描 PDF 采用 300DPI 渲染
- 某些非標準版式發票可能無法完整識別

## 許可和相容性

- **RapidOCR**：Apache 2.0
- **PyMuPDF**：AGPL 3.0（商用需關注）
- **pyzbar**：MIT
- **OpenCV**：Apache 2.0

## 下一步

- [ ] 多進程配置（提升吞吐量）
- [ ] 模型量化（降低內存占用）
- [ ] 異步隊列（集成 Redis / Celery）
- [ ] 監控端點（Prometheus metrics）
- [ ] 識別結果緩存
