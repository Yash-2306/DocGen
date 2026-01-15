package com.docgen.service;

import com.docgen.model.CodeChunk;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * RagService implements a full Retrieval-Augmented Generation pipeline:
 *
 *  1. CHUNK  - Split the uploaded Java code into method-level blocks
 *  2. EMBED  - Convert each chunk to a 768-dim vector via Gemini embeddings
 *  3. STORE  - Keep chunks + vectors in a simple in-memory list
 *  4. QUERY  - Embed the user's question, compute cosine similarity,
 *              return the top-K most relevant chunks
 *  5. ANSWER - Build a prompt with those chunks and call Gemini to answer
 *
 * Interview note: This is exactly the pipeline used in production RAG systems.
 * The only difference at scale is replacing the in-memory list with a vector
 * database (Pinecone, Chroma, Weaviate) for faster approximate nearest-neighbour
 * search across millions of chunks.
 */
@Service
public class RagService {

    // Number of chunks to retrieve for each question (top-K)
    private static final int TOP_K = 3;

    // Rough max chars per chunk (~60-80 lines of code)
    private static final int CHUNK_SIZE_CHARS = 1500;

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent}")
    private String geminiGenerateUrl;

    @Value("${gemini.api.key:}")
    private String apiKey;

    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    // In-memory vector store: a simple list of CodeChunks (text + embedding)
    private final List<CodeChunk> vectorStore = new ArrayList<>();

    public RagService(EmbeddingService embeddingService, ObjectMapper objectMapper) {
        this.embeddingService = embeddingService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().build();
    }

    // =========================================================================
    // STEP 1 + 2 + 3 : Index the uploaded source code
    // =========================================================================

    /**
     * Clears the current index and rebuilds it from the provided source code.
     * Splits code into method-level chunks, embeds each one, and stores them.
     *
     * @param code Full Java source file content
     * @return Number of chunks indexed
     */
    public int indexCode(String code) throws Exception {
        vectorStore.clear();
        List<String> chunks = splitIntoChunks(code);

        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            double[] embedding = embeddingService.embed(chunk);
            String label = "Chunk " + (i + 1) + " of " + chunks.size();
            vectorStore.add(new CodeChunk(chunk, embedding, label));
        }
        return vectorStore.size();
    }

    /**
     * Splits Java source code into method-level chunks.
     * Strategy: detect method signatures by regex, then group lines together.
     * Falls back to fixed character-size chunks for non-standard code.
     */
    private List<String> splitIntoChunks(String code) {
        List<String> chunks = new ArrayList<>();

        // Split on method boundaries: lines that start a method declaration
        // Regex matches common Java method patterns like:
        //   public ResponseEntity<..> getUser(...) {
        //   @GetMapping private List<..> findAll() {
        Pattern methodPattern = Pattern.compile(
            "(?m)^\\s{0,4}(@\\w+[^\\n]*\\n)*"   // optional annotations
            + "\\s{0,4}(public|private|protected)"  // access modifier
            + "[^{;]+\\{\\s*$"                      // up to the opening brace
        );

        List<Integer> splitPoints = new ArrayList<>();
        splitPoints.add(0);

        Matcher matcher = methodPattern.matcher(code);
        while (matcher.find()) {
            if (matcher.start() > 0) {
                splitPoints.add(matcher.start());
            }
        }
        splitPoints.add(code.length());

        for (int i = 0; i < splitPoints.size() - 1; i++) {
            String chunk = code.substring(splitPoints.get(i), splitPoints.get(i + 1)).trim();
            if (chunk.isEmpty()) continue;

            // If a chunk is too big, split it further by character count
            if (chunk.length() > CHUNK_SIZE_CHARS) {
                for (int start = 0; start < chunk.length(); start += CHUNK_SIZE_CHARS) {
                    int end = Math.min(start + CHUNK_SIZE_CHARS, chunk.length());
                    String subChunk = chunk.substring(start, end).trim();
                    if (!subChunk.isEmpty()) {
                        chunks.add(subChunk);
                    }
                }
            } else {
                chunks.add(chunk);
            }
        }

        // Ensure we always have at least one chunk
        if (chunks.isEmpty() && !code.trim().isEmpty()) {
            chunks.add(code.trim());
        }

        return chunks;
    }

    // =========================================================================
    // STEP 4 : Retrieve — cosine similarity search
    // =========================================================================

    /**
     * Embeds the question, then ranks all stored chunks by cosine similarity.
     * Returns the TOP_K most relevant chunks.
     *
     * Cosine similarity formula:
     *   similarity = (A · B) / (|A| * |B|)
     * where A and B are two vectors. Score of 1.0 = identical meaning.
     */
    public List<CodeChunk> retrieve(String question) throws Exception {
        if (vectorStore.isEmpty()) {
            return Collections.emptyList();
        }

        double[] questionEmbedding = embeddingService.embed(question);

        // Score every stored chunk against the question vector
        return vectorStore.stream()
            .sorted(Comparator.comparingDouble(
                (CodeChunk chunk) -> cosineSimilarity(questionEmbedding, chunk.getEmbedding())
            ).reversed())
            .limit(TOP_K)
            .collect(Collectors.toList());
    }

    /**
     * Computes cosine similarity between two equal-length vectors.
     * Range: -1 (opposite) to 0 (unrelated) to 1 (identical meaning).
     */
    private double cosineSimilarity(double[] a, double[] b) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // =========================================================================
    // STEP 5 : Generate — build prompt and call Gemini
    // =========================================================================

    /**
     * Builds a prompt that injects the retrieved code chunks as context,
     * then asks Gemini to answer the user's question based only on that context.
     *
     * Interview note: This "context stuffing" pattern is the core of RAG.
     * By feeding retrieved chunks instead of the full file, we:
     *   1. Stay within token limits
     *   2. Reduce hallucination (model only sees relevant code)
     *   3. Speed up inference (smaller prompt = faster response)
     */
    public String generateAnswer(String question, List<CodeChunk> retrievedChunks) throws Exception {
        if (retrievedChunks.isEmpty()) {
            return "No relevant code was found in the indexed codebase. "
                 + "Please index your controller code first by clicking 'Index for Q&A'.";
        }

        // Build context block from retrieved chunks
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < retrievedChunks.size(); i++) {
            context.append("--- Code Chunk ").append(i + 1).append(" ---\n");
            context.append(retrievedChunks.get(i).getText()).append("\n\n");
        }

        String prompt = "You are a senior Java developer reviewing Spring Boot source code.\n"
            + "Answer the developer's question using ONLY the code context provided below.\n"
            + "Be precise and concise. If the answer cannot be determined from the code, say so.\n\n"
            + "CODE CONTEXT:\n"
            + context
            + "QUESTION: " + question + "\n\n"
            + "ANSWER:";

        // Call Gemini generate API
        Map<String, Object> part = Map.of("text", prompt);
        Map<String, Object> content = Map.of("parts", List.of(part));
        Map<String, Object> requestBody = Map.of("contents", List.of(content));

        String requestJson = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(geminiGenerateUrl + "?key=" + apiKey))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestJson))
            .build();

        HttpResponse<String> response = httpClient.send(
            request, HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new RuntimeException("Gemini API error " + response.statusCode()
                + ": " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        return root.path("candidates")
                   .path(0)
                   .path("content")
                   .path("parts")
                   .path(0)
                   .path("text")
                   .asText("Could not extract answer from Gemini response.");
    }

    /**
     * Returns how many chunks are currently indexed.
     */
    public int getIndexedChunkCount() {
        return vectorStore.size();
    }

    /**
     * Clears the in-memory vector store.
     */
    public void clearIndex() {
        vectorStore.clear();
    }
}
