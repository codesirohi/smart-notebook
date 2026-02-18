package org.sirohi.smartnotebook.service;

import org.sirohi.smartnotebook.config.ModelConfig;
import org.sirohi.smartnotebook.dto.ChunkMatch;
import org.sirohi.smartnotebook.dto.Citation;
import org.sirohi.smartnotebook.dto.QueryResponse;
import org.sirohi.smartnotebook.gateway.*;
import org.sirohi.smartnotebook.logging.StructuredLogger;
import org.sirohi.smartnotebook.model.ChatMessage;
import org.sirohi.smartnotebook.repository.QueryLogRepository;
import org.sirohi.smartnotebook.repository.VectorSearchRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * RAG query orchestration pipeline.
 * Embed query → vector search → build context → LLM completion → citations.
 */
@Service
public class QueryService {

        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(QueryService.class);

        private final GatewayFactory gatewayFactory;
        private final ModelConfig modelConfig;
        private final VectorSearchRepository vectorSearch;
        private final QueryLogRepository queryLog;
        private final long latencyBudgetMs;

        /**
         * RAG system prompt with few-shot examples.
         *
         * Interview Note: Few-shot prompting improves LLM consistency by showing examples
         * of the desired output format. This is especially important for:
         * 1. Citation format consistency
         * 2. Handling "I don't know" cases gracefully
         * 3. Preventing hallucination (only cite actual context)
         * 4. Proper grounding behavior (answer only from context)
         *
         * The examples demonstrate:
         * - How to cite specific documents and chunks
         * - How to synthesize information from multiple sources
         * - How to handle missing information gracefully
         */
        private static final String RAG_SYSTEM_PROMPT = """
                        You are a knowledge assistant. Answer the user's question based ONLY on
                        the provided context documents.

                        Rules:
                        1. Direct Answer: Start your answer immediately. Do NOT use "Assistant:" or "User:" prefixes.
                        2. No History: Do NOT repeat the conversation history or the question.
                        3. Context Only: If the context contains the answer, provide it using the information.
                        4. If the context does NOT contain enough information, say:
                           "I don't have enough information in the indexed documents to answer this."
                        5. Never make up information not present in the context.

                        EXAMPLES:

                        Example 1 - Good answer with single source:
                        Context: "Machine learning is a subset of artificial intelligence that enables computers to learn from data without being explicitly programmed."
                        Question: What is machine learning?
                        Answer: Machine learning is a subset of artificial intelligence that enables computers to learn from data without being explicitly programmed. This allows systems to improve their performance on tasks through experience rather than following hard-coded rules.

                        Example 2 - Synthesizing multiple sources:
                        Context:
                        [Doc A] "Python uses dynamic typing, making it flexible for rapid development."
                        [Doc B] "Python's extensive libraries make it popular for data science and machine learning."
                        Question: Why is Python popular?
                        Answer: Python is popular for several reasons. First, it uses dynamic typing which makes it flexible for rapid development. Additionally, its extensive libraries make it particularly well-suited for data science and machine learning applications.

                        Example 3 - Handling missing information:
                        Context: "The solar system has 8 planets. Mercury is the closest to the sun."
                        Question: How many moons does Jupiter have?
                        Answer: I don't have enough information in the indexed documents to answer this.

                        Now answer the user's question using ONLY the provided context:
                        """;

        public QueryService(GatewayFactory gatewayFactory,
                        ModelConfig modelConfig,
                        VectorSearchRepository vectorSearch,
                        QueryLogRepository queryLog,
                        @Value("${app.query.latency-budget-ms:15000}") long latencyBudgetMs) {
                this.gatewayFactory = gatewayFactory;
                this.modelConfig = modelConfig;
                this.vectorSearch = vectorSearch;
                this.queryLog = queryLog;
                this.latencyBudgetMs = latencyBudgetMs;
        }

