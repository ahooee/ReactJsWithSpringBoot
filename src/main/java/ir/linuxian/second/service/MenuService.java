package ir.linuxian.second.service;

import ir.linuxian.second.dto.MenuItemResponse;
import ir.linuxian.second.dto.MenuResponse;
import ir.linuxian.second.entities.menu.Menu;
import ir.linuxian.second.entities.menu.MenuItem;
import ir.linuxian.second.repos.MenuRepo;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MenuService {

    private final MenuRepo menuRepo;

    public MenuService(MenuRepo menuRepo) {
        this.menuRepo = menuRepo;
    }

    public List<MenuResponse> getAllMenus(){

        return menuRepo.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public MenuResponse getMenuById(Long menuId){
        Menu menu = menuRepo.findById(menuId).orElseThrow(()->new RuntimeException("Menu not found"+menuId));

        return toResponse(menu);
    }

    public MenuResponse getMenuBySlug(String slug){
        Menu menu = menuRepo.findBySlug(slug).orElseThrow(()->new RuntimeException("Menu not found! "+slug));

        return toResponse(menu);
    }

    public Menu addMenu(Menu menu){
        return menuRepo.save(menu);
    }

    public Menu updateMenu(Long menuId,Menu menu){
        Menu oldMenu = menuRepo.getMenuById(menuId);
        oldMenu.setSlug(menu.getSlug());
        oldMenu.setName(menu.getName());
        return menuRepo.save(oldMenu);
    }

    public boolean deleteMenuById(Long menuId){
        if(menuRepo.existsById(menuId)){
            menuRepo.deleteById(menuId);
            return true;
        }
        return false;
    }
    private MenuResponse toResponse(Menu menu) {

        List<MenuItemResponse> items = menu.getItems()
                .stream()
                .filter(item -> item.getParent() == null)
                .map(this::toItemResponse)
                .toList();

        return new MenuResponse(
                menu.getId(),
                menu.getName(),
                menu.getSlug(),
                items
        );
    }

    private MenuItemResponse toItemResponse(MenuItem item) {

        List<MenuItemResponse> children = item.getChildren()
                .stream()
                .map(this::toItemResponse)
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
