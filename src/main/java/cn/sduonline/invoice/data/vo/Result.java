package cn.sduonline.invoice.data.vo;

import cn.sduonline.invoice.data.enums.BizCode;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 统一接口返回体。
 *
 * @param <T> 业务数据类型
 */
@Data
@NoArgsConstructor
public class Result<T> {

    /** 业务状态码。 */
    private Integer code;

    /** 业务数据。 */
    private T data;

    /** 提示信息。 */
    private String msg;

    /** 响应时间戳（毫秒）。 */
    private Instant timestamp;

    public Result(int code, T data, String msg) {
        this.code = code;
        this.data = data;
        this.msg = msg;
        this.timestamp = Instant.now();
    }

    /**
     * 成功，无数据。
     */
    public static <T> Result<T> ok() {
        return new Result<>(BizCode.SUCCESS.getCode(), null, BizCode.SUCCESS.getMsg());
    }

    /**
     * 成功并携带数据。
     */
    public static <T> Result<T> ok(T data) {
        return new Result<>(BizCode.SUCCESS.getCode(), data, BizCode.SUCCESS.getMsg());
    }

    /**
     * 按业务码失败。
     */
    public static <T> Result<T> fail(BizCode bizCode) {
        return new Result<>(bizCode.getCode(), null, bizCode.getMsg());
    }

    /**
     * 按业务码失败，并追加更具体的原因（如字段名、上下文）。
     */
    public static <T> Result<T> fail(BizCode bizCode, String extraMsg) {
        return new Result<>(bizCode.getCode(), null, bizCode.getMsg() + "：" + extraMsg);
    }

    /**
     * 按业务码失败，并携带数据。
     */
    public static <T> Result<T> fail(BizCode bizCode, T data) {
        return new Result<>(bizCode.getCode(), data, bizCode.getMsg());
    }

    /**
     * 按业务码失败，携带数据并追加说明。
     */
    public static <T> Result<T> fail(BizCode bizCode, T data, String extraMsg) {
        return new Result<>(bizCode.getCode(), data, bizCode.getMsg() + "：" + extraMsg);
    }
}