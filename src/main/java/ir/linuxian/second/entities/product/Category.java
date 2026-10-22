package ir.linuxian.second.entities.product;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @Getter @Setter
    @Column(nullable = false, unique = true)
    private String name;

    @Getter @Setter
    private String nameFa;

    @Getter @Setter
    @Column(nullable = false, unique = true)
    private String slug;

    @Getter @Setter
    private String imageUrl;

    @Getter @Setter
    @Column(columnDefinition = "TEXT")
    private String description;

    public Category() {
    }
}
