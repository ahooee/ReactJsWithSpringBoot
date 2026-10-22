package ir.linuxian.second.repos;

import ir.linuxian.second.entities.media.MediaAsset;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaAssetRepo extends JpaRepository<MediaAsset, Long> {
}
