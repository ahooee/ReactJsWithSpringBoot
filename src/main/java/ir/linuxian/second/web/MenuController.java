package ir.linuxian.second.web;

import ir.linuxian.second.dto.MenuResponse;
import ir.linuxian.second.entities.menu.Menu;
import ir.linuxian.second.service.MenuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/menus")
public class MenuController {

    private final MenuService menuService;

    @Autowired
    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping
    public List<MenuResponse> getAllMenus() {
        return menuService.getAllMenus();
    }

    @GetMapping("/{menuId}")
    public MenuResponse getMenuById(@PathVariable  Long menuId) {
        return menuService.getMenuById(menuId);
    }

    @GetMapping("/slug/{slug}")
    public MenuResponse getMenuBySlug(@PathVariable String slug) {
        return menuService.getMenuBySlug(slug);
    }

    @PostMapping
    public Menu createMenu(@RequestBody Menu menu){
        return menuService.addMenu(menu);
    }
    @PutMapping("/{menuId}")
    public Menu updateMenu(@PathVariable Long menuId, @RequestBody Menu menu) {

       return menuService.updateMenu(menuId, menu);
    }

    @DeleteMapping("/{menuId}")
    public ResponseEntity<?> deleteMenuById(@PathVariable Long menuId) {
        if(menuService.deleteMenuById(menuId)){
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

}
