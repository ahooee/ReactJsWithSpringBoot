package ir.linuxian.second.bot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One persisted Bot Manager setting (key/value). Admin edits via
 * {@code /api/admin/bot/settings} override {@code application.properties}.
 */
@Entity
@Table(name = "bot_settings")
public class BotSetting {

    @Id
    @Column(nullable = false, unique = true)
    private String key;

    @Column(columnDefinition = "TEXT")
    private String value;

    public BotSetting() {
    }

    public BotSetting(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
