package com.example.item;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 문항 검색 서비스. 레거시 {@code search.php} 의 buildSearchQuery · runSearchQuery 를 옮겼다.
 * 레거시 동작은 버그로 보여도 그대로 둔다(예: 난이도 미입력 시 5 제외, BR-14).
 * 입력값 문제는 예외가 아니라 경고 목록으로 돌려준다.
 */
@Service
@Transactional(readOnly = true)
public class ItemSearchService {

    static final int PAGE_SIZE = 20;
    static final int MAX_PAGE = 999;
    static final int KEYWORD_MAX_LENGTH = 100;
    static final int TAG_MAX_LENGTH = 50;
    /** 난이도 미입력 시 조건 {@code level < 5}. 주석은 "1~5 모두"라 하지만 코드대로 5를 뺀다(search.php:213-215). */
    static final int DEFAULT_LEVEL_EXCLUSIVE_UPPER = 5;
    static final String EMPTY_RESULT_MESSAGE = "검색 결과가 없습니다";

    static final String WARN_KEYWORD_TOO_LONG = "키워드가 너무 길어 100자까지만 사용했습니다.";
    static final String WARN_KEYWORD_ONE_CHAR = "키워드가 한 글자라 결과가 많을 수 있습니다.";
    static final String WARN_KEYWORD_WILDCARD = "키워드의 % 와 _ 는 와일드카드로 처리됩니다.";
    static final String WARN_UNIT_FORMAT = "단원 코드 형식이 올바르지 않습니다. (예: M5-1)";
    static final String WARN_UNIT_LOWERCASE = "단원 코드는 대문자로 입력하세요. (입력값 그대로 조회합니다)";
    static final String WARN_UNIT_UNKNOWN = "등록되지 않은 단원 코드입니다: ";
    static final String WARN_LEVEL_RANGE = "난이도는 1~5 사이여야 합니다.";
    static final String WARN_TAG_WILDCARD = "태그는 부분 일치를 지원하지 않습니다.";
    static final String WARN_TAG_TOO_LONG = "태그 이름이 너무 길어 50자까지만 사용했습니다.";
    static final String WARN_TAG_UNKNOWN = "등록되지 않은 태그입니다: ";
    static final String WARN_DIR_INVALID = "정렬 방향은 asc 또는 desc 만 가능합니다.";
    static final String WARN_SORT_UNKNOWN = "알 수 없는 정렬 기준입니다. 기본 정렬을 사용합니다.";
    static final String WARN_PAGE_INVALID = "페이지 번호가 올바르지 않아 1페이지를 표시합니다.";
    static final String WARN_PAGE_TOO_LARGE = "페이지 번호는 999 를 넘을 수 없습니다.";

