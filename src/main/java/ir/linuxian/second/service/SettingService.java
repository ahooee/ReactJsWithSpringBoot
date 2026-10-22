package ir.linuxian.second.service;

import ir.linuxian.second.entities.settings.SiteSetting;
import ir.linuxian.second.repos.SiteSettingRepo;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SettingService {

    private final SiteSettingRepo repo;

    public SettingService(SiteSettingRepo repo) {
        this.repo = repo;
    }

    /**
     * Price (in rials) applied when a library book is turned into its shop
     * product for the first time. Configurable from Admin → Settings → Shop
     * (key {@code library.ebookPrice}).
     */
    public BigDecimal libraryEbookPrice() {
        String raw = repo.findById("library.ebookPrice").map(SiteSetting::getValue).orElse(null);
        try {
            long value = Long.parseLong(raw == null || raw.isBlank() ? "500000" : raw.trim());
            return BigDecimal.valueOf(Math.max(0, value));
        } catch (NumberFormatException e) {
            return BigDecimal.valueOf(500000);
        }
    }

    public Map<String, String> getAllAsMap() {
        return repo.findAll().stream()
                .collect(Collectors.toMap(SiteSetting::getKey, s -> s.getValue() == null ? "" : s.getValue()));
    }

    /** Raw value of one setting ({@code null} when unset). */
    public String valueOf(String key) {
        return repo.findById(key).map(SiteSetting::getValue).orElse(null);
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
