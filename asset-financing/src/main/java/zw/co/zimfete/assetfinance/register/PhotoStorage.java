package zw.co.zimfete.assetfinance.register;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import zw.co.zimfete.assetfinance.config.ZimfeteProperties;
import zw.co.zimfete.assetfinance.web.BusinessRuleException;

/**
 * Stores photos on the server's disk (on Oracle Cloud, a block volume that is backed up).
 * File names are generated, never taken from the upload, so a crafted name cannot escape the folder.
 */
@Component
public class PhotoStorage {

    private static final Map<String, String> ALLOWED = Map.of("image/jpeg", ".jpg", "image/png", ".png",
            "image/webp", ".webp");

    private final Path root;

    public PhotoStorage(ZimfeteProperties properties) {
        this.root = Path.of(properties.storage().photoDirectory()).toAbsolutePath().normalize();
    }

    public String store(long assetId, String contentType, InputStream content) {
        String extension = ALLOWED.get(contentType);
        if (extension == null) {
            throw new BusinessRuleException("Only JPEG, PNG or WebP photos are accepted");
        }
        String name = assetId + "/" + UUID.randomUUID() + extension;
        Path target = root.resolve(name);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store photo", e);
        }
        return name;
    }

    public Resource load(String storedName) {
        Path path = root.resolve(storedName).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid photo path");
        }
        return new PathResource(path);
    }
}
