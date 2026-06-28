package org.example.rag.index;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;

@Component
public class RagSchemaMigration {

    private static final Logger logger = LoggerFactory.getLogger(RagSchemaMigration.class);

    private final JdbcTemplate jdbcTemplate;

    public RagSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void migrate() {
        if (!isMysqlLikeDatabase()) {
            return;
        }
        alterQuietly("ALTER TABLE rag_chunk MODIFY COLUMN content MEDIUMTEXT");
        alterQuietly("ALTER TABLE rag_chunk MODIFY COLUMN embedding_content MEDIUMTEXT");
        alterQuietly("ALTER TABLE rag_chunk MODIFY COLUMN metadata_json MEDIUMTEXT");
    }

    private boolean isMysqlLikeDatabase() {
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            String productName = connection.getMetaData().getDatabaseProductName();
            String normalized = productName == null ? "" : productName.toLowerCase();
            return normalized.contains("mysql") || normalized.contains("mariadb");
        } catch (Exception e) {
            logger.warn("[RAG-OFFLINE] Skip RAG schema migration: {}", e.getMessage());
            return false;
        }
    }

    private void alterQuietly(String sql) {
        try {
            jdbcTemplate.execute(sql);
            logger.info("[RAG-OFFLINE] Applied schema migration: {}", sql);
        } catch (Exception e) {
            logger.warn("[RAG-OFFLINE] Schema migration skipped: {}, reason={}", sql, e.getMessage());
        }
    }
}
