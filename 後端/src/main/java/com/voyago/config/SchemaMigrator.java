package com.voyago.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 啟動時自動升級資料庫結構（正式環境 ddl-auto=none，不讓 Hibernate 自己改表）。
 *
 * 規則跟 Flyway 一樣：classpath:db/migration/V{版本}__{說明}.sql 依版本號執行，
 * 執行過的版本記在 schema_migration 表，不會重跑。沒用 Flyway 是因為 TiDB 需要 Flyway 10 的獨立模組，
 * 跟目前的 Spring Boot 版本搭配不保證相容；這裡只需要「依序執行、記錄版本」這兩件事。
 *
 * MySQL／TiDB 的 DDL 會自動 commit、無法整批回滾，所以每句 SQL 都寫成可重跑：
 * 「欄位／索引／資料表已存在」的錯誤視為已完成（例如上次執行到一半中斷），其他錯誤就停止啟動。
 * 測試用 H2 由 Hibernate 建表，設定 app.db.migrate=false 關閉。
 */
@Component
public class SchemaMigrator implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigrator.class);
    private static final Pattern FILE = Pattern.compile("V(\\d+)__(.+)\\.sql");
    /** MySQL 錯誤碼：1050 資料表已存在、1060 欄位重複、1061 索引名稱重複、1826 外鍵名稱重複。 */
    private static final Set<Integer> ALREADY_DONE = Set.of(1050, 1060, 1061, 1826);

    private final JdbcTemplate jdbc;
    private final boolean enabled;

    public SchemaMigrator(JdbcTemplate jdbc, @Value("${app.db.migrate:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.enabled = enabled;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (!enabled) return;
        jdbc.execute("CREATE TABLE IF NOT EXISTS schema_migration (" +
                "version INT NOT NULL PRIMARY KEY, description VARCHAR(200) NOT NULL, " +
                "applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        List<Integer> applied = jdbc.queryForList("SELECT version FROM schema_migration", Integer.class);

        Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath:db/migration/V*__*.sql");
        Arrays.sort(files, Comparator.comparingInt(SchemaMigrator::version));

        // 全新的空資料庫：結構會由 01_schema.sql（或本機的 Hibernate ddl-auto=update）直接建成最新版，
        // 不需要升級腳本，全部記為已套用即可（等同 Flyway 的 baseline）
        Integer tables = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables " +
                "WHERE table_schema = DATABASE() AND table_name = 'route'", Integer.class);
        if (tables != null && tables == 0) {
            for (Resource f : files) {
                if (!applied.contains(version(f))) {
                    jdbc.update("INSERT INTO schema_migration (version, description) VALUES (?, ?)", version(f), description(f));
                }
            }
            log.info("資料庫升級：全新資料庫，記錄基準版本，不執行升級腳本");
            return;
        }
        for (Resource f : files) {
            int v = version(f);
            if (applied.contains(v)) continue;
            String desc = description(f);
            log.info("資料庫升級：執行 V{} {}", v, desc);
            for (String sql : statements(new String(f.getInputStream().readAllBytes(), StandardCharsets.UTF_8))) {
                try {
                    jdbc.execute(sql);
                } catch (RuntimeException e) {
                    Integer code = mysqlErrorCode(e);
                    if (code != null && ALREADY_DONE.contains(code)) {
                        log.info("  已存在，略過：{}", firstLine(sql));
                    } else {
                        throw new IllegalStateException("資料庫升級 V" + v + " 失敗：" + firstLine(sql), e);
                    }
                }
            }
            jdbc.update("INSERT INTO schema_migration (version, description) VALUES (?, ?)", v, desc);
            log.info("資料庫升級：V{} 完成", v);
        }
    }

    /** 以「行尾分號」切句；先去掉整行註解（-- 開頭）。 */
    static List<String> statements(String script) {
        String noComments = Arrays.stream(script.split("\\R"))
                .filter(l -> !l.trim().startsWith("--"))
                .reduce("", (a, b) -> a + b + "\n");
        return Arrays.stream(noComments.split(";\\s*\\n"))
                .map(String::trim).map(s -> s.endsWith(";") ? s.substring(0, s.length() - 1) : s)
                .filter(s -> !s.isBlank()).toList();
    }

    private static Integer mysqlErrorCode(Throwable e) {
        for (Throwable c = e; c != null; c = c.getCause()) {
            if (c instanceof SQLException s && s.getErrorCode() != 0) return s.getErrorCode();
        }
        return null;
    }

    private static int version(Resource r) {
        Matcher m = FILE.matcher(r.getFilename() == null ? "" : r.getFilename());
        if (!m.matches()) throw new IllegalStateException("migration 檔名格式錯誤：" + r.getFilename());
        return Integer.parseInt(m.group(1));
    }

    private static String description(Resource r) {
        Matcher m = FILE.matcher(r.getFilename());
        m.matches();
        return m.group(2).replace('_', ' ');
    }

    private static String firstLine(String sql) {
        String l = sql.lines().findFirst().orElse("");
        return l.length() > 100 ? l.substring(0, 100) + "…" : l;
    }
}
