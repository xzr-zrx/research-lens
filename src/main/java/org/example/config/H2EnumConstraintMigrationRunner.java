package org.example.config;

import org.example.enums.TaskStage;
import org.example.enums.TaskStatus;
import org.example.enums.TaskType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Repairs enum CHECK constraints created by older Hibernate schema versions.
 *
 * <p>Hibernate's ddl-auto=update adds new enum constants to Java code, but H2 does not
 * automatically widen the existing CHECK constraint. As a result, inserting a newly
 * introduced task type or stage fails with a check-constraint violation. This runner
 * recreates the affected constraints from the current enum definitions while keeping
 * all existing user data.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class H2EnumConstraintMigrationRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(H2EnumConstraintMigrationRunner.class);
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9_]+");

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    public H2EnumConstraintMigrationRunner(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!isH2()) {
            return;
        }

        migrateEnumConstraint("ANALYSIS_TASK", "TASK_TYPE",
                Arrays.stream(TaskType.values()).map(Enum::name).toList());
        migrateEnumConstraint("ANALYSIS_TASK", "STAGE",
                Arrays.stream(TaskStage.values()).map(Enum::name).toList());
        migrateEnumConstraint("ANALYSIS_TASK", "STATUS",
                Arrays.stream(TaskStatus.values()).map(Enum::name).toList());
    }

    private boolean isH2() {
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            return product != null && product.toUpperCase(Locale.ROOT).contains("H2");
        } catch (Exception ex) {
            log.warn("无法识别数据库类型，跳过 H2 枚举约束兼容迁移", ex);
            return false;
        }
    }

    private void migrateEnumConstraint(String tableName, String columnName, List<String> values) {
        requireSafeIdentifier(tableName);
        requireSafeIdentifier(columnName);

        Integer columnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE UPPER(TABLE_NAME) = ? AND UPPER(COLUMN_NAME) = ?
                """, Integer.class, tableName.toUpperCase(Locale.ROOT), columnName.toUpperCase(Locale.ROOT));
        if (columnCount == null || columnCount == 0) {
            return;
        }

        List<Map<String, Object>> constraints = jdbcTemplate.queryForList("""
                SELECT tc.CONSTRAINT_NAME, cc.CHECK_CLAUSE
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
                JOIN INFORMATION_SCHEMA.CHECK_CONSTRAINTS cc
                  ON tc.CONSTRAINT_CATALOG = cc.CONSTRAINT_CATALOG
                 AND tc.CONSTRAINT_SCHEMA = cc.CONSTRAINT_SCHEMA
                 AND tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME
                WHERE UPPER(tc.TABLE_NAME) = ?
                  AND tc.CONSTRAINT_TYPE = 'CHECK'
                """, tableName.toUpperCase(Locale.ROOT));

        for (Map<String, Object> row : constraints) {
            String constraintName = String.valueOf(row.get("CONSTRAINT_NAME"));
            String clause = String.valueOf(row.get("CHECK_CLAUSE"));
            if (clause.toUpperCase(Locale.ROOT).contains(columnName.toUpperCase(Locale.ROOT))) {
                dropConstraint(tableName, constraintName);
            }
        }

        String stableConstraintName = "CK_" + tableName + "_" + columnName;
        jdbcTemplate.execute("ALTER TABLE " + tableName + " DROP CONSTRAINT IF EXISTS " + stableConstraintName);

        String allowedValues = values.stream()
                .map(value -> "'" + value.replace("'", "''") + "'")
                .reduce((left, right) -> left + "," + right)
                .orElseThrow(() -> new IllegalArgumentException("枚举值不能为空"));

        jdbcTemplate.execute("ALTER TABLE " + tableName
                + " ADD CONSTRAINT " + stableConstraintName
                + " CHECK (" + columnName + " IN (" + allowedValues + "))");
        log.info("已更新 H2 枚举约束 {}.{}，允许值：{}", tableName, columnName, values);
    }

    private void dropConstraint(String tableName, String constraintName) {
        jdbcTemplate.execute("ALTER TABLE " + tableName + " DROP CONSTRAINT " + quoteIdentifier(constraintName));
        log.info("已删除旧 H2 CHECK 约束 {}.{}", tableName, constraintName);
    }

    private String quoteIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("数据库约束名不能为空");
        }
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private void requireSafeIdentifier(String identifier) {
        if (identifier == null || !SAFE_IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException("非法数据库标识符: " + identifier);
        }
    }
}
