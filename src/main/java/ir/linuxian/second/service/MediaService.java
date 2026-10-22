package ir.linuxian.second.service;

import ir.linuxian.second.entities.media.MediaAsset;
import ir.linuxian.second.repos.MediaAssetRepo;
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

    private final MediaAssetRepo mediaAssetRepo;
    private final Path uploadDir;

    public MediaService(MediaAssetRepo mediaAssetRepo,
                        @Value("${app.upload.dir:./uploads}") String uploadDir) throws IOException {
        this.mediaAssetRepo = mediaAssetRepo;
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(this.uploadDir);
    }

    public List<MediaAsset> findAll() {
        return mediaAssetRepo.findAll();
    }

    public MediaAsset store(MultipartFile file) throws IOException {
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0) {
            ext = original.substring(dot);
        }
        String filename = UUID.randomUUID() + ext;
        Path target = uploadDir.resolve(filename);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        MediaAsset asset = new MediaAsset();
        asset.setFilename(filename);
        asset.setOriginalName(original);
        asset.setContentType(file.getContentType());
        asset.setSize(file.getSize());
        asset.setUrl("/uploads/" + filename);
        return mediaAssetRepo.save(asset);
    }

    public void delete(Long id) throws IOException {
        MediaAsset asset = mediaAssetRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Media not found: " + id));
        try {
            Files.deleteIfExists(uploadDir.resolve(asset.getFilename()));
        } catch (IOException ignored) {
        }
        mediaAssetRepo.delete(asset);
    }
}
