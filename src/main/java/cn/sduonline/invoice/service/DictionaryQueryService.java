package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.vo.DictionaryItemVO;
import cn.sduonline.invoice.mapper.DictionaryItemMapper;
import cn.sduonline.invoice.tenant.TenantContext;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DictionaryQueryService {
    private final DictionaryItemMapper itemMapper;

    public DictionaryQueryService(DictionaryItemMapper itemMapper) {
        this.itemMapper = itemMapper;
    }

    public List<DictionaryItemVO> listPublishedItems(String dictionaryCode) {
        String organizationId = TenantContext.requireOrganizationId();
        return itemMapper.findPublishedEnabled(organizationId, dictionaryCode.trim()).stream()
                .map(item -> new DictionaryItemVO(item.getId(), item.getDisplayName(),
                        item.getSortOrder() == null ? 0 : item.getSortOrder()))
                .toList();
    }
}
