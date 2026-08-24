"""
FastAPI 發票 OCR 識別服務
接收 PDF/圖片二進制流，返回識別結果（QR + 文字字段）
"""
import logging
import tempfile
from pathlib import Path
from typing import Optional

from fastapi import FastAPI, Request, Header, HTTPException
from fastapi.responses import JSONResponse
import uvicorn

from invoice_ocr import recognize_invoice

# ============================================================
# 配置
# ============================================================

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s'
)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="Invoice OCR Service",
    version="1.0.0",
    openapi_url="/api/openapi.json",
    docs_url="/api/docs",
    redoc_url="/api/redoc",
)

# 期望的 API Key（從環境變量讀，簡單示例）
EXPECTED_API_KEY = "CHANGE_ME"  # 實際部署時從 env 讀


# ============================================================
# 工具函數
# ============================================================

def validate_api_key(auth_header: Optional[str]) -> bool:
    """驗證 Bearer token"""
    if not auth_header:
        return False

    parts = auth_header.split()
    if len(parts) != 2 or parts[0].lower() != "bearer":
        return False

    return parts[1] == EXPECTED_API_KEY


def get_file_suffix(content_type: str) -> str:
    """從 Content-Type 推斷文件副檔名"""
    mapping = {
        "application/pdf": ".pdf",
        "image/jpeg": ".jpg",
        "image/jpg": ".jpg",
        "image/png": ".png",
        "image/bmp": ".bmp",
        "image/webp": ".webp",
        "image/tiff": ".tiff",
        "image/tif": ".tif",
    }
    return mapping.get(content_type.lower(), ".bin")


# ============================================================
# 健康檢查
# ============================================================

@app.get("/api/health")
async def health():
    """存活探針"""
    return {"status": "ok"}


# ============================================================
# 主要識別接口
# ============================================================

@app.post("/api/recognize")
async def recognize(
    request: Request,
    authorization: Optional[str] = Header(None),
    content_type: Optional[str] = Header(None),
):
    """
    識別發票

    請求：
    - Content-Type: application/pdf | image/jpeg | image/png 等
    - Authorization: Bearer {api_key}
    - Body: 二進制文件

    返回：
    {
        "rawText": "識別出的全文",
        "qrRaw": "二維碼原文",
        "fields": {
            "invoiceCode": {"value": "...", "confidence": 0.95},
            "invoiceNumber": {"value": "...", "confidence": 0.99},
            ...
        }
    }
    """

    # 驗證認證
    if not validate_api_key(authorization):
        logger.warning("Unauthorized request")
        raise HTTPException(status_code=401, detail="Unauthorized")

    # 讀取請求體
    try:
        body = await request.body()
        if not body or len(body) == 0:
            logger.error("Empty request body")
            raise HTTPException(status_code=400, detail="Empty body")

        if len(body) > 50 * 1024 * 1024:  # 50MB 限制
            logger.error("File too large")
            raise HTTPException(status_code=413, detail="File too large (max 50MB)")

    except Exception as e:
        logger.error(f"Failed to read body: {e}")
        raise HTTPException(status_code=400, detail=str(e))

    # 建立臨時文件
    suffix = get_file_suffix(content_type or "application/octet-stream")
    temp_path = None

    try:
        with tempfile.NamedTemporaryFile(
            suffix=suffix,
            delete=False,
            prefix="invoice-ocr-"
        ) as tmp:
            temp_path = tmp.name
            tmp.write(body)

        logger.info(f"Processing file: {temp_path} (size: {len(body)} bytes, type: {content_type})")

        # 識別
        result = recognize_invoice(temp_path)

        logger.info(f"Recognition completed: qrRaw={result.get('qrRaw', 'None')[:20] if result.get('qrRaw') else 'None'}")

        return JSONResponse(
            status_code=200,
            content=result
        )

    except ValueError as e:
        logger.error(f"Unsupported file format: {e}")
        raise HTTPException(status_code=415, detail=str(e))

    except Exception as e:
        logger.error(f"Recognition failed: {e}")
        raise HTTPException(status_code=500, detail=f"Recognition failed: {str(e)}")

    finally:
        # 清理臨時文件
        if temp_path:
            try:
                Path(temp_path).unlink()
            except Exception as e:
                logger.warning(f"Failed to cleanup temp file {temp_path}: {e}")


# ============================================================
# 錯誤處理
# ============================================================

@app.exception_handler(Exception)
async def general_exception_handler(request: Request, exc: Exception):
    logger.error(f"Unhandled exception: {exc}")
    return JSONResponse(
        status_code=500,
        content={"detail": "Internal server error"}
    )


# ============================================================
# 啟動
# ============================================================

if __name__ == "__main__":
    import os

    # 從環境變量讀 API Key
    api_key = os.getenv("OCR_API_KEY", "CHANGE_ME")
    EXPECTED_API_KEY = api_key
    logger.info(f"API Key configured: {api_key[:4]}...")

    # 啟動服務
    port = int(os.getenv("OCR_PORT", "9000"))
    host = os.getenv("OCR_HOST", "127.0.0.1")

    logger.info(f"Starting OCR service on {host}:{port}")
    uvicorn.run(
        app,
        host=host,
        port=port,
        log_level="info"
    )
