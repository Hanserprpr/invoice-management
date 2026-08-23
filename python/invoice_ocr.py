from __future__ import annotations

import re
import json
import sys
from pathlib import Path
from typing import Any, Optional
from urllib.parse import urlparse, parse_qs

import cv2
import numpy as np
import pymupdf


# ============================================================
# RapidOCR 兼容
# ============================================================

try:
    # 新版
    from rapidocr import RapidOCR
except ImportError:
    # 旧版
    from rapidocr_onnxruntime import RapidOCR


ocr_engine = RapidOCR()


# ============================================================
# 基础数据结构
# ============================================================

FIELD_NAMES = (
    "invoiceCode",
    "invoiceNumber",
    "date",
    "amount",
    "taxId",
)


def empty_field() -> dict:
    return {
        "value": None,
        "confidence": 0.0,
    }


def make_field(
    value: Optional[str],
    confidence: float,
) -> dict:
    return {
        "value": value,
        "confidence": round(
            max(0.0, min(float(confidence), 1.0)),
            4
        ),
    }


def empty_fields() -> dict:
    return {
        name: empty_field()
        for name in FIELD_NAMES
    }


# ============================================================
# OCR item
#
# 所有来源最后统一成：
#
# {
#     "text": "发票号码",
#     "confidence": 0.99,
#     "box": [x1, y1, x2, y2],
#     "page": 0,
#     "source": "ocr" / "pdf"
# }
# ============================================================

def normalize_box(box) -> list[float]:
    """
    RapidOCR 返回四点多边形：
    [
        [x1, y1],
        [x2, y2],
        [x3, y3],
        [x4, y4]
    ]

    转成：
    [left, top, right, bottom]
    """

    arr = np.asarray(box, dtype=float)

    xs = arr[:, 0]
    ys = arr[:, 1]

    return [
        float(xs.min()),
        float(ys.min()),
        float(xs.max()),
        float(ys.max()),
    ]


def box_center(box):
    x1, y1, x2, y2 = box

    return (
        (x1 + x2) / 2,
        (y1 + y2) / 2,
    )


def box_width(box):
    return max(1.0, box[2] - box[0])


def box_height(box):
    return max(1.0, box[3] - box[1])


# ============================================================
# RapidOCR
# ============================================================

def rapid_ocr_image(
    image: np.ndarray,
    page: int = 0,
) -> list[dict]:

    try:
        result = ocr_engine(image)

        # 新版某些版本返回对象
        if hasattr(result, "txts"):
            items = []

            txts = result.txts or []
            scores = result.scores or []
            boxes = result.boxes or []

            for text, score, box in zip(
                txts,
                scores,
                boxes
            ):
                if not text:
                    continue

                items.append({
                    "text": str(text).strip(),
                    "confidence": float(score),
                    "box": normalize_box(box),
                    "page": page,
                    "source": "ocr",
                })

            return items

        # 老版一般：
        # result, elapsed = engine(...)
        if isinstance(result, tuple):
            result = result[0]

        if not result:
            return []

        items = []

        for row in result:
            if len(row) < 3:
                continue

            box, text, confidence = row[:3]

            if not text:
                continue

            items.append({
                "text": str(text).strip(),
                "confidence": float(confidence),
                "box": normalize_box(box),
                "page": page,
                "source": "ocr",
            })

        return items

    except Exception as e:
        print(
            f"[WARN] RapidOCR failed: {e}",
            file=sys.stderr
        )

        return []


# ============================================================
# 二维码
# ============================================================

def decode_qr(
    image: np.ndarray,
) -> list[str]:
    """
    支持同一页多个二维码。
    """

    detector = cv2.QRCodeDetector()
    values = []

    # ------------------------------
    # 多二维码
    # ------------------------------

    try:
        result = detector.detectAndDecodeMulti(image)

        if result and len(result) >= 2:
            ok = result[0]
            decoded_info = result[1]

            if ok and decoded_info:
                for value in decoded_info:
                    if value and value.strip():
                        values.append(value.strip())

    except Exception:
        pass

    # ------------------------------
    # 单二维码 fallback
    # ------------------------------

    if not values:

        try:
            value, points, _ = detector.detectAndDecode(
                image
            )

            if value and value.strip():
                values.append(value.strip())

        except Exception:
            pass

    # 去重
    return list(dict.fromkeys(values))


