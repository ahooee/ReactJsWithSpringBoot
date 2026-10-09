package ir.linuxian.second.repos;

import ir.linuxian.second.entities.media.Media;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;

@RepositoryRestResource(exported = false)
public interface MediaRepo extends JpaRepository<Media, Long> {

    List<Media> findAllByOrderByCreatedAtDesc();
}
