package cn.sduonline.invoice.service;

import cn.sduonline.invoice.data.dto.FormDtos.Condition;
import cn.sduonline.invoice.data.dto.FormDtos.FieldOption;
import cn.sduonline.invoice.data.dto.FormDtos.FormField;
import cn.sduonline.invoice.data.dto.FormDtos.FormSchema;
import cn.sduonline.invoice.data.enums.BizCode;
import cn.sduonline.invoice.exception.BusinessException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class FormSchemaValidator {
    private static final Set<String> SELECT_TYPES = Set.of("SINGLE_SELECT", "MULTI_SELECT");
    private static final Set<String> NUMBER_TYPES = Set.of("NUMBER", "MONEY");

    private final Validator validator;

    public FormSchemaValidator(Validator validator) {
        this.validator = validator;
    }

    public void validate(FormSchema schema, boolean requireFields) {
        Set<ConstraintViolation<FormSchema>> violations = validator.validate(schema);
        if (!violations.isEmpty() || requireFields && schema.fields().isEmpty()) invalid();
        Set<String> previousKeys = new HashSet<>();
        for (FormField field : schema.fields()) {
            if (!previousKeys.add(field.key())) invalid();
            validateTypeConfiguration(field);
            validateCondition(field.visibleWhen(), previousKeys, field.key());
            validateCondition(field.requiredWhen(), previousKeys, field.key());
        }
    }

    private void validateTypeConfiguration(FormField field) {
        List<FieldOption> options = field.options();
        if (SELECT_TYPES.contains(field.type())) {
            if (options == null || options.isEmpty()) invalid();
            Set<String> values = new HashSet<>();
            for (FieldOption option : options) {
                if (!values.add(option.value())) invalid();
            }
        } else if (options != null && !options.isEmpty()) {
            invalid();
        }
        if (NUMBER_TYPES.contains(field.type())) {
            if (field.minValue() != null && field.maxValue() != null
                    && field.maxValue().compareTo(field.minValue()) < 0) invalid();
        } else if (field.minValue() != null || field.maxValue() != null) {
            invalid();
        }
        if ("ATTACHMENT".equals(field.type())) {
            if (field.fileRule() == null) invalid();
        } else if (field.fileRule() != null) {
            invalid();
        }
        if ("NOTICE".equals(field.type()) && Boolean.TRUE.equals(field.required())) invalid();
    }

    private void validateCondition(Condition condition, Set<String> previousKeys, String currentKey) {
        if (condition == null) return;
        if (currentKey.equals(condition.fieldKey()) || !previousKeys.contains(condition.fieldKey())) invalid();
        if ("NOT_EMPTY".equals(condition.operator())) {
            if (condition.value() != null && !condition.value().isNull()) invalid();
        } else if (condition.value() == null || condition.value().isNull()) {
            invalid();
        } else if ("IN".equals(condition.operator())
                && (!condition.value().isArray() || condition.value().isEmpty())) {
            invalid();
        }
    }

    private void invalid() {
        throw new BusinessException(BizCode.PARAM_INVALID, HttpStatus.BAD_REQUEST);
    }
}