# ============================================================
# 日期 / 金额等格式化
# ============================================================

def normalize_date(
    value: str,
) -> Optional[str]:

    if not value:
        return None

    value = (
        str(value)
        .strip()
        .replace(" ", "")
    )

    # YYYYMMDD
    m = re.fullmatch(
        r"(\d{4})(\d{2})(\d{2})",
        value
    )

    if m:
        y, month, day = map(int, m.groups())

        if valid_date(y, month, day):
            return f"{y:04d}-{month:02d}-{day:02d}"

    # YYYY年MM月DD日
    # YYYY-MM-DD
    # YYYY/MM/DD
    # YYYY.MM.DD

    m = re.search(
        r"(\d{4})"
        r"[年./\-]"
        r"(\d{1,2})"
        r"[月./\-]"
        r"(\d{1,2})"
        r"日?",
        value,
    )

    if m:
        y, month, day = map(int, m.groups())

        if valid_date(y, month, day):
            return f"{y:04d}-{month:02d}-{day:02d}"

    return None


def valid_date(
    y: int,
    m: int,
    d: int,
) -> bool:

    if not (2000 <= y <= 2100):
        return False

    if not (1 <= m <= 12):
        return False

    if not (1 <= d <= 31):
        return False

    return True


def normalize_amount(
    value: str,
) -> Optional[str]:

    if not value:
        return None

    value = (
        str(value)
        .strip()
        .replace(",", "")
        .replace("￥", "")
        .replace("¥", "")
        .replace(" ", "")
    )

    m = re.search(
        r"-?\d+(?:\.\d{1,2})?",
        value
    )

    if not m:
        return None

    try:
        return f"{float(m.group()):.2f}"

    except ValueError:
        return None


def normalize_tax_id(
    value: str,
) -> Optional[str]:

    if not value:
        return None

    value = re.sub(
        r"[^0-9A-Za-z]",
        "",
        value
    ).upper()

    if 15 <= len(value) <= 20:
        return value

    return None


def normalize_number(
    value: str,
) -> Optional[str]:

    if not value:
        return None

    value = re.sub(
        r"\D",
        "",
        value
    )

    return value or None


# ============================================================
# QR 解析
# ============================================================

def parse_qr(
    qr_raw: Optional[str],
) -> dict:

    fields = {}

    if not qr_raw:
        return fields

    raw = qr_raw.strip()

    # ========================================================
    # 1. 老式增值税发票二维码
    #
    # 常见：
    # 01,10,发票代码,发票号码,金额,日期,校验码,...
    # ========================================================

    parts = [
        x.strip()
        for x in raw.split(",")
    ]

    if len(parts) >= 6:

        code = parts[2]
        number = parts[3]
        amount = parts[4]
        date = parts[5]

        if re.fullmatch(
            r"\d{10,12}",
            code
        ):
            fields["invoiceCode"] = make_field(
                code,
                1.0
            )

        if re.fullmatch(
            r"\d{8,24}",
            number
        ):
            fields["invoiceNumber"] = make_field(
                number,
                1.0
            )

        normalized_amount = normalize_amount(amount)

        if normalized_amount:
            fields["amount"] = make_field(
                normalized_amount,
                1.0
            )

        normalized_date = normalize_date(date)

        if normalized_date:
            fields["date"] = make_field(
                normalized_date,
                1.0
            )

    # ========================================================
    # 2. URL 类型二维码
    #
    # 有些二维码是 URL：
    #
    # https://...?invoiceNumber=xxx&amount=xxx
    #
    # ========================================================

    if raw.startswith(
        ("http://", "https://")
    ):

        try:
            parsed = urlparse(raw)
            query = parse_qs(parsed.query)

            url_keys = {
                "invoiceCode": [
                    "invoiceCode",
                    "invoice_code",
                    "fpdm",
                ],

                "invoiceNumber": [
                    "invoiceNumber",
                    "invoice_number",
                    "fphm",
                ],

                "date": [
                    "date",
                    "invoiceDate",
                    "kprq",
                ],

                "amount": [
                    "amount",
                    "totalAmount",
                    "je",
                ],

                "taxId": [
                    "taxId",
                    "tax_id",
                    "nsrsbh",
                ],
            }

            for target, keys in url_keys.items():

                for key in keys:

                    if key not in query:
                        continue

                    value = query[key][0]

                    normalized = normalize_field(
                        target,
                        value
                    )

                    if normalized:
                        fields[target] = make_field(
                            normalized,
                            1.0
                        )

                        break

        except Exception:
            pass

    # ========================================================
    # 3. JSON 二维码
    # ========================================================

    if (
        raw.startswith("{")
        and raw.endswith("}")
    ):

        try:
            obj = json.loads(raw)

            aliases = {
                "invoiceCode": (
                    "invoiceCode",
                    "invoice_code",
                    "fpdm",
                ),

                "invoiceNumber": (
                    "invoiceNumber",
                    "invoice_number",
                    "fphm",
                ),

                "date": (
                    "date",
                    "invoiceDate",
                    "kprq",
                ),

                "amount": (
                    "amount",
                    "totalAmount",
                    "je",
                ),

                "taxId": (
                    "taxId",
                    "tax_id",
                    "nsrsbh",
                ),
            }

            for field_name, keys in aliases.items():

                for key in keys:

                    if key in obj:

                        value = normalize_field(
                            field_name,
                            str(obj[key])
                        )

                        if value:
                            fields[field_name] = (
                                make_field(
                                    value,
                                    1.0
                                )
                            )

                        break

        except Exception:
            pass

    return fields


