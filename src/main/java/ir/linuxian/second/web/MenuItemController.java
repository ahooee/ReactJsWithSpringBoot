package ir.linuxian.second.web;

import ir.linuxian.second.dto.MenuItemResponse;
import ir.linuxian.second.entities.menu.MenuItem;
import ir.linuxian.second.repos.ProductRepo;
import ir.linuxian.second.service.MenuItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/menus")
public class MenuItemController {

    private MenuItemService menuItemService;
    private ProductRepo productRepo;
    @Autowired
    public void setMenuItemService(MenuItemService menuItemService) {
        this.menuItemService = menuItemService;
    }
    @Autowired
    public void setProductRepo(ProductRepo productRepo) {
        this.productRepo = productRepo;
    }

    @GetMapping("/{menuId}/items")
    public List<MenuItemResponse> getMenuList(@PathVariable Long menuId){

        return menuItemService.getItemsForMenu(menuId);


    }


    @GetMapping("/items/{menuId}")
    public MenuItemResponse getMenuItemById(@PathVariable Long menuId){
        return menuItemService.getMenuItemById(menuId);
    }


    @PostMapping("/{menuId}/items")
    public MenuItemResponse createMenuItem(
            @PathVariable Long menuId,
            @RequestBody MenuItem item
    ) {
        return menuItemService.createItem(
                menuId,
                item
        );
    }

    @PutMapping("/items/{id}")
    public MenuItemResponse updateMenuItem(
            @PathVariable Long id,
            @RequestBody MenuItem item
    ) {
        return menuItemService.updateMenuItem(id, item);
    }

    /**
     * Change an item's hierarchy level: { "parentId": 12 } nests it under
     * item 12, { "parentId": null } moves it back to the top level.
     */
    @PutMapping("/items/{id}/parent")
    public MenuItemResponse moveMenuItem(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body
    ) {
        Object raw = body == null ? null : body.get("parentId");
        Long parentId = null;
        if (raw instanceof Number number) {
            parentId = number.longValue();
        } else if (raw != null) {
            throw new RuntimeException("parentId must be a number or null");
        }
        return menuItemService.moveItem(id, parentId);
    }

    @DeleteMapping("/items/{id}")
    public ResponseEntity<?> deleteMenuItem(
            @PathVariable Long id
    ) {
        menuItemService.deleteItem(id);

        return ResponseEntity.noContent().build();
    }

    /**
     * Reconcile Shop product links: every product gets exactly one entry
     * under its type submenu (Shop -&gt; Books -&gt; E-books / Paper books).
     * Returns {created, moved, removed}.
     */
    @PostMapping("/sync-products")
    public Map<String, Integer> syncProducts() {
        return menuItemService.syncAllProducts(productRepo.findAll());
    }
}
