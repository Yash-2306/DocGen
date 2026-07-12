package com.docgen.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

/**
 * EmbeddingService calls the Gemini text-embedding-004 model
 * to convert any text string into a 768-dimensional float vector.
 *
 * Interview note: "text-embedding-004" is Google's latest embedding model.
 * It maps text into a high-dimensional vector space where semantically similar
 * texts have vectors that are close together (high cosine similarity).
 */
@Service
public class EmbeddingService {

    private static final String EMBEDDING_URL =
        "https://generativelanguage.googleapis.com/v1/models/text-embedding-004:embedContent";

    @Value("${gemini.api.key:}")
    private String apiKey;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public EmbeddingService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().build();
    }

    /**
     * Converts the given text into a 768-dimensional embedding vector.
     *
     * @param text The input text to embed (a code chunk or a question)
     * @return double[] vector of length 768
     * @throws Exception if the Gemini API call fails
     */
    public double[] embed(String text) throws Exception {
        // Build the request body per Gemini embedding API spec
        Map<String, Object> content = Map.of(
            "parts", new Object[]{Map.of("text", text)}
        );
        Map<String, Object> requestBody = Map.of(
            "model", "models/text-embedding-004",
            "content", content
        );

        String requestJson = objectMapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(EMBEDDING_URL + "?key=" + apiKey))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestJson))
            .build();

        HttpResponse<String> response = httpClient.send(
            request, HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new RuntimeException("Embedding API error " + response.statusCode()
                + ": " + response.body());
        }

        // Parse the response: embedding.values is a JSON array of floats
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode values = root.path("embedding").path("values");

        double[] vector = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            vector[i] = values.get(i).asDouble();
        }
        return vector;
    }
}
