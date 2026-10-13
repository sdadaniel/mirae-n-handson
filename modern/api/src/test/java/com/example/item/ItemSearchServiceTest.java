package com.example.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class ItemSearchServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private UnitRepository unitRepository;

    @Mock
    private TagRepository tagRepository;

    @InjectMocks
    private ItemSearchService itemSearchService;

    private final Unit unit = ItemFixtures.unit(1, "M5-1", "분수의 덧셈과 뺄셈", 5);

    private static ItemSearchCondition condition(String q, String unit, String level, String tag,
                                                 String sort, String dir, String page) {
        return new ItemSearchCondition(q, unit, level, tag, sort, dir, page);
    }

    private static ItemSearchCondition none() {
        return condition("", "", "", "", "", "", "1");
    }

    private void stubEmptyResult() {
        when(itemRepository.findAll(ArgumentMatchers.<Specification<Item>>any(), ArgumentMatchers.any(Pageable.class)))
            .thenReturn(Page.empty());
    }

    private Pageable capturePageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(itemRepository).findAll(ArgumentMatchers.<Specification<Item>>any(), captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("기본 호출: 경고 없음, 1페이지 20건, level DESC · id ASC, 행은 페이지 순서대로")
    void defaultSearch() {
        Item hard = ItemFixtures.item(18, unit, "분수의 뺄셈 문장제", 4, ItemStatus.ACTIVE,
            ItemFixtures.tag(2, "문장제"), ItemFixtures.tag(4, "오답률높음"));
        Item easy = ItemFixtures.item(1, unit, "분모가 같은 분수의 덧셈", 1, ItemStatus.ACTIVE);
        when(itemRepository.findAll(ArgumentMatchers.<Specification<Item>>any(), ArgumentMatchers.any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(hard, easy), PageRequest.of(0, 20), 25));
        when(itemRepository.findAllByIdIn(anyCollection())).thenReturn(List.of(easy, hard));

        ItemSearchResponse response = itemSearchService.search(none());

        assertThat(response.count()).isEqualTo(25);
        assertThat(response.message()).isNull();
        assertThat(response.warnings()).isEmpty();
        assertThat(response.rows()).extracting(ItemSearchItemResponse::id).containsExactly(18, 1);
        assertThat(response.rows().get(0).tags()).containsExactly("문장제", "오답률높음");
        assertThat(response.rows().get(0).unit()).isEqualTo("M5-1");
        Pageable pageable = capturePageable();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Order.desc("level"), Sort.Order.asc("id")));
    }

    @Test
    @DisplayName("0건이면 message 는 \"검색 결과가 없습니다\"")
    void emptyResultMessage() {
        stubEmptyResult();

        ItemSearchResponse response = itemSearchService.search(none());

        assertThat(response.count()).isZero();
        assertThat(response.rows()).isEmpty();
        assertThat(response.message()).isEqualTo("검색 결과가 없습니다");
    }

    @Test
    @DisplayName("키워드: 100자는 경고 없음, 101자는 잘림 경고")
    void keywordLengthBoundary() {
        stubEmptyResult();

        assertThat(itemSearchService.search(condition("a".repeat(100), "", "", "", "", "", "1")).warnings())
            .isEmpty();
        assertThat(itemSearchService.search(condition("a".repeat(101), "", "", "", "", "", "1")).warnings())
            .containsExactly("키워드가 너무 길어 100자까지만 사용했습니다.");
    }

    @Test
    @DisplayName("키워드: 한 글자 · 와일드카드 경고가 레거시 순서로 나온다")
    void keywordOneCharAndWildcard() {
        stubEmptyResult();

        assertThat(itemSearchService.search(condition(" % ", "", "", "", "", "", "1")).warnings())
            .containsExactly("키워드가 한 글자라 결과가 많을 수 있습니다.", "키워드의 % 와 _ 는 와일드카드로 처리됩니다.");
    }

    @Test
    @DisplayName("단원: 형식은 맞지만 없는 코드는 '등록되지 않은 단원 코드' 경고만")
    void unknownUnitWarns() {
        stubEmptyResult();
        when(unitRepository.findByCode("Z9-9")).thenReturn(Optional.empty());

        assertThat(itemSearchService.search(condition("", "Z9-9", "", "", "", "", "1")).warnings())
            .containsExactly("등록되지 않은 단원 코드입니다: Z9-9");
    }

    @Test
    @DisplayName("단원: 200자 코드는 형식 경고 + 미등록 경고")
    void longUnitWarnsFormatAndUnknown() {
        stubEmptyResult();
        String longCode = "A".repeat(200);
        when(unitRepository.findByCode(longCode)).thenReturn(Optional.empty());

        assertThat(itemSearchService.search(condition("", longCode, "", "", "", "", "1")).warnings())
            .containsExactly("단원 코드 형식이 올바르지 않습니다. (예: M5-1)", "등록되지 않은 단원 코드입니다: " + longCode);
    }

    @Test
    @DisplayName("단원: 소문자 코드는 형식 검사는 통과하고 대문자 경고만(BR-12)")
    void lowercaseUnitWarnsUppercaseOnly() {
        stubEmptyResult();
        when(unitRepository.findByCode("m5-1")).thenReturn(Optional.of(unit));

        assertThat(itemSearchService.search(condition("", " m5-1 ", "", "", "", "", "1")).warnings())
            .containsExactly("단원 코드는 대문자로 입력하세요. (입력값 그대로 조회합니다)");
    }

    @Test
    @DisplayName("난이도: 범위 밖 값은 오류 없이 경고")
    void outOfRangeLevelWarns() {
        stubEmptyResult();

        assertThat(itemSearchService.search(condition("", "", "-1", "", "", "", "1")).warnings())
            .containsExactly("난이도는 1~5 사이여야 합니다.");
        assertThat(itemSearchService.search(condition("", "", "5", "", "", "", "1")).warnings()).isEmpty();
    }

    @Test
    @DisplayName("phpIntval: PHP 7 (int) 변환과 같게 앞부분 숫자만 읽는다")
    void phpIntvalMatchesPhp() {
        assertThat(ItemSearchService.phpIntval("-1")).isEqualTo(-1);
        assertThat(ItemSearchService.phpIntval("3abc")).isEqualTo(3);
        assertThat(ItemSearchService.phpIntval("abc")).isZero();
        assertThat(ItemSearchService.phpIntval("1e1")).isEqualTo(10);
        assertThat(ItemSearchService.phpIntval("2.9")).isEqualTo(2);
        assertThat(ItemSearchService.phpIntval("0x1A")).isZero();
        assertThat(ItemSearchService.phpIntval("99999999999999999999")).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("태그: 등록 여부는 대소문자까지 정확히 비교, % · 길이 경고")
    void tagWarnings() {
        stubEmptyResult();
        // 운영 DB 콜레이션처럼 대소문자를 무시하고 찾아 준다
        when(tagRepository.findByName(anyString())).thenAnswer(inv -> "calc".equalsIgnoreCase(inv.getArgument(0))
            ? Optional.of(ItemFixtures.tag(1, "Calc")) : Optional.empty());

        assertThat(itemSearchService.search(condition("", "", "", "Calc", "", "", "1")).warnings()).isEmpty();
        assertThat(itemSearchService.search(condition("", "", "", "calc", "", "", "1")).warnings())
            .containsExactly("등록되지 않은 태그입니다: calc");
        assertThat(itemSearchService.search(condition("", "", "", "%" + "x".repeat(50), "", "", "1")).warnings())
            .containsExactly("태그는 부분 일치를 지원하지 않습니다.", "태그 이름이 너무 길어 50자까지만 사용했습니다.",
                "등록되지 않은 태그입니다: %" + "x".repeat(49));
    }

    @Test
    @DisplayName("정렬: sort 없이 dir 만 있으면 경고 없이 기본 정렬")
    void dirWithoutSortUsesDefault() {
        stubEmptyResult();

        assertThat(itemSearchService.search(condition("", "", "", "", "", "asc", "1")).warnings()).isEmpty();
        assertThat(capturePageable().getSort())
            .isEqualTo(Sort.by(Sort.Order.desc("level"), Sort.Order.asc("id")));
    }

    @Test
    @DisplayName("정렬: 잘못된 방향 경고가 알 수 없는 기준 경고보다 먼저")
    void invalidDirAndSortWarnInLegacyOrder() {
        stubEmptyResult();

        assertThat(itemSearchService.search(condition("", "", "", "", "name", "up", "1")).warnings())
            .containsExactly("정렬 방향은 asc 또는 desc 만 가능합니다.", "알 수 없는 정렬 기준입니다. 기본 정렬을 사용합니다.");
    }

    @Test
    @DisplayName("정렬: unit desc 는 단원 DESC · 난이도 DESC · id ASC, 기준 · 방향은 대소문자 · 공백 무시")
    void sortKeysFollowLegacy() {
        stubEmptyResult();

        itemSearchService.search(condition("", "", "", "", " UNIT ", "DESC", "1"));
        assertThat(capturePageable().getSort()).isEqualTo(
            Sort.by(Sort.Order.desc("unit.code"), Sort.Order.desc("level"), Sort.Order.asc("id")));
    }

    @Test
    @DisplayName("정렬: id 는 2차 키 없이 단독, level 은 방향 없으면 DESC")
    void sortIdAloneAndLevelDefaultDesc() {
        stubEmptyResult();

        itemSearchService.search(condition("", "", "", "", "id", "desc", "1"));
        itemSearchService.search(condition("", "", "", "", "level", "", "1"));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(itemRepository, times(2))
            .findAll(ArgumentMatchers.<Specification<Item>>any(), captor.capture());
        assertThat(captor.getAllValues().get(0).getSort()).isEqualTo(Sort.by(Sort.Order.desc("id")));
        assertThat(captor.getAllValues().get(1).getSort())
            .isEqualTo(Sort.by(Sort.Order.desc("level"), Sort.Order.asc("id")));
    }

    @Test
    @DisplayName("페이지: 999 는 경고 없음, 1000 은 999 로 바꾸고 경고")
    void pageUpperBoundary() {
        stubEmptyResult();

        assertThat(itemSearchService.search(condition("", "", "", "", "", "", "999")).warnings()).isEmpty();
        assertThat(itemSearchService.search(condition("", "", "", "", "", "", "1000")).warnings())
            .containsExactly("페이지 번호는 999 를 넘을 수 없습니다.");
    }

    @Test
    @DisplayName("페이지: 숫자가 아니면 1페이지 + 경고, 0 은 경고 없이 1페이지")
    void pageInvalidAndZero() {
        stubEmptyResult();

        assertThat(itemSearchService.search(condition("", "", "", "", "", "", "abc")).warnings())
            .containsExactly("페이지 번호가 올바르지 않아 1페이지를 표시합니다.");
        assertThat(itemSearchService.search(condition("", "", "", "", "", "", "0")).warnings()).isEmpty();
        assertThat(itemSearchService.search(condition("", "", "", "", "", "", "")).warnings()).isEmpty();
    }
}
