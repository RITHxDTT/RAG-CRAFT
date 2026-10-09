package com.ragcraft.knowledge.service;

import com.ragcraft.common.web.AppException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

/** Extension, size, and magic-byte checks mirroring backend/app/services/file_validation.py. */
public final class FileValidation {

    public static final List<String> EXTENSIONS = List.of("pdf", "docx", "txt", "md", "xlsx");
    private static final Map<String, String> MIME = Map.of(
            "pdf", "application/pdf",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "txt", "text/plain",
            "md", "text/markdown");

    private FileValidation() {}

    public record Checked(String extension, String mimeType, byte[] bytes, String sha256) {}

    public static Checked check(MultipartFile file, int maxMb) {
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String extension = dot < 0 ? "" : original.substring(dot + 1).toLowerCase();
        if (!EXTENSIONS.contains(extension)) throw AppException.badRequest("Unsupported file type.");
        if (file.isEmpty()) throw AppException.badRequest("File is empty.");
        if (file.getSize() > (long) maxMb * 1024 * 1024) throw AppException.badRequest("Maximum size is " + maxMb + " MB.");
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw AppException.badRequest("The file could not be read.");
        }
        if ("pdf".equals(extension) && !startsWith(bytes, "%PDF")) throw AppException.badRequest("The file is not a valid PDF.");
        if (("docx".equals(extension) || "xlsx".equals(extension)) && !(bytes.length > 1 && bytes[0] == 'P' && bytes[1] == 'K'))
            throw AppException.badRequest("The file is not a valid Office document.");
        return new Checked(extension, MIME.get(extension), bytes, sha256(bytes));
    }

    private static boolean startsWith(byte[] bytes, String prefix) {
        byte[] expected = prefix.getBytes(StandardCharsets.US_ASCII);
        return bytes.length >= expected.length && Arrays.equals(Arrays.copyOf(bytes, expected.length), expected);
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