# ============================================================
# PDF 文本层
# ============================================================

def extract_pdf_text_items(
    page,
    page_number: int,
) -> list[dict]:
    """
    PyMuPDF words:
    x0, y0, x1, y1, word, block_no, line_no, word_no

    PDF 原生文字没有 OCR confidence，
    因此这里认为字符读取置信度 = 1.0。
    """

    words = page.get_text(
        "words",
        sort=True
    )

    result = []

    for word in words:

        if len(word) < 5:
            continue

        x1, y1, x2, y2, text = word[:5]

        text = str(text).strip()

        if not text:
            continue

        result.append({
            "text": text,
            "confidence": 1.0,
            "box": [
                float(x1),
                float(y1),
                float(x2),
                float(y2),
            ],
            "page": page_number,
            "source": "pdf",
        })

    return result


def pdf_has_useful_text(
    items: list[dict],
) -> bool:
    """
    判断是不是电子 PDF。

    防止一些扫描 PDF 只有几个隐藏字符，
    导致错误地跳过 OCR。
    """

    text = "".join(
        item["text"]
        for item in items
    )

    chinese_count = len(
        re.findall(
            r"[\u4e00-\u9fff]",
            text
        )
    )

    alnum_count = len(
        re.findall(
            r"[A-Za-z0-9]",
            text
        )
    )

    return (
        chinese_count >= 5
        or alnum_count >= 30
    )


# ============================================================
# PDF → OpenCV Image
# ============================================================

def render_pdf_page(
    page,
    dpi: int = 300,
) -> np.ndarray:

    pix = page.get_pixmap(
        dpi=dpi,
        alpha=False
    )

    img = np.frombuffer(
        pix.samples,
        dtype=np.uint8
    )

    img = img.reshape(
        pix.height,
        pix.width,
        pix.n
    )

    if pix.n == 4:
        return cv2.cvtColor(
            img,
            cv2.COLOR_RGBA2BGR
        )

    return cv2.cvtColor(
        img,
        cv2.COLOR_RGB2BGR
    )


# ============================================================
# 坐标关系
# ============================================================

def vertical_overlap(
    a,
    b,
) -> float:
    """
    两框垂直重叠比例。
    """

    top = max(a[1], b[1])
    bottom = min(a[3], b[3])

    overlap = max(
        0.0,
        bottom - top
    )

    return overlap / min(
        box_height(a),
        box_height(b)
    )


def horizontal_overlap(
    a,
    b,
) -> float:

    left = max(a[0], b[0])
    right = min(a[2], b[2])

    overlap = max(
        0.0,
        right - left
    )

    return overlap / min(
        box_width(a),
        box_width(b)
    )


# ============================================================
# Label
# ============================================================

LABELS = {

    "invoiceCode": [
        "发票代码",
        "發票代碼",
        "发票代码：",
    ],

    "invoiceNumber": [
        "发票号码",
        "發票號碼",
        "发票号码：",
        "票据号码",
    ],

    "date": [
        "开票日期",
        "開票日期",
        "开具日期",
        "日期",
    ],

    "amount": [
        "价税合计",
        "價稅合計",
        "小写",
        "小寫",
        "金额合计",
    ],

    "taxId": [
        "纳税人识别号",
        "納稅人識別號",
        "统一社会信用代码",
        "統一社會信用代碼",
    ],
}


