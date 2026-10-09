package ir.linuxian.second.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
public class MenuResponse {

    private Long id;
    private String name;
    private String slug;
    private List<MenuItemResponse> menuItems;

}