    private static final Pattern UNIT_CODE_FORMAT = Pattern.compile("[A-Za-z][0-9]{1,2}-[0-9]{1,2}");
    private static final Pattern VALID_LEVEL = Pattern.compile("[1-5]");
    /** PCRE {@code /^[0-9]+$/} 는 끝의 줄바꿈 하나를 허용한다(search.php:337). */
    private static final Pattern PAGE_DIGITS = Pattern.compile("\\A([0-9]+)\\n?\\z");
    /** PHP {@code (int)"문자열"} 이 읽는 앞부분 숫자. */
    private static final Pattern PHP_NUMERIC_PREFIX =
        Pattern.compile("\\A[ \\t\\n\\r\\x0B\\f]*([+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?)");
    /** PHP {@code trim()} 기본 대상 문자. */
    private static final String PHP_TRIM_CHARS = " \t\n\r\0\u000B";

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("level"), Sort.Order.asc("id"));

    private static final Logger log = LoggerFactory.getLogger(ItemSearchService.class);

    private final ItemRepository itemRepository;
    private final UnitRepository unitRepository;
    private final TagRepository tagRepository;

    public ItemSearchService(ItemRepository itemRepository, UnitRepository unitRepository,
                             TagRepository tagRepository) {
        this.itemRepository = itemRepository;
        this.unitRepository = unitRepository;
        this.tagRepository = tagRepository;
    }

    /** 공개 문항 검색. 경고 순서와 문구는 레거시와 같다. */
    public ItemSearchResponse search(ItemSearchCondition condition) {
        List<String> warnings = new ArrayList<>();
        Specification<Item> spec = ItemSearchSpecs.isPublic();

        spec = applyKeyword(spec, condition.q(), warnings);
        spec = applyUnit(spec, condition.unit(), warnings);
        spec = applyLevel(spec, condition.level(), warnings);
        spec = applyTag(spec, condition.tag(), warnings);
        Sort sort = resolveSort(condition.sort(), condition.dir(), warnings);
        int page = resolvePage(condition.page(), warnings);

        Page<Item> result = itemRepository.findAll(spec, PageRequest.of(page - 1, PAGE_SIZE, sort));
        long total = result.getTotalElements();
        log.debug("item search page={} total={} warnings={}", page, total, warnings.size());

        String message = total == 0 ? EMPTY_RESULT_MESSAGE : null;
        return new ItemSearchResponse(toRows(result.getContent()), total, message, List.copyOf(warnings));
    }

    /** BR-07 · BR-08 · BR-09 · BR-10 (search.php:85-104). */
    private Specification<Item> applyKeyword(Specification<Item> spec, String raw, List<String> warnings) {
        String q = phpTrim(raw);
        if (mbLength(q) > KEYWORD_MAX_LENGTH) {
            q = mbSubstring(q, KEYWORD_MAX_LENGTH);
            warnings.add(WARN_KEYWORD_TOO_LONG);
        }
        if (q.isEmpty()) {
            return spec;
        }
        if (mbLength(q) == 1) {
            warnings.add(WARN_KEYWORD_ONE_CHAR);
        }
        if (q.contains("%") || q.contains("_")) {
            warnings.add(WARN_KEYWORD_WILDCARD);
        }
        return spec.and(ItemSearchSpecs.keywordContains(q));
    }

    /** BR-11 · BR-12 · BR-13 (search.php:109-147). 없는 단원도 조회는 그대로 한다(결과 0건). */
    private Specification<Item> applyUnit(Specification<Item> spec, String raw, List<String> warnings) {
        String unit = phpTrim(raw);
        if (unit.isEmpty()) {
            return spec;
        }
        if (!UNIT_CODE_FORMAT.matcher(unit).matches()) {
            warnings.add(WARN_UNIT_FORMAT);
        }
        if (hasAsciiLowercase(unit)) {
            warnings.add(WARN_UNIT_LOWERCASE);
        }
        if (unitRepository.findByCode(unit).isEmpty()) {
            warnings.add(WARN_UNIT_UNKNOWN + unit);
        }
        return spec.and(ItemSearchSpecs.unitCodeEquals(unit));
    }

    /** BR-14 · BR-15 · BR-16 (search.php:211-230). 범위 밖 값도 오류가 아니라 경고 후 정수로 비교한다. */
    private Specification<Item> applyLevel(Specification<Item> spec, String raw, List<String> warnings) {
        String level = phpTrim(raw);
        if (level.isEmpty()) {
            return spec.and(ItemSearchSpecs.levelBelow(DEFAULT_LEVEL_EXCLUSIVE_UPPER));
        }
        if (VALID_LEVEL.matcher(level).matches()) {
            return spec.and(ItemSearchSpecs.levelEquals(Integer.parseInt(level)));
        }
        warnings.add(WARN_LEVEL_RANGE);
        return spec.and(ItemSearchSpecs.levelEquals(phpIntval(level)));
    }

    /** BR-17 · BR-18 · BR-19 (search.php:235-261). 등록 여부는 레거시처럼 대소문자까지 정확히 비교한다. */
    private Specification<Item> applyTag(Specification<Item> spec, String raw, List<String> warnings) {
        String tag = phpTrim(raw);
        if (tag.isEmpty()) {
            return spec;
        }
        if (tag.contains("%") || tag.contains("_")) {
            warnings.add(WARN_TAG_WILDCARD);
        }
        if (mbLength(tag) > TAG_MAX_LENGTH) {
            tag = mbSubstring(tag, TAG_MAX_LENGTH);
            warnings.add(WARN_TAG_TOO_LONG);
        }
        String tagName = tag;
        // tag.name 콜레이션(utf8mb4_unicode_ci)은 대소문자를 무시하므로 한 건만 찾고 equals 로 다시 비교한다
        boolean known = tagRepository.findByName(tagName).filter(t -> t.getName().equals(tagName)).isPresent();
        if (!known) {
            warnings.add(WARN_TAG_UNKNOWN + tagName);
        }
        return spec.and(ItemSearchSpecs.hasTagNamed(tagName));
    }

    /** BR-20 ~ BR-24 (search.php:266-327). 방향 경고가 정렬 기준 경고보다 먼저 나온다. */
    private static Sort resolveSort(String rawSort, String rawDir, List<String> warnings) {
        String sort = asciiLower(phpTrim(rawSort));
        String dir = asciiLower(phpTrim(rawDir));
        if (!dir.equals("asc") && !dir.equals("desc")) {
            if (!dir.isEmpty()) {
                warnings.add(WARN_DIR_INVALID);
            }
            dir = "";
        }
        boolean desc = dir.equals("desc");
        boolean asc = dir.equals("asc");
        return switch (sort) {
            case "id" -> Sort.by(desc ? Sort.Order.desc("id") : Sort.Order.asc("id"));
            case "title" -> Sort.by(desc ? Sort.Order.desc("title") : Sort.Order.asc("title"), Sort.Order.asc("id"));
            case "unit" -> Sort.by(desc ? Sort.Order.desc("unit.code") : Sort.Order.asc("unit.code"),
                Sort.Order.desc("level"), Sort.Order.asc("id"));
            case "level" -> Sort.by(asc ? Sort.Order.asc("level") : Sort.Order.desc("level"), Sort.Order.asc("id"));
            case "created" -> Sort.by(asc ? Sort.Order.asc("createdAt") : Sort.Order.desc("createdAt"),
                Sort.Order.asc("id"));
            case "" -> DEFAULT_SORT;
            default -> {
                warnings.add(WARN_SORT_UNKNOWN);
                yield DEFAULT_SORT;
            }
        };
    }

    /** BR-26 · BR-27 (search.php:336-348). {@code page=0} 은 숫자라서 경고 없이 1이 된다. */
    private static int resolvePage(String raw, List<String> warnings) {
        int page = 1;
        Matcher digits = PAGE_DIGITS.matcher(raw);
        if (digits.matches()) {
            BigInteger value = new BigInteger(digits.group(1));
            page = value.compareTo(BigInteger.valueOf(MAX_PAGE)) > 0 ? MAX_PAGE + 1 : value.intValue();
        } else if (!raw.isEmpty() && !raw.equals("1")) {
            warnings.add(WARN_PAGE_INVALID);
        }
        if (page < 1) {
            page = 1;
        }
        if (page > MAX_PAGE) {
            page = MAX_PAGE;
            warnings.add(WARN_PAGE_TOO_LARGE);
        }
        return page;
    }

    /** 페이지 순서를 유지한 채 단원 · 태그를 한 번의 쿼리로 채운다. */
    private List<ItemSearchItemResponse> toRows(List<Item> pageItems) {
        if (pageItems.isEmpty()) {
            return List.of();
        }
        List<Integer> ids = pageItems.stream().map(Item::getId).toList();
        Map<Integer, Item> loaded = itemRepository.findAllByIdIn(ids).stream()
            .collect(Collectors.toMap(Item::getId, Function.identity()));
        return ids.stream().map(loaded::get).map(ItemSearchItemResponse::from).toList();
    }

    /** PHP 7 {@code (int)"문자열"}: 앞부분 숫자만 읽고, 없으면 0. 범위를 넘으면 int 범위로 자른다. */
    static int phpIntval(String value) {
        Matcher m = PHP_NUMERIC_PREFIX.matcher(value);
        if (!m.find()) {
            return 0;
        }
        double parsed = Double.parseDouble(m.group(1));
        if (Double.isInfinite(parsed) || Double.isNaN(parsed)) {
            return 0;
        }
        return (int) parsed;
    }

    static String phpTrim(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && PHP_TRIM_CHARS.indexOf(value.charAt(start)) >= 0) {
            start++;
        }
        while (end > start && PHP_TRIM_CHARS.indexOf(value.charAt(end - 1)) >= 0) {
            end--;
        }
        return value.substring(start, end);
    }

    private static int mbLength(String value) {
        return value.codePointCount(0, value.length());
    }

    private static String mbSubstring(String value, int codePoints) {
        return value.substring(0, value.offsetByCodePoints(0, codePoints));
    }

    /** PHP {@code strtoupper} 는 ASCII 만 바꾼다. */
    private static boolean hasAsciiLowercase(String value) {
        return value.chars().anyMatch(c -> c >= 'a' && c <= 'z');
    }

    /** PHP {@code strtolower} 는 ASCII 만 바꾼다. */
    private static String asciiLower(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            sb.append(c >= 'A' && c <= 'Z' ? (char) (c + ('a' - 'A')) : c);
        }
        return sb.toString();
    }
}
