package com.docgen.controller;

import com.docgen.model.ApiDocRequest;
import com.docgen.model.ApiDocumentation;
import com.docgen.model.ApiDocumentation.Parameter;
import com.docgen.model.SavedDoc;
import com.docgen.model.SavedDocRepository;
import com.docgen.service.GeminiService;
import com.docgen.service.PdfExportService;
import com.docgen.service.RagService;
import com.docgen.model.CodeChunk;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api")
public class DocApiController {

    private final GeminiService geminiService;
    private final PdfExportService pdfExportService;
    private final SavedDocRepository savedDocRepository;
    private final ObjectMapper objectMapper;
    private final RagService ragService;

    public DocApiController(GeminiService geminiService, PdfExportService pdfExportService,
                            SavedDocRepository savedDocRepository, ObjectMapper objectMapper,
                            RagService ragService) {
        this.geminiService = geminiService;
        this.pdfExportService = pdfExportService;
        this.savedDocRepository = savedDocRepository;
        this.objectMapper = objectMapper;
        this.ragService = ragService;
    }

    @PostMapping(value = "/generate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> generateDocs(@RequestBody ApiDocRequest request) {
        try {
            String code = request.getControllerCode();
            if (code == null || code.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Controller code must not be empty.");
            }
            
            int loc = code.split("\r\n|\r|\n").length;
            long startTime = System.currentTimeMillis();
            
            ApiDocumentation docs = geminiService.generateDocs(code);
            
            long durationMs = System.currentTimeMillis() - startTime;

            Map<String, Object> response = new HashMap<>();
            response.put("documentation", docs);
            response.put("metrics", Map.of(
                "parsingTimeMs", durationMs,
                "linesOfCode", loc,
                "endpointsCount", docs.getEndpoints() != null ? docs.getEndpoints().size() : 0
            ));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error generating documentation: " + e.getMessage());
        }
    }

    @PostMapping(value = "/generate-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> generateDocsFromFile(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("Please select a file to upload.");
            }
            String code = new String(file.getBytes(), StandardCharsets.UTF_8);
            int loc = code.split("\r\n|\r|\n").length;
            long startTime = System.currentTimeMillis();
            
            ApiDocumentation docs = geminiService.generateDocs(code);
            
            long durationMs = System.currentTimeMillis() - startTime;

