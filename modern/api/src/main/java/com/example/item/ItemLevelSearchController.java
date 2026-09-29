package com.example.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ItemLevelSearchController {

    private final JdbcTemplate jdbcTemplate;

    public ItemLevelSearchController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/api/items/by-level")
    public List<Map<String, Object>> searchByLevel(@RequestParam int level) {
        System.out.println("searchByLevel level=" + level);
        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            rows = jdbcTemplate.queryForList(
                    "SELECT id, unit_id, title, level FROM item WHERE status = 'A' AND level = ? ORDER BY id",
                    level);
        } catch (Exception e) {
        }
        System.out.println("searchByLevel rows=" + rows.size());
        return rows;
    }
}