def clean_label_text(
    text: str,
) -> str:

    return (
        text
        .replace(" ", "")
        .replace("：", "")
        .replace(":", "")
    )


def is_label(
    text: str,
    label: str,
) -> bool:

    t = clean_label_text(text)
    l = clean_label_text(label)

    return (
        l in t
        or t in l
    )


# ============================================================
# 候选是否合法
# ============================================================

def normalize_field(
    field_name: str,
    text: str,
) -> Optional[str]:

    if not text:
        return None

    text = text.strip()

    # 防止 label 本身成为 value
    for labels in LABELS.values():
        for label in labels:

            text = text.replace(
                label,
                ""
            )

    text = text.strip(" ：:")

    # -----------------------
    # invoiceCode
    # -----------------------

    if field_name == "invoiceCode":

        m = re.search(
            r"\d{10,12}",
            text
        )

        return (
            m.group()
            if m
            else None
        )

    # -----------------------
    # invoiceNumber
    # -----------------------

    if field_name == "invoiceNumber":

        m = re.search(
            r"\d{8,24}",
            text
        )

        return (
            m.group()
            if m
            else None
        )

    # -----------------------
    # date
    # -----------------------

    if field_name == "date":
        return normalize_date(text)

    # -----------------------
    # amount
    # -----------------------

    if field_name == "amount":
        return normalize_amount(text)

    # -----------------------
    # taxId
    # -----------------------

    if field_name == "taxId":

        m = re.search(
            r"[0-9A-Z]{15,20}",
            text.upper()
        )

        if not m:
            return None

        return normalize_tax_id(
            m.group()
        )

    return None


# ============================================================
# 同一个 OCR box 中：
#
# 发票号码：123456789
#
# ============================================================

def extract_value_from_same_item(
    field_name: str,
    item: dict,
) -> Optional[str]:

    text = item["text"]

    for label in LABELS[field_name]:

        if not is_label(text, label):
            continue

        cleaned = text

        cleaned = cleaned.replace(
            label,
            ""
        )

        cleaned = cleaned.lstrip(
            " :："
        )

        value = normalize_field(
            field_name,
            cleaned
        )

        if value:
            return value

    return None


# ============================================================
# 坐标找 Value
# ============================================================

def find_value_near_label(
    field_name: str,
    label_item: dict,
    items: list[dict],
) -> Optional[dict]:

    label_box = label_item["box"]
    lx1, ly1, lx2, ly2 = label_box

    label_h = box_height(label_box)
    label_w = box_width(label_box)

    candidates = []

    for item in items:

        if item is label_item:
            continue

        if item["page"] != label_item["page"]:
            continue

        value = normalize_field(
            field_name,
            item["text"]
        )

        if not value:
            continue

        box = item["box"]
        x1, y1, x2, y2 = box

        score = 0.0

        # ====================================================
        # 1. label 右侧
        #
        # 发票号码    12345678
        #
        # ====================================================

        if x1 >= lx2 - label_h:

            v_overlap = vertical_overlap(
                label_box,
                box
            )

            if v_overlap >= 0.25:

                distance = max(
                    0,
                    x1 - lx2
                )

                # 距离越近越好
                distance_score = max(
                    0,
                    1 - (
                        distance /
                        max(label_w * 8, 1)
                    )
                )

                score = (
                    4.0
                    + v_overlap * 2
                    + distance_score
                )

        # ====================================================
        # 2. label 下方
        #
        # 纳税人识别号
        # 9137XXXXXXXXXX
        #
        # ====================================================

        if y1 >= ly2 - label_h * 0.3:

            h_overlap = horizontal_overlap(
                label_box,
                box
            )

            vertical_distance = max(
                0,
                y1 - ly2
            )

            if (
                h_overlap >= 0.1
                or abs(x1 - lx1) <
                label_w * 2
            ):

                if vertical_distance < (
                    label_h * 5
                ):

                    distance_score = max(
                        0,
                        1 - (
                            vertical_distance /
                            max(label_h * 5, 1)
                        )
                    )

                    below_score = (
                        2.5
                        + h_overlap
                        + distance_score
                    )

                    score = max(
                        score,
                        below_score
                    )

        if score <= 0:
            continue

        # OCR confidence 也参与排序
        score += (
            item["confidence"]
            * 0.5
        )

        candidates.append({
            "value": value,
            "confidence": item[
                "confidence"
            ],
            "score": score,
            "item": item,
        })

    if not candidates:
        return None

    candidates.sort(
        key=lambda x: x["score"],
        reverse=True
    )

    return candidates[0]


