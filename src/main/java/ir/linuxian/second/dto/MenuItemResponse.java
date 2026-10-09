package ir.linuxian.second.dto;

import lombok.Getter;
import org.apache.catalina.LifecycleState;

import java.util.List;
@Getter
public class MenuItemResponse {

    private Long id;
    private String title;
    private String url;
    private Integer sortOrder;
    private List<MenuItemResponse> children;
    public MenuItemResponse(){}
    public MenuItemResponse(Long id, String title, String url, Integer sortOrder, List<MenuItemResponse> children) {
        this.id = id;
        this.title = title;
        this.url = url;
        this.sortOrder = sortOrder;
        this.children = children;
    }


}
