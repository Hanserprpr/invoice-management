package cn.sduonline.invoice.scan;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class FileContentInspectorTests {
    @TempDir Path tempDir;
    private final FileContentInspector inspector = new FileContentInspector();

    @Test
    void calculatesBodyDigestAndDetectsPdfSignature() throws Exception {
        byte[] body = "%PDF-1.7\nbody\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        Path file = Files.write(tempDir.resolve("document.bin"), body);

        var result = inspector.inspect(file);

        assertThat(result.sizeBytes()).isEqualTo(body.length);
        assertThat(result.sha256()).isEqualTo(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(body)));
        assertThat(result.contentType()).isEqualTo("application/pdf");
    }

    @Test
    void doesNotTrustFileExtensionForUnknownContent() throws Exception {
        Path file = Files.writeString(tempDir.resolve("fake.pdf"), "not a pdf");

        assertThat(inspector.inspect(file).contentType())
                .isEqualTo("application/octet-stream");
    }

    @Test
    void recognizesOfdOnlyWhenZipContainsRootDescriptor() throws Exception {
        Path file = tempDir.resolve("document.ofd");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("OFD.xml"));
            zip.write("<ofd:OFD/>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        assertThat(inspector.inspect(file).contentType()).isEqualTo("application/ofd");
    }
}
