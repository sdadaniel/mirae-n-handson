package com.example.item;

import java.util.List;

/**
 * 문항 검색 응답. {@code count} 는 현재 페이지 행 수가 아니라 조건에 맞는 전체 건수(search.php:655),
 * {@code message} 는 전체 건수가 0일 때만 채운다(BR-28).
 */
public record ItemSearchResponse(
    List<ItemSearchItemResponse> rows,
    long count,
    String message,
    List<String> warnings) {
}
