package org.sirohi.smartnotebook.repository;

import org.sirohi.smartnotebook.dto.ChunkMatch;
import org.sirohi.smartnotebook.dto.ChunkWithEmbedding;
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

    /**
     * Find similar chunks with embeddings for MMR re-ranking.
     * Fetches more candidates than needed so MMR can select diverse subset.
     *
     * @param queryVector Query embedding
     * @param candidateCount Number of candidates to fetch (typically 2-3x topK)
     * @param docFilter Optional document filter
     * @return Chunks with embeddings for MMR processing
     */
    public List<ChunkWithEmbedding> findSimilarWithEmbeddings(float[] queryVector, int candidateCount, List<UUID> docFilter) {
        String vectorStr = arrayToVectorString(queryVector);

        String docFilterClause = (docFilter == null || docFilter.isEmpty())
                ? ""
                : "AND dc.document_id = ANY(?)";

        String sql = """
                SELECT dc.id, dc.document_id, dc.chunk_index, dc.content,
                       dc.metadata, dc.token_count, dc.embedding,
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
                    chunkWithEmbeddingRowMapper(),
                    vectorStr, vectorStr, candidateCount);
        } else {
            UUID[] docArray = docFilter.toArray(new UUID[0]);
            return jdbc.query(sql,
                    chunkWithEmbeddingRowMapper(),
                    vectorStr, docArray, vectorStr, candidateCount);
        }
    }

    private RowMapper<ChunkWithEmbedding> chunkWithEmbeddingRowMapper() {
        return (rs, rowNum) -> {
            // Parse pgvector format: [0.1,0.2,0.3,...]
            float[] embedding = parseVectorString(rs.getString("embedding"));

            return new ChunkWithEmbedding(
                    rs.getObject("id", UUID.class),
                    rs.getObject("document_id", UUID.class),
                    rs.getString("document_title"),
                    rs.getInt("chunk_index"),
                    rs.getString("content"),
                    rs.getDouble("similarity"),
                    rs.getInt("token_count"),
                    rs.getString("metadata"),
                    embedding);
        };
    }

    /**
     * Parse pgvector string format [0.1,0.2,...] to float array.
     */
    private float[] parseVectorString(String vectorStr) {
        if (vectorStr == null || vectorStr.isEmpty()) {
            return new float[0];
        }

        // Remove brackets
        String content = vectorStr.substring(1, vectorStr.length() - 1);
        if (content.isEmpty()) {
            return new float[0];
        }

        String[] parts = content.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }
}
