package com.example.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

/** 검색 조건 쿼리 — H2(MariaDB 모드)에 실제 SQL 을 날린다. */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ItemSearchSpecsTest {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("level"), Sort.Order.asc("id"));

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ItemRepository itemRepository;

    @BeforeEach
    void seed() {
        Unit fraction = entityManager.persist(ItemFixtures.unit(1, "M5-1", "분수의 덧셈과 뺄셈", 5));
        Unit decimal = entityManager.persist(ItemFixtures.unit(3, "M5-3", "소수의 곱셈", 5));
        Tag calc = entityManager.persist(ItemFixtures.tag(1, "계산"));
        Tag word = entityManager.persist(ItemFixtures.tag(2, "문장제"));

        entityManager.persist(ItemFixtures.item(null, fraction, "분모가 같은 분수의 덧셈", 2, ItemStatus.ACTIVE, calc));
        entityManager.persist(ItemFixtures.item(null, fraction, "분수 문장제", 4, ItemStatus.ACTIVE, calc, word));
        entityManager.persist(ItemFixtures.item(null, fraction, "검수 중 문항", 3, ItemStatus.REVIEWING));
        entityManager.persist(ItemFixtures.item(null, fraction, "난이도 5 분수", 5, ItemStatus.ACTIVE, word));
        entityManager.persist(ItemFixtures.item(null, decimal, "소수 곱셈", 1, ItemStatus.ACTIVE));
        entityManager.flush();
        entityManager.clear();
    }

    private List<String> titles(Specification<Item> spec, Sort sort) {
        return itemRepository.findAll(spec, PageRequest.of(0, ItemSearchService.PAGE_SIZE, sort)).stream()
            .map(Item::getTitle)
            .toList();
    }

    @Test
    @DisplayName("기본 조건: 공개 문항 중 난이도 5 는 빠진다(BR-14), level DESC · id ASC")
    void defaultExcludesLevelFive() {
        Specification<Item> spec = ItemSearchSpecs.isPublic().and(ItemSearchSpecs.levelBelow(5));

        assertThat(titles(spec, DEFAULT_SORT)).containsExactly("분수 문장제", "분모가 같은 분수의 덧셈", "소수 곱셈");
    }

    @Test
    @DisplayName("키워드: 제목 또는 지문에 포함")
    void keywordMatchesTitleOrStem() {
        Specification<Item> spec = ItemSearchSpecs.isPublic().and(ItemSearchSpecs.keywordContains("문제 본문"));

        assertThat(titles(spec, DEFAULT_SORT)).hasSize(4);
        assertThat(titles(ItemSearchSpecs.isPublic().and(ItemSearchSpecs.keywordContains("소수")), DEFAULT_SORT))
            .containsExactly("소수 곱셈");
    }

    @Test
    @DisplayName("단원 · 난이도 AND, 없는 단원은 0건")
    void unitAndLevel() {
        Specification<Item> spec = ItemSearchSpecs.isPublic()
            .and(ItemSearchSpecs.unitCodeEquals("M5-1"))
            .and(ItemSearchSpecs.levelEquals(4));

        assertThat(titles(spec, DEFAULT_SORT)).containsExactly("분수 문장제");
        assertThat(titles(ItemSearchSpecs.isPublic().and(ItemSearchSpecs.unitCodeEquals("Z9-9")), DEFAULT_SORT))
            .isEmpty();
    }

    @Test
    @DisplayName("태그: 이름이 같은 태그가 붙은 문항만, 중복 행 없음")
    void tagExists() {
        Specification<Item> spec = ItemSearchSpecs.isPublic().and(ItemSearchSpecs.hasTagNamed("문장제"));

        assertThat(titles(spec, DEFAULT_SORT)).containsExactly("난이도 5 분수", "분수 문장제");
    }

    @Test
    @DisplayName("정렬 unit DESC: 단원 코드 DESC 다음 난이도 DESC")
    void sortByUnitCode() {
        Sort sort = Sort.by(Sort.Order.desc("unit.code"), Sort.Order.desc("level"), Sort.Order.asc("id"));

        assertThat(titles(ItemSearchSpecs.isPublic(), sort))
            .containsExactly("소수 곱셈", "난이도 5 분수", "분수 문장제", "분모가 같은 분수의 덧셈");
    }

    @Test
    @DisplayName("전체 건수는 페이지 밖이어도 유지된다(page=999 일 때 count 는 전체 건수)")
    void totalCountBeyondLastPage() {
        Page<Item> page = itemRepository.findAll(ItemSearchSpecs.isPublic(),
            PageRequest.of(998, ItemSearchService.PAGE_SIZE, DEFAULT_SORT));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(4);
    }

    @Test
    @DisplayName("findAllByIdIn: 단원 · 태그가 초기화된 채로 온다")
    void findAllByIdInLoadsDetails() {
        List<Integer> ids = itemRepository.findAll(ItemSearchSpecs.isPublic()).stream().map(Item::getId).toList();
        entityManager.clear();

        List<Item> items = itemRepository.findAllByIdIn(ids);

        assertThat(items).hasSize(4);
        assertThat(items).allSatisfy(item -> {
            assertThat(Hibernate.isInitialized(item.getUnit())).isTrue();
            assertThat(Hibernate.isInitialized(item.getTags())).isTrue();
        });
    }
}