            Map<String, Object> response = new HashMap<>();
            response.put("documentation", docs);
            response.put("metrics", Map.of(
                "parsingTimeMs", durationMs,
                "linesOfCode", loc,
                "endpointsCount", docs.getEndpoints() != null ? docs.getEndpoints().size() : 0
            ));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error generating documentation from file: " + e.getMessage());
        }
    }

    // CRUD - History Save
    @PostMapping(value = "/save", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> saveDoc(@RequestBody Map<String, Object> payload) {
        try {
            ApiDocumentation docs = objectMapper.convertValue(payload.get("documentation"), ApiDocumentation.class);
            Map<?, ?> metrics = (Map<?, ?>) payload.get("metrics");
            
            int loc = metrics != null && metrics.get("linesOfCode") != null ? ((Number) metrics.get("linesOfCode")).intValue() : 0;
            long time = metrics != null && metrics.get("parsingTimeMs") != null ? ((Number) metrics.get("parsingTimeMs")).longValue() : 0;

            SavedDoc doc = new SavedDoc();
            doc.setTitle(docs.getTitle());
            doc.setDescription(docs.getDescription());
            doc.setBasePath(docs.getBasePath());
            doc.setEndpointsJson(objectMapper.writeValueAsString(docs.getEndpoints()));
            doc.setLinesOfCode(loc);
            doc.setParsingTimeMs(time);

            SavedDoc saved = savedDocRepository.save(doc);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error saving documentation to history: " + e.getMessage());
        }
    }

    // CRUD - History Retrieve List
    @GetMapping("/history")
    public ResponseEntity<?> getHistory() {
        try {
            List<SavedDoc> list = savedDocRepository.findAll();
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error retrieving history: " + e.getMessage());
        }
    }

    // CRUD - History Delete
    @DeleteMapping("/history/{id}")
    public ResponseEntity<?> deleteHistory(@PathVariable("id") Long id) {
        try {
            savedDocRepository.deleteById(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error deleting document from history: " + e.getMessage());
        }
    }

    @PostMapping(value = "/export/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<?> exportPdf(@RequestBody ApiDocumentation docs) {
        try {
            byte[] pdfBytes = pdfExportService.generatePdf(docs);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDispositionFormData("attachment", "api-documentation.pdf");
            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error exporting PDF: " + e.getMessage());
        }
    }

    @PostMapping(value = "/export/html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<?> exportHtml(@RequestBody ApiDocumentation docs) {
        try {
            String htmlContent = buildStandaloneHtml(docs);
            byte[] htmlBytes = htmlContent.getBytes(StandardCharsets.UTF_8);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDispositionFormData("attachment", "api-documentation.html");
            return new ResponseEntity<>(htmlBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error exporting HTML: " + e.getMessage());
        }
    }

    // OpenAPI 3.0 JSON Export
    @PostMapping(value = "/export/openapi", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> exportOpenApi(@RequestBody ApiDocumentation docs) {
        try {
            Map<String, Object> openapi = new LinkedHashMap<>();
            openapi.put("openapi", "3.0.3");
            
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("title", docs.getTitle());
            info.put("description", docs.getDescription());
            info.put("version", "1.0.0");
            openapi.put("info", info);

            List<Map<String, String>> servers = new ArrayList<>();
            servers.add(Map.of("url", docs.getBasePath() != null && !docs.getBasePath().isEmpty() ? docs.getBasePath() : "/"));
            openapi.put("servers", servers);

            Map<String, Object> paths = new LinkedHashMap<>();
            if (docs.getEndpoints() != null) {
                for (var ep : docs.getEndpoints()) {
                    String path = ep.getPath();
                    if (!path.startsWith("/")) {
                        path = "/" + path;
                    }
                    
                    Map<String, Object> pathOperations = (Map<String, Object>) paths.computeIfAbsent(path, k -> new LinkedHashMap<String, Object>());
                    
                    Map<String, Object> opDetails = new LinkedHashMap<>();
                    opDetails.put("summary", ep.getSummary());
                    opDetails.put("description", ep.getDescription());

                    // Parameters (headers, path variables, query parameters)
                    List<Map<String, Object>> parameters = new ArrayList<>();
                    addParametersToOpenApi(parameters, ep.getHeaders(), "header");
                    addParametersToOpenApi(parameters, ep.getPathVariables(), "path");
                    addParametersToOpenApi(parameters, ep.getQueryParams(), "query");
                    if (!parameters.isEmpty()) {
                        opDetails.put("parameters", parameters);
                    }

                    // Request Body
                    if (ep.getRequestBody() != null && ep.getRequestBody().getExampleJson() != null && !ep.getRequestBody().getExampleJson().trim().isEmpty()) {
                        Map<String, Object> reqBodyNode = new LinkedHashMap<>();
                        reqBodyNode.put("description", ep.getRequestBody().getDescription());
                        
                        Map<String, Object> contentMap = new LinkedHashMap<>();
                        Map<String, Object> mimeMap = new LinkedHashMap<>();
                        mimeMap.put("schema", parseJsonOrString(ep.getRequestBody().getSchemaJson()));
                        mimeMap.put("example", parseJsonOrString(ep.getRequestBody().getExampleJson()));
                        
                        contentMap.put(ep.getRequestBody().getContentType(), mimeMap);
                        reqBodyNode.put("content", contentMap);
                        opDetails.put("requestBody", reqBodyNode);
                    }

                    // Responses
                    Map<String, Object> responsesMap = new LinkedHashMap<>();
                    if (ep.getResponses() != null) {
                        for (var resp : ep.getResponses()) {
                            Map<String, Object> respDetails = new LinkedHashMap<>();
                            respDetails.put("description", resp.getDescription());
                            
                            if (resp.getExampleJson() != null && !resp.getExampleJson().trim().isEmpty()) {
                                Map<String, Object> contentMap = new LinkedHashMap<>();
                                Map<String, Object> mimeMap = new LinkedHashMap<>();
                                mimeMap.put("schema", parseJsonOrString(resp.getSchemaJson()));
                                mimeMap.put("example", parseJsonOrString(resp.getExampleJson()));
                                contentMap.put(resp.getContentType(), mimeMap);
                                respDetails.put("content", contentMap);
                            }
                            responsesMap.put(String.valueOf(resp.getStatusCode()), respDetails);
                        }
                    }
                    if (responsesMap.isEmpty()) {
                        responsesMap.put("200", Map.of("description", "Success"));
                    }
                    opDetails.put("responses", responsesMap);

                    pathOperations.put(ep.getMethod().toLowerCase(), opDetails);
                }
            }
            openapi.put("paths", paths);

            byte[] jsonBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(openapi);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDispositionFormData("attachment", "openapi-spec.json");
            return new ResponseEntity<>(jsonBytes, headers, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error exporting OpenAPI JSON: " + e.getMessage());
        }
    }

    private void addParametersToOpenApi(List<Map<String, Object>> parameters, List<Parameter> sourceParams, String inType) {
        if (sourceParams != null) {
            for (var p : sourceParams) {
                Map<String, Object> paramNode = new LinkedHashMap<>();
                paramNode.put("name", p.getName());
                paramNode.put("in", inType);
                paramNode.put("required", p.isRequired());
                paramNode.put("description", p.getDescription());
                paramNode.put("schema", Map.of("type", p.getType() != null ? p.getType() : "string"));
                if (p.getExample() != null && !p.getExample().isEmpty()) {
                    paramNode.put("example", p.getExample());
                }
                parameters.add(paramNode);
            }
        }
    }

    private Object parseJsonOrString(String str) {
        if (str == null || str.trim().isEmpty()) {
            return Map.of("type", "object");
        }
        try {
            return objectMapper.readTree(str);
        } catch (Exception e) {
            // Return as String if it is not valid JSON
            return str;
        }
    }

    private String buildStandaloneHtml(ApiDocumentation docs) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n")
            .append("<html lang=\"en\" class=\"dark\">\n")
            .append("<head>\n")
            .append("  <meta charset=\"UTF-8\">\n")
            .append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
            .append("  <title>").append(docs.getTitle()).append(" - API Docs</title>\n")
            .append("  <script src=\"https://cdn.tailwindcss.com\"></script>\n")
            .append("  <script>\n")
            .append("    tailwind.config = {\n")
            .append("      darkMode: 'class',\n")
            .append("      theme: {\n")
            .append("        extend: {\n")
            .append("          colors: {\n")
            .append("            slate: {\n")
            .append("              950: '#020617'\n")
            .append("            }\n")
            .append("          }\n")
            .append("        }\n")
            .append("      }\n")
            .append("    }\n")
            .append("  </script>\n")
            .append("</head>\n")
            .append("<body class=\"bg-slate-950 text-slate-100 min-h-screen font-sans\">\n")
            .append("  <div class=\"max-w-6xl mx-auto px-4 py-8\">\n")
            .append("    <!-- Header -->\n")
            .append("    <header class=\"mb-8 border-b border-slate-800 pb-6\">\n")
            .append("      <h1 class=\"text-4xl font-extrabold tracking-tight bg-gradient-to-r from-teal-400 to-blue-500 bg-clip-text text-transparent\">")
            .append(docs.getTitle()).append("</h1>\n")
            .append("      <p class=\"mt-3 text-lg text-slate-400\">").append(docs.getDescription()).append("</p>\n")
            .append("      ").append((docs.getBasePath() != null && !docs.getBasePath().isEmpty()) ? "<div class=\"mt-4 inline-flex items-center gap-2 px-3 py-1.5 rounded-md bg-slate-900 border border-slate-800 text-sm font-mono\"><span class=\"text-slate-500\">Base Path:</span><span class=\"text-teal-400 font-semibold\">" + docs.getBasePath() + "</span></div>" : "").append("\n")
            .append("    </header>\n")
            .append("    \n")
            .append("    <!-- Endpoints -->\n")
            .append("    <main class=\"space-y-12\">\n");

        if (docs.getEndpoints() == null || docs.getEndpoints().isEmpty()) {
            html.append("      <div class=\"text-center py-12 text-slate-500\">No endpoints documented.</div>\n");
        } else {
            for (var endpoint : docs.getEndpoints()) {
                String methodColor = "bg-teal-500/10 text-teal-400 border-teal-500/20";
                if ("POST".equalsIgnoreCase(endpoint.getMethod())) methodColor = "bg-emerald-500/10 text-emerald-400 border-emerald-500/20";
                else if ("PUT".equalsIgnoreCase(endpoint.getMethod())) methodColor = "bg-amber-500/10 text-amber-400 border-amber-500/20";
                else if ("DELETE".equalsIgnoreCase(endpoint.getMethod())) methodColor = "bg-red-500/10 text-red-400 border-red-500/20";

                html.append("      <section class=\"bg-slate-900 border border-slate-800 rounded-xl overflow-hidden shadow-xl\">\n")
                    .append("        <!-- Method + Path Bar -->\n")
                    .append("        <div class=\"flex items-center gap-4 px-6 py-4 bg-slate-900 border-b border-slate-800\">\n")
                    .append("          <span class=\"px-2.5 py-1 text-sm font-extrabold rounded-md border ").append(methodColor).append("\">")
                    .append(endpoint.getMethod().toUpperCase()).append("</span>\n")
                    .append("          <span class=\"font-mono font-bold text-lg text-slate-200\">").append(endpoint.getPath()).append("</span>\n")
                    .append("        </div>\n")
                    .append("        \n")
                    .append("        <!-- Info Body -->\n")
                    .append("        <div class=\"p-6 space-y-6\">\n")
                    .append("          <!-- Description -->\n")
                    .append("          <div>\n")
                    .append("            <h3 class=\"text-sm font-semibold uppercase tracking-wider text-slate-500 mb-1\">Summary</h3>\n")
                    .append("            <p class=\"text-slate-300 font-medium text-lg\">").append(endpoint.getSummary()).append("</p>\n")
                    .append("            <p class=\"text-slate-400 text-sm mt-1\">").append(endpoint.getDescription()).append("</p>\n")
                    .append("          </div>\n");

                // Headers
                appendHtmlParamsTable(html, "Headers", endpoint.getHeaders());

                // Path Variables
                appendHtmlParamsTable(html, "Path Variables", endpoint.getPathVariables());

                // Query Parameters
                appendHtmlParamsTable(html, "Query Parameters", endpoint.getQueryParams());

                // Request Body
                if (endpoint.getRequestBody() != null && 
                    ((endpoint.getRequestBody().getExampleJson() != null && !endpoint.getRequestBody().getExampleJson().isEmpty()) ||
                     (endpoint.getRequestBody().getDescription() != null && !endpoint.getRequestBody().getDescription().isEmpty()))) {
                    
                    html.append("          <div class=\"border-t border-slate-800 pt-6\">\n")
                        .append("            <h3 class=\"text-sm font-semibold uppercase tracking-wider text-slate-500 mb-2\">Request Body</h3>\n");
                    if (endpoint.getRequestBody().getDescription() != null && !endpoint.getRequestBody().getDescription().isEmpty()) {
                        html.append("            <p class=\"text-sm text-slate-400 mb-3\">").append(endpoint.getRequestBody().getDescription()).append(" (").append(endpoint.getRequestBody().getContentType()).append(")</p>\n");
                    }
                    if (endpoint.getRequestBody().getExampleJson() != null && !endpoint.getRequestBody().getExampleJson().isEmpty()) {
                        html.append("            <pre class=\"bg-slate-950 border border-slate-850 rounded-lg p-4 font-mono text-sm overflow-x-auto text-teal-300\">")
                            .append(endpoint.getRequestBody().getExampleJson()).append("</pre>\n");
                    }
                    html.append("          </div>\n");
                }

                // Responses
                if (endpoint.getResponses() != null && !endpoint.getResponses().isEmpty()) {
                    html.append("          <div class=\"border-t border-slate-800 pt-6\">\n")
                        .append("            <h3 class=\"text-sm font-semibold uppercase tracking-wider text-slate-500 mb-4\">Responses</h3>\n")
                        .append("            <div class=\"space-y-4\">\n");
                    
                    for (var response : endpoint.getResponses()) {
                        String statusBg = "bg-emerald-500/10 text-emerald-400 border-emerald-500/20";
                        if (response.getStatusCode() >= 400) statusBg = "bg-red-500/10 text-red-400 border-red-500/20";
                        else if (response.getStatusCode() >= 300) statusBg = "bg-amber-500/10 text-amber-400 border-amber-500/20";

                        html.append("              <div class=\"bg-slate-950/40 border border-slate-800/80 rounded-lg p-4\">\n")
                            .append("                <div class=\"flex items-center gap-2 mb-2\">\n")
                            .append("                  <span class=\"px-2 py-0.5 text-xs font-bold rounded border ").append(statusBg).append("\">")
                            .append(response.getStatusCode()).append("</span>\n")
                            .append("                  <span class=\"text-sm text-slate-300 font-semibold\">").append(response.getDescription()).append("</span>\n")
                            .append("                  <span class=\"text-xs text-slate-500 ml-auto\">").append(response.getContentType()).append("</span>\n")
                            .append("                </div>\n");

                        if (response.getExampleJson() != null && !response.getExampleJson().isEmpty()) {
                            html.append("                <pre class=\"mt-2 bg-slate-950 border border-slate-850 rounded p-3 font-mono text-xs overflow-x-auto text-blue-300\">")
                                .append(response.getExampleJson()).append("</pre>\n");
                        }
                        html.append("              </div>\n");
                    }
                    html.append("            </div>\n")
                        .append("          </div>\n");
                }

                html.append("        </div>\n")
                    .append("      </section>\n");
            }
        }

        html.append("    </main>\n")
            .append("    \n")
            .append("    <!-- Footer -->\n")
            .append("    <footer class=\"mt-16 text-center text-sm text-slate-600 border-t border-slate-900 pt-6\">\n")
            .append("      Generated by DocGen AI-Powered Documentation Generator.\n")
            .append("    </footer>\n")
            .append("  </div>\n")
            .append("</body>\n")
            .append("</html>");

        return html.toString();
    }

    private void appendHtmlParamsTable(StringBuilder html, String title, java.util.List<Parameter> params) {
        if (params == null || params.isEmpty()) return;

        html.append("          <div class=\"border-t border-slate-800 pt-6\">\n")
            .append("            <h3 class=\"text-sm font-semibold uppercase tracking-wider text-slate-500 mb-3\">").append(title).append("</h3>\n")
            .append("            <div class=\"overflow-x-auto\">\n")
            .append("              <table class=\"min-w-full divide-y divide-slate-800 text-left text-sm\">\n")
            .append("                <thead>\n")
            .append("                  <tr class=\"text-slate-400 font-semibold\">\n")
            .append("                    <th class=\"pb-2\">Name</th>\n")
            .append("                    <th class=\"pb-2\">Type</th>\n")
            .append("                    <th class=\"pb-2\">Required</th>\n")
            .append("                    <th class=\"pb-2\">Description</th>\n")
            .append("                  </tr>\n")
            .append("                </thead>\n")
            .append("                <tbody class=\"divide-y divide-slate-850 text-slate-300\">\n");

        for (var param : params) {
            html.append("                  <tr>\n")
                .append("                    <td class=\"py-2 font-mono font-semibold text-teal-400\">").append(param.getName()).append("</td>\n")
                .append("                    <td class=\"py-2 text-slate-400\">").append(param.getType()).append("</td>\n")
                .append("                    <td class=\"py-2\">").append(param.isRequired() ? "<span class=\"text-red-400 font-medium text-xs bg-red-400/10 px-1.5 py-0.5 rounded border border-red-400/20\">Yes</span>" : "<span class=\"text-slate-500 text-xs\">No</span>").append("</td>\n")
                .append("                    <td class=\"py-2 text-slate-400\">").append(param.getDescription()).append("</td>\n")
                .append("                  </tr>\n");
        }

        html.append("                </tbody>\n")
            .append("              </table>\n")
            .append("            </div>\n")
            .append("          </div>\n");
    }

    // =========================================================================
    // RAG ENDPOINTS
    // =========================================================================

    /**
     * Indexes the provided source code into the in-memory RAG vector store.
     * Chunks the code by method, embeds each chunk with Gemini text-embedding-004,
     * and stores the resulting vectors for similarity search.
     */
    @PostMapping(value = "/rag/index", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> ragIndex(@RequestBody Map<String, String> body) {
        try {
            String code = body.get("code");
            if (code == null || code.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("No code provided for indexing.");
            }
            int chunks = ragService.indexCode(code);
            return ResponseEntity.ok(Map.of(
                "message", "Code indexed successfully.",
                "chunksIndexed", chunks
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error indexing code: " + e.getMessage());
        }
    }

    /**
     * Answers a natural-language question about the indexed codebase.
     * Embeds the question, retrieves the top-3 most relevant code chunks
     * using cosine similarity, then calls Gemini to generate an answer.
     */
    @PostMapping(value = "/rag/ask", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> ragAsk(@RequestBody Map<String, String> body) {
        try {
            String question = body.get("question");
            if (question == null || question.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Question must not be empty.");
            }
            if (ragService.getIndexedChunkCount() == 0) {
                return ResponseEntity.ok(Map.of(
                    "answer", "No code has been indexed yet. Please click 'Index for Q&A' first.",
                    "chunksRetrieved", 0
                ));
            }
            List<CodeChunk> retrieved = ragService.retrieve(question);
            String answer = ragService.generateAnswer(question, retrieved);
            return ResponseEntity.ok(Map.of(
                "answer", answer,
                "chunksRetrieved", retrieved.size()
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error answering question: " + e.getMessage());
        }
    }
}
