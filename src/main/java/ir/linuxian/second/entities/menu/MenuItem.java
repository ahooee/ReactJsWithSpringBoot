package ir.linuxian.second.entities.menu;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name="menu_items")
public class MenuItem {

    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Getter
    @Setter
    @Column(nullable = false)
    private String title;

    @Getter
    @Setter
    @Column(nullable = true)
    private String titleFa;

    @Getter
    @Setter
    @Column(nullable = false)
    private String url;

    @Getter
    @Setter
    @Column(name = "sort_order",nullable = false)
    private Integer sortOrder;

    @Getter
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_id", nullable = false)
    private Menu menu;

    /** Optional parent for nested dropdowns (e.g. Shop > Books > E-books). Null = top level.
     *  Product-page items can never be parents (enforced in MenuItemService). */
    @Getter
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private MenuItem parent;

    @Getter
    @Setter
    @OneToMany(mappedBy = "parent")
    @OrderBy("sortOrder ASC")
    private List<MenuItem> children = new ArrayList<>();

    public MenuItem() {}
    public MenuItem(String title, String url, Integer sortOrder){
        this.title = title;
        this.url = url;
        this.sortOrder = sortOrder;
    }
}
