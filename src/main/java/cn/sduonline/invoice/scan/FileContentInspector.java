package cn.sduonline.invoice.scan;

import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.ZipFile;

@Component
public class FileContentInspector {
    public Inspection inspect(Path path) throws IOException {
        return new Inspection(Files.size(path), sha256(path), detectContentType(path));
    }

    private String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = new BufferedInputStream(Files.newInputStream(path))) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private String detectContentType(Path path) throws IOException {
        byte[] header = new byte[8];
        int length;
        try (var input = Files.newInputStream(path)) {
            length = input.read(header);
        }
        if (startsWith(header, length, new byte[]{'%', 'P', 'D', 'F', '-'})) {
            return "application/pdf";
        }
        if (startsWith(header, length, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff})) {
            return "image/jpeg";
        }
        if (startsWith(header, length, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a})) {
            return "image/png";
        }
        if (startsWith(header, length, new byte[]{'P', 'K', 0x03, 0x04}) && isOfd(path)) {
            return "application/ofd";
        }
        return "application/octet-stream";
    }

    private boolean isOfd(Path path) {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            return zip.getEntry("OFD.xml") != null;
        } catch (IOException exception) {
            return false;
        }
    }

    private boolean startsWith(byte[] actual, int length, byte[] prefix) {
        if (length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (actual[i] != prefix[i]) return false;
        }
        return true;
    }

    public record Inspection(long sizeBytes, String sha256, String contentType) {
    }
}
