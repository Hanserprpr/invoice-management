package cn.sduonline.invoice.service;

import cn.sduonline.invoice.exception.BusinessException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvoiceQrReplayTests {
    private final InvoiceQrParser parser = new InvoiceQrParser();

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            https://example.invalid/invoice?invoiceCode=123456789012&invoiceNumber=12345678 | 123456789012 | 12345678
            invoice_code=111122223333&invoice_number=87654321 | 111122223333 | 87654321
            code=998877665544;no=10203040 | 998877665544 | 10203040
            01,10,440001111122,00001234,100.00,20260813 | 440001111122 | 00001234
            https://example.invalid/?invoiceCode=12%2034&invoiceNumber=56%2078 | 12 34 | 56 78
            """)
    void replaysDesensitizedQrFormats(String raw, String code, String number) {
        var identity = parser.parse(raw);
        assertThat(identity.invoiceCode()).isEqualTo(code);
        assertThat(identity.invoiceNumber()).isEqualTo(number);
    }

    @ParameterizedTest
    @CsvSource({"not-an-invoice", "invoiceCode=only-code", "%%%"})
    void rejectsMalformedReplaySamples(String raw) {
        assertThatThrownBy(() -> parser.parse(raw)).isInstanceOf(BusinessException.class);
    }
}
