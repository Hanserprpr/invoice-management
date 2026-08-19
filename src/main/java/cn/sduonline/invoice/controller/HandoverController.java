package cn.sduonline.invoice.controller;

import cn.sduonline.invoice.data.dto.HandoverDtos.CreateHandoverRequest;
import cn.sduonline.invoice.data.vo.HandoverRecordVO;
import cn.sduonline.invoice.data.vo.Result;
import cn.sduonline.invoice.service.HandoverService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/handovers")
public class HandoverController {
    private final HandoverService service;
    public HandoverController(HandoverService service) { this.service = service; }

    /**
     * 获取纸质票据交接记录。
     */
    @GetMapping
    public Result<List<HandoverRecordVO>> list() { return Result.ok(service.list()); }

    /**
     * 创建纸质票据交接记录。
     */
    @PostMapping
    public ResponseEntity<Result<HandoverRecordVO>> create(
            Authentication authentication, @Valid @RequestBody CreateHandoverRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(service.create(authentication.getName(), request)));
    }
}
