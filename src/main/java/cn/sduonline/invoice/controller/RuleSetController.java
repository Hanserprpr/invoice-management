package cn.sduonline.invoice.controller;

import cn.sduonline.invoice.data.dto.RuleDtos.*;
import cn.sduonline.invoice.data.vo.Result;
import cn.sduonline.invoice.data.vo.RuleSetVO;
import cn.sduonline.invoice.service.RuleSetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rule-sets")
public class RuleSetController {
    private final RuleSetService service;

    public RuleSetController(RuleSetService service) {
        this.service = service;
    }

    @GetMapping
    public Result<List<RuleSetVO>> list() {
        return Result.ok(service.list());
    }

    @PostMapping
    public ResponseEntity<Result<RuleSetVO>> create(Authentication authentication,
                                                    @Valid @RequestBody CreateRuleSetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(service.create(authentication.getName(), request)));
    }

    @PatchMapping("/{id}")
    public Result<RuleSetVO> update(@PathVariable String id, Authentication authentication,
                                    @Valid @RequestBody UpdateRuleSetRequest request) {
        return Result.ok(service.update(id, authentication.getName(), request));
    }

    @PostMapping("/{id}/versions")
    public ResponseEntity<Result<RuleSetVO.VersionVO>> publish(
            @PathVariable String id, Authentication authentication,
            @Valid @RequestBody PublishRuleVersionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(service.publish(id, authentication.getName(), request)));
    }
}
