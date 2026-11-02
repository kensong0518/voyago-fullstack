package com.voyago.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaMigratorTest {

    @Test
    void splitsStatements_ignoringCommentsAndMultilineBodies() {
        String script = """
                -- 註解；裡面的分號不能被當成斷句
                ALTER TABLE a ADD COLUMN x INT;
                CREATE TABLE IF NOT EXISTS b (
                  id BIGINT NOT NULL,
                  name VARCHAR(10) DEFAULT 'a;b'
                ) ENGINE=InnoDB;

                INSERT IGNORE INTO b (id) VALUES
                (1),
                (2);
                """;
        List<String> s = SchemaMigrator.statements(script);
        assertEquals(3, s.size());
        assertEquals("ALTER TABLE a ADD COLUMN x INT", s.get(0));
        assertTrue(s.get(1).startsWith("CREATE TABLE IF NOT EXISTS b (") && s.get(1).contains("'a;b'"));
        assertTrue(s.get(2).endsWith("(2)"));
    }

    @Test
    void realMigrationFile_parses() throws Exception {
        String v3 = new String(getClass().getResourceAsStream("/db/migration/V3__reviews_coupons.sql").readAllBytes());
        List<String> s = SchemaMigrator.statements(v3);
        assertEquals(8, s.size());
        assertTrue(s.stream().noneMatch(x -> x.startsWith("--")));
    }
}
