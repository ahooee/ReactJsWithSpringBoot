package ir.linuxian.second.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class MenuItemResponse {

    private Long id;
    private String title;
    private String titleFa;
    private String url;
    private Integer sortOrder;
    private Long parentId;
    private List<MenuItemResponse> children;
    /** True when this item links to a product page: it can never be a parent. */
    private boolean productItem;

    public MenuItemResponse(){}

    public MenuItemResponse(Long id, String title, String titleFa, String url, Integer sortOrder) {
        this(id, title, titleFa, url, sortOrder, null, List.of(), false);
    }

    public MenuItemResponse(Long id, String title, String titleFa, String url,
                            Integer sortOrder, Long parentId, List<MenuItemResponse> children) {
        this(id, title, titleFa, url, sortOrder, parentId, children, false);
    }

    public MenuItemResponse(Long id, String title, String titleFa, String url,
                            Integer sortOrder, Long parentId, List<MenuItemResponse> children,
                            boolean productItem) {
        this.id = id;
        this.title = title;
        this.titleFa = titleFa;
        this.url = url;
        this.sortOrder = sortOrder;
        this.parentId = parentId;
        this.children = children == null ? List.of() : children;
        this.productItem = productItem;
    }


}
