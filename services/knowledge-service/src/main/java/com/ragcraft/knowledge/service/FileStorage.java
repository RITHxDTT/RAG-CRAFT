package com.ragcraft.knowledge.service;

import com.ragcraft.knowledge.KnowledgeProperties;
import com.ragcraft.common.web.AppException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Stores uploaded files flat under the storage directory as {uuid}.{ext}, like the FastAPI backend. */
@Component
public class FileStorage {

    private final Path root;

    public FileStorage(KnowledgeProperties properties) {
        this.root = Path.of(properties.getStorageDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot create storage directory " + root, ex);
        }
    }

    public String save(InputStream content, String extension) {
        String key = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        try {
            Files.copy(content, root.resolve(key), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new AppException(org.springframework.http.HttpStatus.INSUFFICIENT_STORAGE, "The file could not be stored.");
        }
        return key;
    }

    public Path path(String key) {
        Path resolved = root.resolve(key).normalize();
        if (!resolved.startsWith(root)) throw AppException.badRequest("Invalid storage key.");
        return resolved;
    }

    public boolean exists(String key) {
        return key != null && Files.exists(path(key));
    }

    public void delete(String key) {
        if (key == null) return;
        try {
            Files.deleteIfExists(path(key));
        } catch (IOException ignored) {
            // best effort; a periodic cleanup can remove leftovers
        }
    }
}
