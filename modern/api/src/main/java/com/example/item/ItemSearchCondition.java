package com.example.item;

import java.util.List;
import java.util.Map;
import org.springframework.util.MultiValueMap;

/**
 * 문항 검색 입력값(가공 전 문자열). 파라미터 이름은 레거시 {@code search.php} 와 같다.
 * 없는 파라미터는 빈 문자열, {@code page} 만 {@code "1"} 이다(search.php:77).
 */
public record ItemSearchCondition(
    String q,
    String unit,
    String level,
    String tag,
    String sort,
    String dir,
    String page) {

    private static final String DEFAULT_PAGE = "1";

    /**
     * PHP {@code $_GET} 규칙으로 값을 고른다. 같은 이름이 반복되면 PHP 처럼 마지막 값,
     * {@code name[]} 배열 형태면 첫 값(BR-06, search.php:55-79).
     */
    static ItemSearchCondition from(MultiValueMap<String, String> params) {
        return new ItemSearchCondition(
            phpParam(params, "q", ""),
            phpParam(params, "unit", ""),
            phpParam(params, "level", ""),
            phpParam(params, "tag", ""),
            phpParam(params, "sort", ""),
            phpParam(params, "dir", ""),
            phpParam(params, "page", DEFAULT_PAGE));
    }

    private static String phpParam(MultiValueMap<String, String> params, String name, String absent) {
        List<String> plain = params.get(name);
        if (plain != null && !plain.isEmpty()) {
            return plain.get(plain.size() - 1);
        }
        String arrayPrefix = name + "[";
        for (Map.Entry<String, List<String>> entry : params.entrySet()) {
            String key = entry.getKey();
            boolean isArrayKey = key.startsWith(arrayPrefix) && key.indexOf(']', arrayPrefix.length()) >= 0;
            if (isArrayKey && !entry.getValue().isEmpty()) {
                return entry.getValue().get(0);
            }
        }
        return absent;
    }
}
