package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.po.DictionaryItem;
import cn.sduonline.invoice.data.po.DictionaryVersion;
import cn.sduonline.invoice.mapper.DictionaryItemMapper;
import cn.sduonline.invoice.mapper.DictionaryVersionMapper;
import cn.sduonline.invoice.util.UlidGenerator;
import org.springframework.stereotype.Service;

@Service
public class DictionaryTemplateService {
    private final DictionaryVersionMapper versionMapper;
    private final DictionaryItemMapper itemMapper;

    public DictionaryTemplateService(DictionaryVersionMapper versionMapper,
                                     DictionaryItemMapper itemMapper) {
        this.versionMapper = versionMapper;
        this.itemMapper = itemMapper;
    }

    public void clonePublishedTemplates(String organizationId, String actorCasId) {
        for (DictionaryVersion template : versionMapper.findPublishedTemplates()) {
            String versionId = UlidGenerator.next();
            versionMapper.insert(DictionaryVersion.builder()
                    .id(versionId)
                    .organizationId(organizationId)
                    .dictionaryType(template.getDictionaryType())
                    .versionNo(1)
                    .status("PUBLISHED")
                    .publishedByCasId(actorCasId)
                    .publishedAt(java.time.Instant.now())
                    .build());
            for (DictionaryItem item : itemMapper.findTemplateItems(template.getId())) {
                itemMapper.insert(DictionaryItem.builder()
                        .id(UlidGenerator.next())
                        .organizationId(organizationId)
                        .dictionaryVersionId(versionId)
                        .code(item.getCode())
                        .displayName(item.getDisplayName())
                        .sortOrder(item.getSortOrder())
                        .enabled(item.getEnabled())
                        .metadataJson(item.getMetadataJson())
                        .build());
            }
        }
    }
}