# ============================================================
# Coordinate extractor
# ============================================================

def extract_by_coordinates(
    items: list[dict],
) -> dict:

    result = {}

    for field_name in FIELD_NAMES:

        best = None

        for item in items:

            # ------------------------------------------
            # 同一个框
            # 发票号码：12345678
            # ------------------------------------------

            same_value = (
                extract_value_from_same_item(
                    field_name,
                    item
                )
            )

            if same_value:

                candidate = {
                    "value": same_value,
                    "confidence": item[
                        "confidence"
                    ],
                    "score": 10.0,
                }

                if (
                    best is None
                    or candidate["score"]
                    > best["score"]
                ):
                    best = candidate

            # ------------------------------------------
            # 找 label
            # ------------------------------------------

            matched_label = False

            for label in LABELS[field_name]:

                if is_label(
                    item["text"],
                    label
                ):
                    matched_label = True
                    break

            if not matched_label:
                continue

            candidate = find_value_near_label(
                field_name,
                item,
                items,
            )

            if not candidate:
                continue

            if (
                best is None
                or candidate["score"]
                > best["score"]
            ):
                best = candidate

        if best:
            result[field_name] = make_field(
                best["value"],
                best["confidence"]
            )

    return result


# ============================================================
# 全文 Regex fallback
# ============================================================

def joined_text(
    items: list[dict],
) -> str:

    return "\n".join(
        item["text"]
        for item in items
    )


def compact_text(
    items: list[dict],
) -> str:

    return "".join(
        item["text"]
        .replace(" ", "")
        for item in items
    )


def average_matching_confidence(
    items: list[dict],
    value: str,
) -> float:

    normalized = (
        value
        .replace("-", "")
        .replace(".", "")
    )

    scores = []

    for item in items:

        text = re.sub(
            r"[^0-9A-Za-z]",
            "",
            item["text"]
        )

        if (
            normalized in text
            or text in normalized
        ):
            scores.append(
                item["confidence"]
            )

    if not scores:
        return 0.7

    return max(scores)


def extract_by_regex(
    items: list[dict],
) -> dict:

    text = compact_text(items)
    result = {}

    regexes = {

        "invoiceCode": [
            r"发票代码[:：]?(\d{10,12})",
            r"發票代碼[:：]?(\d{10,12})",
        ],

        "invoiceNumber": [
            r"发票号码[:：]?(\d{8,24})",
            r"發票號碼[:：]?(\d{8,24})",
        ],

        "date": [
            (
                r"(?:开票日期|開票日期|开具日期)"
                r"[:：]?"
                r"("
                r"\d{4}[年./\-]"
                r"\d{1,2}[月./\-]"
                r"\d{1,2}日?"
                r")"
            )
        ],

        "amount": [
            (
                r"(?:价税合计|價稅合計)"
                r".{0,30}?"
                r"(?:小写|小寫)?"
                r".{0,15}?"
                r"[¥￥]?"
                r"(\d+(?:\.\d{1,2})?)"
            )
        ],

        "taxId": [
            (
                r"(?:纳税人识别号|納稅人識別號|"
                r"统一社会信用代码|統一社會信用代碼)"
                r"[:：]?"
                r"([0-9A-Z]{15,20})"
            )
        ],
    }

    for field_name, patterns in regexes.items():

        for pattern in patterns:

            match = re.search(
                pattern,
                text,
                re.I
            )

            if not match:
                continue

            value = normalize_field(
                field_name,
                match.group(1)
            )

            if not value:
                continue

            confidence = (
                average_matching_confidence(
                    items,
                    match.group(1)
                )
            )

            # Regex fallback 略降 confidence
            confidence *= 0.9

            result[field_name] = make_field(
                value,
                confidence
            )

            break

    return result


# ============================================================
# 合并
#
# QR > Coordinate > Regex
# ============================================================

