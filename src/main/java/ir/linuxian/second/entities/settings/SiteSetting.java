package ir.linuxian.second.entities.settings;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "site_settings")
public class SiteSetting {

    @Id
    @Getter
    @Column(nullable = false, unique = true)
    private String key;

    @Getter @Setter
    @Column(columnDefinition = "TEXT")
    private String value;

    public SiteSetting() {
    }

    public SiteSetting(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public void setKey(String key) {
        this.key = key;
    }
}
