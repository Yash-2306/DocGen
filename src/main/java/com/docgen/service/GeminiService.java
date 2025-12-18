package com.docgen.service;

import com.docgen.model.ApiDocumentation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent}")
    private String apiUrl;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GeminiService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().build();
    }

    public ApiDocumentation generateDocs(String controllerCode) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Gemini API Key is not configured. Please set the GEMINI_API_KEY environment variable.");
        }

        String prompt = "You are an expert developer tool. Parse the following Spring Boot Controller Java code " +
                "and generate structured API documentation JSON matching the schema below. Detect RequestMappings, " +
                "GetMapping, PostMapping, PutMapping, DeleteMapping, PathVariables, RequestParams, RequestHeaders, and RequestBodies. " +
                "Create mock request and response JSON examples based on the parameter types and method signatures.\n\n" +
                "Output MUST be a single JSON object matching this structure exactly (do not wrap in markdown ```json blocks):\n" +
                "{\n" +
                "  \"title\": \"API Service Title\",\n" +
                "  \"description\": \"Overall description of what this controller does\",\n" +
                "  \"basePath\": \"The base controller path from @RequestMapping, if any\",\n" +
                "  \"endpoints\": [\n" +
                "    {\n" +
                "      \"path\": \"/endpoint-path\",\n" +
                "      \"method\": \"GET/POST/PUT/DELETE\",\n" +
                "      \"summary\": \"Brief one line summary of the endpoint's purpose\",\n" +
                "      \"description\": \"Detailed description of the endpoint behavior\",\n" +
                "      \"headers\": [\n" +
                "        { \"name\": \"Header-Name\", \"type\": \"string/number/boolean\", \"required\": true, \"description\": \"header description\", \"example\": \"header-value\" }\n" +
                "      ],\n" +
                "      \"queryParams\": [\n" +
                "        { \"name\": \"paramName\", \"type\": \"string\", \"required\": false, \"description\": \"query param description\", \"example\": \"value\" }\n" +
                "      ],\n" +
                "      \"pathVariables\": [\n" +
                "        { \"name\": \"id\", \"type\": \"number\", \"required\": true, \"description\": \"path parameter description\", \"example\": \"1\" }\n" +
                "      ],\n" +
                "      \"requestBody\": {\n" +
                "        \"contentType\": \"application/json\",\n" +
                "        \"description\": \"Description of the payload\",\n" +
                "        \"schemaJson\": \"JSON representation of the expected schema keys and types\",\n" +
                "        \"exampleJson\": \"Formatted sample JSON object for the payload\"\n" +
                "      },\n" +
                "      \"responses\": [\n" +
                "        {\n" +
                "          \"statusCode\": 200,\n" +
                "          \"description\": \"Successful response description\",\n" +
                "          \"contentType\": \"application/json\",\n" +
                "          \"schemaJson\": \"JSON representation of response schema\",\n" +
                "          \"exampleJson\": \"Formatted sample response JSON object\"\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  ]\n" +
                "}\n\n" +
                "Source Controller Code:\n" +
                controllerCode;

        // Build Gemini Request Payload
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("text", prompt);

        Map<String, Object> contentNode = new HashMap<>();
        contentNode.put("parts", List.of(textPart));

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("responseMimeType", "application/json");

        Map<String, Object> requestPayload = new HashMap<>();
        requestPayload.put("contents", List.of(contentNode));
        requestPayload.put("generationConfig", generationConfig);

        String jsonPayload = objectMapper.writeValueAsString(requestPayload);

        URI targetUri = URI.create(apiUrl + "?key=" + apiKey);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(targetUri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Gemini API call failed with status: " + response.statusCode() + " - " + response.body());
        }

        // Parse Response JSON
        JsonNode rootNode = objectMapper.readTree(response.body());
        String rawText = rootNode.path("candidates")
                .path(0)
                .path("content")
                .path("parts")
                .path(0)
                .path("text")
                .asText();

        if (rawText == null || rawText.trim().isEmpty()) {
            throw new RuntimeException("Gemini API returned an empty response. Verify your prompt or controller code.");
        }

        // Deserialize Gemini raw JSON text back into our ApiDocumentation Java object
        return objectMapper.readValue(rawText.trim(), ApiDocumentation.class);
    }
}
