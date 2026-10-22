package ir.linuxian.second.repos;

import ir.linuxian.second.entities.menu.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface MenuItemRepo extends JpaRepository<MenuItem, Long> {

    List<MenuItem> findByMenuIdOrderBySortOrderAsc(Long menuId);

    List<MenuItem> findByMenuIdAndParentIsNullOrderBySortOrderAsc(Long menuId);

    List<MenuItem> findByParentIdOrderBySortOrderAsc(Long parentId);

    /** All items of a menu with the given URL (duplicates possible, take care). */
    List<MenuItem> findAllByMenuIdAndUrl(Long menuId, String url);

    MenuItem getMenuItemsById(Long id);

    int countByMenuId(Long menuId);

    int countByParentId(Long parentId);

    /**
     * Legacy bridge: on databases created before the Menu → MenuItem
     * flattening, nested items still reference their parent through the
     * leftover parent_id column. Clear it so the parent menu (and its
     * items) can be deleted without FK violations. Fails silently on
     * databases where the column is already gone.
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE menu_items SET parent_id = NULL WHERE menu_id = :menuId", nativeQuery = true)
    void clearLegacyParents(@Param("menuId") Long menuId);
}
