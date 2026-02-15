package org.sirohi.smartnotebook.service;

import org.sirohi.smartnotebook.dto.ChunkMatch;
import org.sirohi.smartnotebook.dto.Citation;
import org.sirohi.smartnotebook.dto.QueryResponse;
import org.sirohi.smartnotebook.gateway.*;
import org.sirohi.smartnotebook.model.ChatMessage;
import org.sirohi.smartnotebook.repository.QueryLogRepository;
import org.sirohi.smartnotebook.repository.VectorSearchRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * RAG query orchestration pipeline.
 * Embed query → vector search → build context → LLM completion → citations.
 */
@Service
public class QueryService {

        private final ModelGateway modelGateway;
        private final VectorSearchRepository vectorSearch;
        private final QueryLogRepository queryLog;

        private static final String RAG_SYSTEM_PROMPT = """
                        You are a knowledge assistant. Answer the user's question based ONLY on
                        the provided context documents. Follow these rules strictly:

                        1. If the context contains the answer, provide it with specific citations.
                        2. If the context does NOT contain enough information, say:
                           "I don't have enough information in the indexed documents to answer this."
                        3. Never make up information not present in the context.
                        4. For each claim, cite the source document title and chunk.
                        5. If multiple documents are relevant, synthesize across them.

                        Format citations as [Document: "title", Section: chunk_index].
                        """;

        public QueryService(ModelGateway modelGateway,
                        VectorSearchRepository vectorSearch,
                        QueryLogRepository queryLog) {
                this.modelGateway = modelGateway;
                this.vectorSearch = vectorSearch;
                this.queryLog = queryLog;
        }

        public QueryResponse query(String question, int topK, List<UUID> documentIds) {
                long startTime = System.currentTimeMillis();

                // 1. Embed the question
                EmbeddingResponse questionEmbedding = modelGateway.embed(
                                new EmbeddingRequest(question));

                // 2. Vector similarity search
                List<ChunkMatch> matches = vectorSearch.findSimilar(
                                questionEmbedding.vector(),
                                topK,
                                documentIds);

                // 3. Build RAG context
                String context = buildContext(matches);

                // 4. Generate grounded answer
                CompletionResponse completion = modelGateway.complete(
                                new CompletionRequest(
                                                RAG_SYSTEM_PROMPT,
                                                buildUserPrompt(question, context)));

                // 5. Extract citations
                List<Citation> citations = matches.stream()
                                .map(m -> new Citation(
                                                m.documentTitle(),
                                                m.chunkIndex(),
                                                m.content(),
                                                m.similarity()))
                                .toList();

                long latencyMs = System.currentTimeMillis() - startTime;

                // 6. Log query (fire-and-forget)
                queryLog.logAsync(question, completion.text(), matches, latencyMs);

                return new QueryResponse(
                                completion.text(),
                                citations,
                                computeConfidence(matches),
                                latencyMs);
        }

        /**
         * Conversation-aware RAG query. Includes chat history in the prompt
         * so the LLM can handle follow-up questions.
         */
        public QueryResponse queryWithHistory(String question, int topK,
                        List<UUID> documentIds,
                        List<ChatMessage> conversationHistory) {
                long startTime = System.currentTimeMillis();

                // 1. Embed the question
                EmbeddingResponse questionEmbedding = modelGateway.embed(
                                new EmbeddingRequest(question));

                // 2. Vector similarity search
                List<ChunkMatch> matches = vectorSearch.findSimilar(
                                questionEmbedding.vector(),
                                topK,
                                documentIds);

                // 3. Build RAG context
                String context = buildContext(matches);

                // 4. Build conversation history string
                String historyStr = buildConversationHistory(conversationHistory);

                // 5. Generate grounded answer with conversation context
                CompletionResponse completion = modelGateway.complete(
                                new CompletionRequest(
                                                RAG_SYSTEM_PROMPT,
                                                buildConversationalPrompt(question, context, historyStr)));

                // 6. Extract citations
                List<Citation> citations = matches.stream()
                                .map(m -> new Citation(
                                                m.documentTitle(),
                                                m.chunkIndex(),
                                                m.content(),
                                                m.similarity()))
                                .toList();

                long latencyMs = System.currentTimeMillis() - startTime;

                // 7. Log query
                queryLog.logAsync(question, completion.text(), matches, latencyMs);

                return new QueryResponse(
                                completion.text(),
                                citations,
                                computeConfidence(matches),
                                latencyMs);
        }

        private String buildContext(List<ChunkMatch> matches) {
                StringBuilder sb = new StringBuilder();
                for (ChunkMatch m : matches) {
                        sb.append("--- Document: \"%s\" | Chunk %d (similarity: %.3f) ---\n"
                                        .formatted(m.documentTitle(), m.chunkIndex(), m.similarity()));
                        sb.append(m.content());
                        sb.append("\n\n");
                }
                return sb.toString();
        }

        private String buildUserPrompt(String question, String context) {
                return """
                                Context documents:
                                %s

                                Question: %s

                                Provide a comprehensive answer with citations:
                                """.formatted(context, question);
        }

        private String buildConversationHistory(List<ChatMessage> history) {
                if (history == null || history.isEmpty())
                        return "";
                StringBuilder sb = new StringBuilder();
                for (ChatMessage msg : history) {
                        sb.append(msg.getRole().equals("user") ? "User: " : "Assistant: ");
                        sb.append(msg.getContent());
                        sb.append("\n");
                }
                return sb.toString();
        }

        private String buildConversationalPrompt(String question, String context, String history) {
                if (history.isEmpty()) {
                        return buildUserPrompt(question, context);
                }
                return """
                                Context documents:
                                %s

                                Conversation history:
                                %s

                                Current question: %s

                                Provide a comprehensive answer with citations. Use the conversation history
                                to understand any follow-up questions or references to previous answers:
                                """.formatted(context, history, question);
        }

        private double computeConfidence(List<ChunkMatch> matches) {
                if (matches.isEmpty())
                        return 0.0;
                return matches.stream()
                                .mapToDouble(ChunkMatch::similarity)
                                .average()
                                .orElse(0.0);
        }
}
