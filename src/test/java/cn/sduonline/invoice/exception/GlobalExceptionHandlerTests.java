package cn.sduonline.invoice.exception;

import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.data.vo.Result;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;
class GlobalExceptionHandlerTests {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsFrameworkErrorsToStableHttpAndBusinessCodes() {
        assertResponse(handler.handleNotFound(
                        new NoResourceFoundException(HttpMethod.GET, "/missing", "/missing")),
                HttpStatus.NOT_FOUND, BizCode.RESOURCE_NOT_FOUND);
        assertResponse(handler.handleMethodNotSupported(
                        new HttpRequestMethodNotSupportedException("POST")),
                HttpStatus.METHOD_NOT_ALLOWED, BizCode.NOT_SUPPORTED);
        assertResponse(handler.handleBadRequest(new IllegalArgumentException("invalid")),
                HttpStatus.BAD_REQUEST, BizCode.PARAM_INVALID);
        assertResponse(handler.handleUploadTooLarge(new MaxUploadSizeExceededException(1024)),
                HttpStatus.PAYLOAD_TOO_LARGE, BizCode.FILE_TOO_LARGE);
    }

    private void assertResponse(ResponseEntity<Result<Void>> response, HttpStatus status,
                                BizCode bizCode) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(bizCode.getCode());
        assertThat(response.getBody().getMsg()).isEqualTo(bizCode.getMsg());
    }
}