def merge_all_fields(
    qr_fields: dict,
    coordinate_fields: dict,
    regex_fields: dict,
) -> dict:

    result = empty_fields()

    for field_name in FIELD_NAMES:

        if field_name in qr_fields:

            result[field_name] = (
                qr_fields[field_name]
            )

        elif field_name in coordinate_fields:

            result[field_name] = (
                coordinate_fields[
                    field_name
                ]
            )

        elif field_name in regex_fields:

            result[field_name] = (
                regex_fields[
                    field_name
                ]
            )

    return result


# ============================================================
# 图片识别
# ============================================================

def recognize_image(
    path: str,
) -> dict:

    image = cv2.imread(path)

    if image is None:
        raise ValueError(
            f"无法读取图片: {path}"
        )

    qr_values = decode_qr(image)

    qr_raw = (
        qr_values[0]
        if qr_values
        else None
    )

    qr_fields = parse_qr(qr_raw)

    items = rapid_ocr_image(
        image,
        page=0
    )

    coordinate_fields = (
        extract_by_coordinates(items)
    )

    regex_fields = (
        extract_by_regex(items)
    )

    fields = merge_all_fields(
        qr_fields,
        coordinate_fields,
        regex_fields,
    )

    return {
        "rawText": (
            joined_text(items)
            if items
            else None
        ),
        "qrRaw": qr_raw,
        "fields": fields,
    }


# ============================================================
# PDF
# ============================================================

def recognize_pdf(
    path: str,
    dpi: int = 300,
) -> dict:

    doc = pymupdf.open(path)

    all_items = []
    qr_values = []

    for page_number, page in enumerate(doc):

        # ====================================================
        # 无论是不是电子 PDF，都渲染一次。
        #
        # 原因：
        # QR Code 是页面视觉内容，
        # 直接从渲染页扫最简单稳定。
        # ====================================================

        image = render_pdf_page(
            page,
            dpi=dpi
        )

        page_qrs = decode_qr(image)

        for qr in page_qrs:
            if qr not in qr_values:
                qr_values.append(qr)

        # ====================================================
        # 先尝试 PDF 原生文字
        # ====================================================

        pdf_items = extract_pdf_text_items(
            page,
            page_number
        )

        if pdf_has_useful_text(
            pdf_items
        ):
            all_items.extend(
                pdf_items
            )

        else:
            # ================================================
            # 扫描 PDF：
            # RapidOCR
            # ================================================

            ocr_items = rapid_ocr_image(
                image,
                page=page_number
            )

            all_items.extend(
                ocr_items
            )

    doc.close()

    # ========================================================
    # QR
    # ========================================================

    qr_raw = (
        qr_values[0]
        if qr_values
        else None
    )

    qr_fields = {}

    for qr in qr_values:

        parsed = parse_qr(qr)

        # 第一个合法值优先
        for key, value in parsed.items():

            if key not in qr_fields:
                qr_fields[key] = value

    # ========================================================
    # 坐标 + Regex
    # ========================================================

    coordinate_fields = (
        extract_by_coordinates(
            all_items
        )
    )

    regex_fields = (
        extract_by_regex(
            all_items
        )
    )

    fields = merge_all_fields(
        qr_fields,
        coordinate_fields,
        regex_fields,
    )

    return {
        "rawText": (
            joined_text(all_items)
            if all_items
            else None
        ),

        "qrRaw": qr_raw,

        "fields": fields,
    }


# ============================================================
# 统一入口
# ============================================================

def recognize_invoice(
    path: str,
) -> dict:

    path_obj = Path(path)

    if not path_obj.exists():
        raise FileNotFoundError(path)

    suffix = path_obj.suffix.lower()

    if suffix == ".pdf":
        return recognize_pdf(path)

    if suffix in {
        ".jpg",
        ".jpeg",
        ".png",
        ".bmp",
        ".webp",
        ".tif",
        ".tiff",
    }:
        return recognize_image(path)

    raise ValueError(
        f"不支持的文件格式: {suffix}"
    )


# ============================================================
# CLI
# ============================================================

if __name__ == "__main__":

    if len(sys.argv) < 2:

        print(
            "Usage: python invoice_ocr.py <invoice.pdf|image>"
        )

        sys.exit(1)

    invoice_path = sys.argv[1]

    result = recognize_invoice(
        invoice_path
    )

    print(
        json.dumps(
            result,
            ensure_ascii=False,
            indent=2
        )
    )