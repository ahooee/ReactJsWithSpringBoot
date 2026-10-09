package ir.linuxian.second.web;

import ir.linuxian.second.entities.media.Media;
import ir.linuxian.second.service.MediaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    @GetMapping
    public List<Media> getAllMedia() {
        return mediaService.findAll();
    }

    @PostMapping("/upload")
    public Media upload(@RequestParam("file") MultipartFile file,
                        @RequestParam(value = "alt", required = false) String alt)
            throws IOException {
        return mediaService.store(file, alt);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        mediaService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
