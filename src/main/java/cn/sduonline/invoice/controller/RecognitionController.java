package cn.sduonline.invoice.controller;

import cn.sduonline.invoice.data.dto.RecognitionDtos.ConfirmSuggestionsRequest;
import cn.sduonline.invoice.data.vo.RecognitionJobVO;
import cn.sduonline.invoice.data.vo.RecognitionSuggestionVO;
import cn.sduonline.invoice.data.vo.Result;
import cn.sduonline.invoice.service.RecognitionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/invoices/{invoiceId}/recognition")
public class RecognitionController {
    private final RecognitionService recognitionService;

    public RecognitionController(RecognitionService recognitionService) {
        this.recognitionService = recognitionService;
    }

    /**
     * 启动指定发票的智能识别任务。
     */
    @PostMapping
    public ResponseEntity<Result<RecognitionJobVO>> start(
            @PathVariable String invoiceId, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Result.ok(recognitionService.start(invoiceId, authentication.getName())));
    }

    /**
     * 获取指定发票的识别任务记录。
     */
    @GetMapping("/jobs")
    public Result<List<RecognitionJobVO>> jobs(@PathVariable String invoiceId) {
        return Result.ok(recognitionService.jobs(invoiceId));
    }

    /**
     * 获取指定发票的识别建议。
     */
    @GetMapping("/suggestions")
    public Result<List<RecognitionSuggestionVO>> suggestions(@PathVariable String invoiceId) {
        return Result.ok(recognitionService.suggestions(invoiceId));
    }

    /**
     * 确认或修正指定发票的识别建议。
     */
    @PutMapping("/suggestions")
    public Result<List<RecognitionSuggestionVO>> confirm(
            @PathVariable String invoiceId, Authentication authentication,
            @Valid @RequestBody ConfirmSuggestionsRequest request) {
        return Result.ok(recognitionService.confirm(invoiceId, authentication.getName(), request));
    }
}
