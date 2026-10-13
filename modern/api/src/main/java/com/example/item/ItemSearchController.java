package com.example.item;

import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 문항 검색 API. 레거시 {@code search.php} 이관. 입력값이 이상해도 오류 대신 경고와 함께 200 으로 답한다. */
@RestController
@RequestMapping("/api")
public class ItemSearchController {

    private final ItemSearchService itemSearchService;

    public ItemSearchController(ItemSearchService itemSearchService) {
        this.itemSearchService = itemSearchService;
    }

    /** {@code GET /api/items/search?q=&unit=&level=&tag=&sort=&dir=&page=} — 공개 문항 검색. */
    @GetMapping("/items/search")
    public ItemSearchResponse search(@RequestParam MultiValueMap<String, String> params) {
        return itemSearchService.search(ItemSearchCondition.from(params));
    }
}
