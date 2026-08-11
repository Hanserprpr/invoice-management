package cn.sduonline.invoice.exception;

import cn.sduonline.invoice.data.enums.BizCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BusinessException extends RuntimeException {
    private final BizCode bizCode;
    private final HttpStatus status;

    public BusinessException(BizCode bizCode, HttpStatus status) {
        super(bizCode.getMsg());
        this.bizCode = bizCode;
        this.status = status;
    }

    public BusinessException(BizCode bizCode, HttpStatus status, String message) {
        super(message);
        this.bizCode = bizCode;
        this.status = status;
    }
}