        public QueryResponse query(String question, int topK, List<UUID> documentIds) {
                long startTime = System.currentTimeMillis();

                // Interview Note: Structured logging provides machine-parseable logs for monitoring
                StructuredLogger.info(log, "query_started")
                        .field("query_type", "standard")
                        .field("question_length", question.length())
                        .field("top_k", topK)
                        .field("document_count", documentIds != null ? documentIds.size() : 0)
                        .log();

                // 1. Embed the question using default embedding model
                String embeddingModel = modelConfig.getDefaultEmbeddingModel();
                ModelGateway embeddingGateway = gatewayFactory.getGatewayForModel(embeddingModel);

                StructuredLogger.debug(log, "embedding_question")
                        .field("model", embeddingModel)
                        .log();

                EmbeddingResponse questionEmbedding = embeddingGateway.embed(
                                new EmbeddingRequest(question, embeddingModel));

                // 2. Vector similarity search
                StructuredLogger.debug(log, "vector_search_started")
                        .field("top_k", topK)
                        .field("embedding_dimensions", questionEmbedding.dimensions())
                        .log();

                long searchStart = System.currentTimeMillis();
                List<ChunkMatch> matches = vectorSearch.findSimilar(
                                questionEmbedding.vector(),
                                topK,
                                documentIds);
                long searchLatencyMs = System.currentTimeMillis() - searchStart;

                StructuredLogger.info(log, "vector_search_completed")
                        .field("matches_found", matches.size())
                        .field("search_latency_ms", searchLatencyMs)
                        .log();

                // 3. Build RAG context
                String context = buildContext(matches);

                // 4. Generate grounded answer using default extraction/chat model if not
                // specified
                String chatModel = modelConfig.getDefaultExtractionModel();
                ModelGateway chatGateway = gatewayFactory.getGatewayForModel(chatModel);

                StructuredLogger.debug(log, "completion_started")
                        .field("model", chatModel)
                        .field("context_length", context.length())
                        .log();

                CompletionResponse completion = chatGateway.complete(
                                new CompletionRequest(
                                                RAG_SYSTEM_PROMPT,
                                                buildUserPrompt(question, context),
                                                chatModel,
                                                defaultCompletionParams()));

                // 5. Extract citations
                List<Citation> citations = matches.stream()
                                .map(m -> new Citation(
                                                m.documentTitle(),
                                                m.chunkIndex(),
                                                m.content(),
                                                m.similarity()))
                                .toList();

                long latencyMs = System.currentTimeMillis() - startTime;

                // Interview Note: Structured performance logging enables dashboards and alerts
                StructuredLogger.info(log, "query_completed")
                        .field("query_type", "standard")
                        .field("total_latency_ms", latencyMs)
                        .field("answer_length", completion.text().length())
                        .field("citations_count", citations.size())
                        .log();

                logQueryPerformance("standard", latencyMs, questionEmbedding.latencyMs(), searchLatencyMs,
                                completion.latencyMs(), matches.size(), chatModel);

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
                        List<ChatMessage> conversationHistory,
                        String model) {
                long startTime = System.currentTimeMillis();

                StructuredLogger.info(log, "query_started")
                        .field("query_type", "with_history")
                        .field("question_length", question.length())
                        .field("history_size", conversationHistory.size())
                        .field("top_k", topK)
                        .field("requested_model", model)
                        .log();

                // 1. Embed the question
                String embeddingModel = modelConfig.getDefaultEmbeddingModel();
                ModelGateway embeddingGateway = gatewayFactory.getGatewayForModel(embeddingModel);

                StructuredLogger.debug(log, "embedding_question")
                        .field("model", embeddingModel)
                        .log();

                EmbeddingResponse questionEmbedding = embeddingGateway.embed(
                                new EmbeddingRequest(question, embeddingModel));

                // 2. Vector similarity search
                long searchStart = System.currentTimeMillis();
                List<ChunkMatch> matches = vectorSearch.findSimilar(
                                questionEmbedding.vector(),
                                topK,
                                documentIds);
                long searchLatencyMs = System.currentTimeMillis() - searchStart;

                StructuredLogger.info(log, "vector_search_completed")
                        .field("matches_found", matches.size())
                        .field("search_latency_ms", searchLatencyMs)
                        .log();

                // 3. Build RAG context
                String context = buildContext(matches);

                // 4. Build conversation history string
                String historyStr = buildConversationHistory(conversationHistory);

                // 5. Generate grounded answer with conversation context
                // Fallback to default model if null
                String targetModel = model != null ? model : modelConfig.getDefaultExtractionModel();
                ModelGateway chatGateway = gatewayFactory.getGatewayForModel(targetModel);

                StructuredLogger.debug(log, "completion_started")
                        .field("model", targetModel)
                        .field("context_length", context.length())
                        .field("history_length", historyStr.length())
                        .log();

                CompletionResponse completion = chatGateway.complete(
                                new CompletionRequest(
                                                RAG_SYSTEM_PROMPT,
                                                buildConversationalPrompt(question, context, historyStr),
                                                targetModel,
                                                defaultCompletionParams()));

                StructuredLogger.debug(log, "completion_received")
                        .field("answer_length", completion.text().length())
                        .log();

                // 6. Extract citations
                List<Citation> citations = matches.stream()
                                .map(m -> new Citation(
                                                m.documentTitle(),
                                                m.chunkIndex(),
                                                m.content(),
                                                m.similarity()))
                                .toList();

                long latencyMs = System.currentTimeMillis() - startTime;

                StructuredLogger.info(log, "query_completed")
                        .field("query_type", "with_history")
                        .field("total_latency_ms", latencyMs)
                        .field("answer_length", completion.text().length())
                        .field("citations_count", citations.size())
                        .field("model", targetModel)
                        .log();

                logQueryPerformance("history", latencyMs, questionEmbedding.latencyMs(), searchLatencyMs,
                                completion.latencyMs(), matches.size(), targetModel);

                // 7. Log query
                queryLog.logAsync(question, completion.text(), matches, latencyMs);

                return new QueryResponse(
                                completion.text(),
                                citations,
                                computeConfidence(matches),
                                latencyMs);
        }

