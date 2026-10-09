package ir.linuxian.second.web;

import ir.linuxian.second.dto.MenuItemResponse;
import ir.linuxian.second.entities.menu.MenuItem;
import ir.linuxian.second.service.MenuItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/menus")
public class MenuItemController {

    private MenuItemService menuItemService;
    @Autowired
    public void setMenuItemService(MenuItemService menuItemService) {
        this.menuItemService = menuItemService;
    }

    @GetMapping("/{menuId}/items")
    public List<MenuItemResponse> getMenuList(@PathVariable Long menuId){

        return menuItemService.getItemsForMenu(menuId);


    }


    @GetMapping("/items/{menuId}")
    public MenuItemResponse getMenuItemById(@PathVariable Long menuId){
        return menuItemService.getMenuItemById(menuId);
    }


    @GetMapping("/items/{id}/children")
    public List<MenuItemResponse> getChildren(
            @PathVariable Long id
    ) {
        return menuItemService.getChildren(id);
    }

    @PostMapping("/{menuId}/items")
    public MenuItemResponse createMenuItem(
            @PathVariable Long menuId,
            @RequestParam(required = false) Long parentId,
            @RequestBody MenuItem item
    ) {
        return menuItemService.createItem(
                menuId,
                item,
                parentId
        );
    }

    @PutMapping("/items/{id}")
    public MenuItem updateMenuItem(
            @PathVariable Long id,
            @RequestBody MenuItem item
    ) {
        return menuItemService.updateMenuItem(id, item);
    }

    @DeleteMapping("/items/{id}")
    public ResponseEntity<?> deleteMenuItem(
            @PathVariable Long id
    ) {
        menuItemService.deleteItem(id);

        return ResponseEntity.noContent().build();
    }
    private MenuItemResponse toResponse(MenuItem item) {

        List<MenuItemResponse> children = item.getChildren()
                .stream()
                .map(this::toResponse)
                .toList();

        return new MenuItemResponse(
                item.getId(),
                item.getTitle(),
                item.getUrl(),
                item.getSortOrder(),
                children
        );
    }
}
