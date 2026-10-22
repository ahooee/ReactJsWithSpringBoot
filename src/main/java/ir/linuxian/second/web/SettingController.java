package ir.linuxian.second.web;

import ir.linuxian.second.entities.settings.SiteSetting;
import ir.linuxian.second.service.SettingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
public class SettingController {

    private final SettingService settingService;

    public SettingController(SettingService settingService) {
        this.settingService = settingService;
    }

    @GetMapping
    public Map<String, String> getAll() {
        return settingService.getAllAsMap();
    }

    @GetMapping("/list")
    public List<SiteSetting> getList() {
        return settingService.getAll();
    }

    @PutMapping
    public ResponseEntity<Map<String, String>> saveAll(@RequestBody Map<String, String> values) {
        settingService.saveAll(values);
        return ResponseEntity.ok(settingService.getAllAsMap());
    }

    @PutMapping("/{key}")
    public SiteSetting saveOne(@PathVariable String key, @RequestBody Map<String, String> body) {
        return settingService.upsert(key, body.getOrDefault("value", ""));
    }
}
