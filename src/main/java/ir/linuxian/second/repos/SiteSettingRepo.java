package ir.linuxian.second.repos;

import ir.linuxian.second.entities.settings.SiteSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteSettingRepo extends JpaRepository<SiteSetting, String> {
}
