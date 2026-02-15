package org.sirohi.smartnotebook.repository;

import org.sirohi.smartnotebook.dto.ChunkMatch;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * pgvector similarity search repository.
 * Uses raw JdbcTemplate for vector operations not supported by JPA.
 */
@Repository
public class VectorSearchRepository {

    private final JdbcTemplate jdbc;

    public VectorSearchRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ChunkMatch> findSimilar(float[] queryVector, int topK, List<UUID> docFilter) {
        String vectorStr = arrayToVectorString(queryVector);

        String docFilterClause = (docFilter == null || docFilter.isEmpty())
                ? ""
                : "AND dc.document_id = ANY(?)";

        String sql = """
                SELECT dc.id, dc.document_id, dc.chunk_index, dc.content,
                       dc.metadata, dc.token_count,
                       d.title AS document_title,
                       1 - (dc.embedding <=> ?::vector) AS similarity
                FROM document_chunks dc
                JOIN documents d ON dc.document_id = d.id
                WHERE dc.embedding IS NOT NULL
                %s
                ORDER BY dc.embedding <=> ?::vector
                LIMIT ?
                """.formatted(docFilterClause);

        if (docFilter == null || docFilter.isEmpty()) {
            return jdbc.query(sql,
                    chunkMatchRowMapper(),
                    vectorStr, vectorStr, topK);
        } else {
            UUID[] docArray = docFilter.toArray(new UUID[0]);
            return jdbc.query(sql,
                    chunkMatchRowMapper(),
                    vectorStr, docArray, vectorStr, topK);
        }
    }

    private String arrayToVectorString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0)
                sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private RowMapper<ChunkMatch> chunkMatchRowMapper() {
        return (rs, rowNum) -> new ChunkMatch(
                rs.getObject("id", UUID.class),
                rs.getObject("document_id", UUID.class),
                rs.getString("document_title"),
                rs.getInt("chunk_index"),
                rs.getString("content"),
                rs.getDouble("similarity"),
                rs.getInt("token_count"),
                rs.getString("metadata"));
    }
}
