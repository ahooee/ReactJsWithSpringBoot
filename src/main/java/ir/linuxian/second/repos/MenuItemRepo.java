package ir.linuxian.second.repos;

import ir.linuxian.second.entities.menu.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MenuItemRepo extends JpaRepository<MenuItem, Long> {

    List<MenuItem> findByMenuIdAndParentIsNullOrderBySortOrderAsc(Long menuId);
    List<MenuItem> findByParentIdOrderBySortOrderAsc(Long parentId);

    MenuItem getMenuItemsById(Long id);

    int countByMenuIdAndParentIsNull(Long menuId);

    int countByParentId(Long parentId);
}
