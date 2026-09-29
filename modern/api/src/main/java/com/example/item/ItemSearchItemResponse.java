package com.example.item;

import java.util.List;

/**
 * 문항 검색 결과의 한 행. 레거시 결과 표의 열(id, title, unit, level, tags)만 담는다(search.php:674-678).
 * {@code unit} 은 단원 코드, {@code tags} 는 태그 id 순 이름 목록(BR-04).
 */
public record ItemSearchItemResponse(
    Integer id,
    String title,
    String unit,
    Integer level,
    List<String> tags) {

    static ItemSearchItemResponse from(Item item) {
        return new ItemSearchItemResponse(
            item.getId(),
            item.getTitle(),
            item.getUnit().getCode(),
            item.getLevel(),
            item.getTags().stream().map(Tag::getName).toList());
    }
}
