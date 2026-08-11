package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.dto.FormDtos.Condition;
import cn.sduonline.invoice.data.dto.FormDtos.FieldOption;
import cn.sduonline.invoice.data.dto.FormDtos.FormField;
import cn.sduonline.invoice.data.dto.FormDtos.FormSchema;
import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.exception.BusinessException;
import cn.sduonline.invoice.mapper.OrganizationMemberMapper;
import cn.sduonline.invoice.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
public class ApplicationAnswerValidator {
    private final OrganizationMemberMapper memberMapper;

    public ApplicationAnswerValidator(OrganizationMemberMapper memberMapper) {
        this.memberMapper = memberMapper;
    }

    public void validate(FormSchema schema, JsonNode answers, boolean submitting) {
        if (answers == null || !answers.isObject()) invalid();
        Map<String, FormField> fields = new HashMap<>();
        for (FormField field : schema.fields()) fields.put(field.key(), field);
        for (String key : answers.propertyNames()) {
            if (!fields.containsKey(key)) invalid();
        }
        for (FormField field : schema.fields()) {
            boolean visible = field.visibleWhen() == null || matches(field.visibleWhen(), answers);
            JsonNode value = answers.get(field.key());
            boolean present = present(value);
            if (!visible) {
                if (present) invalid();
                continue;
            }
            if (submitting && (Boolean.TRUE.equals(field.required())
                    || matches(field.requiredWhen(), answers)) && !present) {
                invalid();
            }
            if (present) validateValue(field, value);
        }
    }

    private void validateValue(FormField field, JsonNode value) {
        switch (field.type()) {
            case "TEXT", "TEXTAREA" -> requireText(value);
            case "PERSON" -> {
                requireText(value);
                if (!memberMapper.hasActiveMembership(TenantContext.requireOrganizationId(), value.asText())) {
                    invalid();
                }
            }
            case "NUMBER", "MONEY" -> validateNumber(field, value);
            case "DATE" -> validateDate(value);
            case "SINGLE_SELECT" -> validateSingleSelect(field, value);
            case "MULTI_SELECT" -> validateMultiSelect(field, value);
            case "ATTACHMENT" -> validateReferenceArray(field, value, true);
            case "INVOICE" -> validateReferenceArray(field, value, false);
            case "NOTICE" -> invalid();
            default -> invalid();
        }
    }

    private void validateNumber(FormField field, JsonNode value) {
        if (!value.isNumber()) invalid();
        var number = value.decimalValue();
        if (field.minValue() != null && number.compareTo(field.minValue()) < 0) invalid();
        if (field.maxValue() != null && number.compareTo(field.maxValue()) > 0) invalid();
        if ("MONEY".equals(field.type()) && number.scale() > 2) invalid();
    }

    private void validateDate(JsonNode value) {
        requireText(value);
        try {
            LocalDate.parse(value.asText());
        } catch (DateTimeParseException exception) {
            invalid();
        }
    }

    private void validateSingleSelect(FormField field, JsonNode value) {
        requireText(value);
        if (!optionValues(field).contains(value.asText())) invalid();
    }

    private void validateMultiSelect(FormField field, JsonNode value) {
        if (!value.isArray()) invalid();
        Set<String> allowed = optionValues(field);
        if (value.size() > allowed.size()) invalid();
        Set<String> duplicateGuard = new HashSet<>();
        for (JsonNode item : value) {
            if (!item.isTextual() || !allowed.contains(item.asText())
                    || !duplicateGuard.add(item.asText())) invalid();
        }
    }

    private void validateReferenceArray(FormField field, JsonNode value, boolean enforceFileLimit) {
        if (!value.isArray()) invalid();
        if (enforceFileLimit && field.fileRule() != null && value.size() > field.fileRule().maxFiles()) invalid();
        if (!enforceFileLimit && value.size() > 100) invalid();
        Set<String> duplicateGuard = new HashSet<>();
        for (JsonNode item : value) {
            if (!item.isTextual() || item.asText().isBlank() || !duplicateGuard.add(item.asText())) invalid();
        }
    }

    private Set<String> optionValues(FormField field) {
        Set<String> values = new HashSet<>();
        if (field.options() != null) {
            for (FieldOption option : field.options()) values.add(option.value());
        }
        return values;
    }

    private boolean matches(Condition condition, JsonNode answers) {
        if (condition == null) return false;
        JsonNode actual = answers.get(condition.fieldKey());
        return switch (condition.operator()) {
            case "NOT_EMPTY" -> present(actual);
            case "EQUALS" -> actual != null && actual.equals(condition.value());
            case "NOT_EQUALS" -> actual == null || !actual.equals(condition.value());
            case "IN" -> inCondition(actual, condition.value());
            default -> false;
        };
    }

    private boolean inCondition(JsonNode actual, JsonNode expected) {
        if (!present(actual) || expected == null || !expected.isArray()) return false;
        if (actual.isArray()) {
            for (JsonNode item : actual) if (contains(expected, item)) return true;
            return false;
        }
        return contains(expected, actual);
    }

    private boolean contains(JsonNode array, JsonNode value) {
        for (JsonNode item : array) if (item.equals(value)) return true;
        return false;
    }

    private boolean present(JsonNode value) {
        return value != null && !value.isNull()
                && (!value.isTextual() || !value.asText().isBlank())
                && (!value.isArray() || !value.isEmpty());
    }

    private void requireText(JsonNode value) {
        if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > 10000) invalid();
    }

    private void invalid() {
        throw new BusinessException(BizCode.PARAM_INVALID, HttpStatus.BAD_REQUEST);
    }
}
