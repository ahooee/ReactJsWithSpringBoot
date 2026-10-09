package ir.linuxian.second.entities.page;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name="pages")
public class Page {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;
    @Getter
    @Setter
    @Column(nullable = false)
    private String title;
    @Getter
    @Setter
    @Column(nullable = false, unique = true)
    private String slug;
    @Getter
    @Setter
    @Column(columnDefinition = "TEXT")
    private String content;

    public Page(){

    }

}
