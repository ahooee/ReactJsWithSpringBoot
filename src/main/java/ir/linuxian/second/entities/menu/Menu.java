package ir.linuxian.second.entities.menu;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name="menus")
public class Menu {


    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Getter
    @Setter
    @Column(nullable = false,unique = true)
    private String name;

    @Getter
    @Setter
    @Column(nullable = false,unique = true)
    private String slug;

    @Getter
    @Setter
    @OneToMany(mappedBy = "menu",cascade = CascadeType.ALL,orphanRemoval = true)
    @OrderBy("sortOrder ASC ")
    private List<MenuItem> items = new ArrayList<>();

    public Menu() {}
    public Menu(String name, String slug) {
        this.name = name;
        this.slug = slug;
    }


}