        /**
         * Context holder for streaming queries. Contains pre-computed citations
         * and the token stream — ChatService uses this to build SSE events.
         */
        public record StreamingQueryContext(
                        Flux<String> tokenStream,
                        List<Citation> citations,
                        double confidence) {
        }

        /**
         * Streaming RAG query with conversation history.
         * Embedding + vector search run synchronously (fast), then the LLM
         * completion streams token-by-token via the returned Flux.
         */
        public StreamingQueryContext queryStreaming(String question, int topK,
                        List<UUID> documentIds,
                        List<ChatMessage> conversationHistory,
                        String model) {

                // 1. Embed the question (synchronous, ~100ms)
                String embeddingModel = modelConfig.getDefaultEmbeddingModel();
                ModelGateway embeddingGateway = gatewayFactory.getGatewayForModel(embeddingModel);

                EmbeddingResponse questionEmbedding = embeddingGateway.embed(
                                new EmbeddingRequest(question, embeddingModel));

                // 2. Vector similarity search (synchronous, ~50ms)
                List<ChunkMatch> matches = vectorSearch.findSimilar(
                                questionEmbedding.vector(),
                                topK,
                                documentIds);

                // 3. Build RAG context
                String context = buildContext(matches);

                // 4. Build conversation history
                String historyStr = buildConversationHistory(conversationHistory);

                // 5. Extract citations (pre-computed before streaming starts)
                List<Citation> citations = matches.stream()
                                .map(m -> new Citation(
                                                m.documentTitle(),
                                                m.chunkIndex(),
                                                m.content(),
                                                m.similarity()))
                                .toList();

                // 6. Stream LLM completion
                String targetModel = model != null ? model : modelConfig.getDefaultExtractionModel();
                ModelGateway chatGateway = gatewayFactory.getGatewayForModel(targetModel);

                Flux<String> tokenStream = chatGateway.completeStreaming(
                                new CompletionRequest(
                                                RAG_SYSTEM_PROMPT,
                                                buildConversationalPrompt(question, context, historyStr),
                                                targetModel,
                                                defaultCompletionParams()));

                return new StreamingQueryContext(tokenStream, citations, computeConfidence(matches));
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

                                Provide a concise answer (max 5 sentences) with citations:
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

                                Provide a concise answer (max 5 sentences) with citations. Use the conversation history
                                to understand any follow-up questions or references to previous answers:
                                """.formatted(context, history, question);
        }

        private Map<String, Object> defaultCompletionParams() {
                return Map.of(
                                "num_predict", 96,
                                "temperature", 0.2);
        }

        /**
         * Log detailed query performance metrics.
         *
         * Interview Note: Structured performance logging enables:
         * 1. Dashboards showing p50/p95/p99 latencies by stage
         * 2. Alerts when total latency exceeds budget
         * 3. Bottleneck identification (is it embedding? search? or LLM?)
         * 4. Cost optimization (track expensive models)
         */
        private void logQueryPerformance(String mode,
                        long totalLatencyMs,
                        long embedLatencyMs,
                        long searchLatencyMs,
                        long completionLatencyMs,
                        int matchCount,
                        String model) {
                boolean overBudget = totalLatencyMs > latencyBudgetMs;
                long overByMs = overBudget ? totalLatencyMs - latencyBudgetMs : 0;

                StructuredLogger.info(log, "query_performance")
                        .field("mode", mode)
                        .field("total_latency_ms", totalLatencyMs)
                        .field("embedding_latency_ms", embedLatencyMs)
                        .field("search_latency_ms", searchLatencyMs)
                        .field("completion_latency_ms", completionLatencyMs)
                        .field("matches_count", matchCount)
                        .field("model", model)
                        .field("latency_budget_ms", latencyBudgetMs)
                        .field("over_budget", overBudget)
                        .field("over_by_ms", overByMs)
                        .log();
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
