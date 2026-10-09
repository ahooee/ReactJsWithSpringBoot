package ir.linuxian.second.service;

import ir.linuxian.second.entities.media.Media;
import ir.linuxian.second.repos.MediaRepo;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@Service
public class MediaService {

    private final MediaRepo mediaRepo;

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    private Path root;

    public MediaService(MediaRepo mediaRepo) {
        this.mediaRepo = mediaRepo;
    }

    @PostConstruct
    public void init() throws IOException {
        root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public List<Media> findAll() {
        return mediaRepo.findAllByOrderByCreatedAtDesc();
    }

    public Media store(MultipartFile file, String alt) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("Uploaded file is empty");
        }
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String safeName = original.replaceAll("[^a-zA-Z0-9._-]", "_");
        String storedName = UUID.randomUUID().toString().substring(0, 8) + "-" + safeName;

        Path target = root.resolve(storedName).normalize();
        if (!target.startsWith(root)) {
            throw new RuntimeException("Invalid upload path");
        }
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        Media media = new Media();
        media.setFilename(original);
        media.setUrl("/uploads/" + storedName);
        media.setContentType(file.getContentType());
        media.setSize(file.getSize());
        media.setAlt(alt);
        return mediaRepo.save(media);
    }

    public void delete(Long id) {
        Media media = mediaRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Media not found: " + id));
        try {
            if (root != null) {
                Files.deleteIfExists(root.resolve(media.getUrl().replace("/uploads/", "")).normalize());
            }
        } catch (IOException ignored) {
            // The database record is removed even if the file is already gone.
        }
        mediaRepo.delete(media);
    }

    public Path getRoot() {
        return root;
    }
}
