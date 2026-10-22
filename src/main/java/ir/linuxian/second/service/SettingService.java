package ir.linuxian.second.service;

import ir.linuxian.second.entities.settings.SiteSetting;
import ir.linuxian.second.repos.SiteSettingRepo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SettingService {

    private final SiteSettingRepo repo;

    public SettingService(SiteSettingRepo repo) {
        this.repo = repo;
    }

    public Map<String, String> getAllAsMap() {
        return repo.findAll().stream()
                .collect(Collectors.toMap(SiteSetting::getKey, s -> s.getValue() == null ? "" : s.getValue()));
    }

    public List<SiteSetting> getAll() {
        return repo.findAll();
    }

    public SiteSetting upsert(String key, String value) {
        SiteSetting setting = repo.findById(key).orElse(new SiteSetting(key, value));
        setting.setValue(value);
        return repo.save(setting);
    }

    public void saveAll(Map<String, String> values) {
        values.forEach(this::upsert);
    }
}
