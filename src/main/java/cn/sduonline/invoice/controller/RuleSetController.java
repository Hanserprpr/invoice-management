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

    /**
     * 获取当前组织的规则集列表。
     *
     * <p>列出社团的全部规则集及其已发布版本，供项目在创建或编辑时选择 `ruleSetVersionId`。
     *
     * <ul>
     *   <li>权限：`role:manage`（默认属于 `CLUB_ADMIN`）。</li>
     *   <li>请求头：`X-Organization-Id` 必填。</li>
     *   <li>成功：`200`。</li>
     *   <li>常见错误：`20003` 缺少 `role:manage`。</li>
     * </ul>
     */
    @GetMapping
    public Result<List<RuleSetVO>> list() {
        return Result.ok(service.list());
    }

    /**
     * 创建规则集。
     *
     * <p>新建一个规则集容器，本身不含规则；规则要通过发布版本写入。设为默认时会自动取消社团内原来的默认规则集。
     *
     * <ul>
     *   <li>权限：`role:manage`。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：`name` 必填；`isDefault` 可选，默认 `false`。</li>
     *   <li>成功：`201`，`data.status` 为 `ACTIVE`。</li>
     *   <li>常见错误：`20003` 缺少 `role:manage`。</li>
     * </ul>
     */
    @PostMapping
    public ResponseEntity<Result<RuleSetVO>> create(Authentication authentication,
                                                    @Valid @RequestBody CreateRuleSetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(service.create(authentication.getName(), request)));
    }

    /**
     * 更新指定规则集。
     *
     * <p>修改规则集名称、默认标记或启用状态。已发布的版本内容不受影响，也没有任何接口可以改动它们。
     *
     * <ul>
     *   <li>权限：`role:manage`。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：`version` 必填；`name`、`isDefault`、`status`（`ACTIVE`/`DISABLED`）可选。</li>
     *   <li>成功：`200`。</li>
     *   <li>常见错误：`53000` 规则集不存在；`10007` 版本冲突。</li>
     * </ul>
     */
    @PatchMapping("/{id}")
    public Result<RuleSetVO> update(@PathVariable String id, Authentication authentication,
                                    @Valid @RequestBody UpdateRuleSetRequest request) {
        return Result.ok(service.update(id, authentication.getName(), request));
    }

    /**
     * 发布指定规则集的新版本。
     *
     * <p>发布一份不可变的规则版本。项目通过 `ruleSetVersionId` 固定引用某个版本，之后再发布新版本不会改变历史项目的判定口径。
     *
     * <ul>
     *   <li>权限：`role:manage`，且规则集处于 `ACTIVE`。</li>
     *   <li>请求头：`X-Organization-Id` 必填；写操作还需 `X-XSRF-TOKEN`（取自 `XSRF-TOKEN` Cookie）。</li>
     *   <li>请求体：`rules`（1–50 条）与 `effectiveAt` 必填；每条规则的 `code` 取值 `MAX_FACE_AMOUNT`、`MAX_CLAIMED_AMOUNT`、`ALLOWED_INVOICE_TYPES`、`REQUIRE_SELLER_TAX_NO`、`REQUIRE_BUYER_TAX_NO`，`severity` 取值 `BLOCK`、`WARNING`、`INFO`；金额类规则填 `amount`，枚举类规则填 `values`。</li>
     *   <li>成功：`201`，`versionNo` 从 `1` 开始递增。</li>
     *   <li>常见错误：`10001` 规则集已停用；`10000` 规则定义非法或重复；`53000` 规则集不存在。</li>
     * </ul>
     */
    @PostMapping("/{id}/versions")
    public ResponseEntity<Result<RuleSetVO.VersionVO>> publish(
            @PathVariable String id, Authentication authentication,
            @Valid @RequestBody PublishRuleVersionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.ok(service.publish(id, authentication.getName(), request)));
    }
}
