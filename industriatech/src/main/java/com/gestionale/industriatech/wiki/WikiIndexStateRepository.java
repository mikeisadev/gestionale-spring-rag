package com.gestionale.industriatech.wiki;

import java.util.HashMap;
import java.util.Map;

import org.springframework.context.annotation.Conditional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;

/**
 * Lezione 11: ricorda l'impronta (hash) di ogni pagina già indicizzata,
 * per non rigenerare embedding inutili.
 *
 * Usa il JdbcTemplate @Primary di VectorStoreConfig, cioè PostgreSQL (configurazione 11B
 * con due database): la tabella sta accanto a vector_store.
 */
@Repository
@Conditional(WikiEnabledCondition.class)
public class WikiIndexStateRepository {

    private final JdbcTemplate jdbcTemplate;

    public WikiIndexStateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // La lezione originale crea la tabella a mano da pgAdmin: noi la creiamo all'avvio,
    // così funziona anche su un database nuovo (per esempio in Docker). IF NOT EXISTS: idempotente.
    @PostConstruct
    void createTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS wiki_index_state (
                    path         VARCHAR(512) PRIMARY KEY,
                    content_hash CHAR(64)     NOT NULL,
                    indexed_at   TIMESTAMP    NOT NULL
                )""");
    }

    /** Mappa percorso della pagina -> hash del contenuto indicizzato. */
    public Map<String, String> findAllHashes() {
        return jdbcTemplate.query("SELECT path, content_hash FROM wiki_index_state", resultSet -> {
            Map<String, String> hashes = new HashMap<>();
            while (resultSet.next()) {
                hashes.put(resultSet.getString("path"), resultSet.getString("content_hash"));
            }
            return hashes;
        });
    }

    public void save(String path, String contentHash) {
        // "Upsert" di PostgreSQL: inserisce, oppure aggiorna se la pagina c'è già
        jdbcTemplate.update("""
                INSERT INTO wiki_index_state (path, content_hash, indexed_at)
                VALUES (?, ?, now())
                ON CONFLICT (path) DO UPDATE
                SET content_hash = EXCLUDED.content_hash, indexed_at = now()
                """, path, contentHash);
    }

    public void delete(String path) {
        jdbcTemplate.update("DELETE FROM wiki_index_state WHERE path = ?", path);
    }

    /** Per la pagina di upload: quante pagine e quanti chunk della wiki sono in pgvector. */
    public int countPages() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM wiki_index_state", Integer.class);
        return count == null ? 0 : count;
    }

    public int countChunks() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM vector_store WHERE metadata->>'source_type' = 'wiki'", Integer.class);
        return count == null ? 0 : count;
    }
}
