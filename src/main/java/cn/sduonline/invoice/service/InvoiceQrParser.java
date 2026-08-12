package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class InvoiceQrParser {
    public record Identity(String digitalInvoiceNo, String invoiceCode, String invoiceNumber) {
    }

    public Identity parse(String raw) {
        String value = raw == null ? "" : raw.trim();
        Map<String, String> params = parseParams(value);
        String digital = first(params, "digitalInvoiceNo", "digital_invoice_no", "ofdNo");
        String code = first(params, "invoiceCode", "invoice_code", "code");
        String number = first(params, "invoiceNumber", "invoice_number", "number", "no");
        if (hasText(digital) || hasText(code) && hasText(number)) {
            return new Identity(clean(digital), clean(code), clean(number));
        }
        String[] parts = value.split(",", -1);
        if (parts.length >= 4 && hasText(parts[2]) && hasText(parts[3])) {
            return new Identity(null, clean(parts[2]), clean(parts[3]));
        }
        throw new BusinessException(BizCode.SCAN_PARSE_FAILED, HttpStatus.BAD_REQUEST);
    }

    private Map<String, String> parseParams(String raw) {
        int question = raw.indexOf('?');
        String query = question >= 0 ? raw.substring(question + 1) : raw;
        Map<String, String> result = new LinkedHashMap<>();
        for (String pair : query.split("[&;]")) {
            int separator = pair.indexOf('=');
            if (separator > 0) {
                result.put(decode(pair.substring(0, separator)), decode(pair.substring(separator + 1)));
            }
        }
        return result;
    }

    private String first(Map<String, String> params, String... keys) {
        for (String key : keys) if (hasText(params.get(key))) return params.get(key);
        return null;
    }

    private String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(BizCode.SCAN_PARSE_FAILED, HttpStatus.BAD_REQUEST);
        }
    }

    private String clean(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
