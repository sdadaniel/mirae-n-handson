package com.example.item;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

/** 문항 검색 조건. 레거시 {@code v_item_public} 뷰에 붙던 WHERE 조각과 하나씩 대응한다. */
final class ItemSearchSpecs {

    private ItemSearchSpecs() {
    }

    /** BR-01: 공개 문항만(01-schema.sql:70). */
    static Specification<Item> isPublic() {
        return (root, query, cb) -> cb.equal(root.get("status"), ItemStatus.ACTIVE);
    }

    /** BR-08 · BR-09: 제목 또는 지문 LIKE. {@code %} · {@code _} 는 이스케이프하지 않는다(search.php:97-98). */
    static Specification<Item> keywordContains(String keyword) {
        String like = "%" + keyword + "%";
        return (root, query, cb) -> cb.or(cb.like(root.get("title"), like), cb.like(root.get("stem"), like));
    }

    /** BR-11: 단원 코드 일치(search.php:118). */
    static Specification<Item> unitCodeEquals(String unitCode) {
        return (root, query, cb) -> cb.equal(root.get("unit").get("code"), unitCode);
    }

    /** BR-14: 난이도 미입력이면 {@code level < bound}(search.php:215). */
    static Specification<Item> levelBelow(int bound) {
        return (root, query, cb) -> cb.lessThan(root.get("level"), bound);
    }

    /** BR-15 · BR-16: 난이도 일치(search.php:218, :226). */
    static Specification<Item> levelEquals(int level) {
        return (root, query, cb) -> cb.equal(root.get("level"), level);
    }

    /** BR-17: 이름이 정확히 같은 태그가 붙은 문항(search.php:244-245). */
    static Specification<Item> hasTagNamed(String tagName) {
        return (root, query, cb) -> {
            Subquery<Integer> sub = query.subquery(Integer.class);
            Root<Item> tagged = sub.from(Item.class);
            Join<Item, Tag> tag = tagged.join("tags");
            sub.select(tagged.get("id"))
                .where(cb.equal(tagged.get("id"), root.get("id")), cb.equal(tag.get("name"), tagName));
            return cb.exists(sub);
        };
    }
}
