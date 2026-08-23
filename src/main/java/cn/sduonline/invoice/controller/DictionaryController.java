package cn.sduonline.invoice.controller;

import cn.sduonline.invoice.data.vo.DictionaryItemVO;
import cn.sduonline.invoice.data.vo.Result;
import cn.sduonline.invoice.service.DictionaryQueryService;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/dictionaries")
public class DictionaryController {
    private final DictionaryQueryService dictionaryQueryService;

    public DictionaryController(DictionaryQueryService dictionaryQueryService) {
        this.dictionaryQueryService = dictionaryQueryService;
    }

    @GetMapping("/{dictionaryCode}/items")
    public Result<List<DictionaryItemVO>> listPublishedItems(
            @PathVariable @Pattern(regexp = "^[A-Z][A-Z0-9_]{0,49}$") String dictionaryCode) {
        return Result.ok(dictionaryQueryService.listPublishedItems(dictionaryCode));
    }
}
