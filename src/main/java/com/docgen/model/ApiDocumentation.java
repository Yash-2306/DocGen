package com.docgen.model;

import java.util.ArrayList;
import java.util.List;

public class ApiDocumentation {
    private String title = "API Documentation";
    private String description = "Auto-generated documentation of the REST endpoints.";
    private String basePath = "";
    private List<Endpoint> endpoints = new ArrayList<>();

    // Getters and Setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getBasePath() { return basePath; }
    public void setBasePath(String basePath) { this.basePath = basePath; }

    public List<Endpoint> getEndpoints() { return endpoints; }
    public void setEndpoints(List<Endpoint> endpoints) { this.endpoints = endpoints; }

    public static class Endpoint {
        private String path = "";
        private String method = "GET";
        private String summary = "";
        private String description = "";
        private List<Parameter> headers = new ArrayList<>();
        private List<Parameter> queryParams = new ArrayList<>();
        private List<Parameter> pathVariables = new ArrayList<>();
        private RequestBodyInfo requestBody;
        private List<ResponseInfo> responses = new ArrayList<>();

        // Getters and Setters
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }

        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }

        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public List<Parameter> getHeaders() { return headers; }
        public void setHeaders(List<Parameter> headers) { this.headers = headers; }

        public List<Parameter> getQueryParams() { return queryParams; }
        public void setQueryParams(List<Parameter> queryParams) { this.queryParams = queryParams; }

        public List<Parameter> getPathVariables() { return pathVariables; }
        public void setPathVariables(List<Parameter> pathVariables) { this.pathVariables = pathVariables; }

        public RequestBodyInfo getRequestBody() { return requestBody; }
        public void setRequestBody(RequestBodyInfo requestBody) { this.requestBody = requestBody; }

        public List<ResponseInfo> getResponses() { return responses; }
        public void setResponses(List<ResponseInfo> responses) { this.responses = responses; }
    }

    public static class Parameter {
        private String name = "";
        private String type = "string";
        private boolean required = false;
        private String description = "";
        private String example = "";

        // Getters and Setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getExample() { return example; }
        public void setExample(String example) { this.example = example; }
    }

    public static class RequestBodyInfo {
        private String contentType = "application/json";
        private String description = "";
        private String schemaJson = "";
        private String exampleJson = "";

        // Getters and Setters
        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getSchemaJson() { return schemaJson; }
        public void setSchemaJson(String schemaJson) { this.schemaJson = schemaJson; }

        public String getExampleJson() { return exampleJson; }
        public void setExampleJson(String exampleJson) { this.exampleJson = exampleJson; }
    }

    public static class ResponseInfo {
        private int statusCode = 200;
        private String description = "Success";
        private String contentType = "application/json";
        private String schemaJson = "";
        private String exampleJson = "";

        // Getters and Setters
        public int getStatusCode() { return statusCode; }
        public void setStatusCode(int statusCode) { this.statusCode = statusCode; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getContentType() { return contentType; }
        public void setContentType(String contentType) { this.contentType = contentType; }

        public String getSchemaJson() { return schemaJson; }
        public void setSchemaJson(String schemaJson) { this.schemaJson = schemaJson; }

        public String getExampleJson() { return exampleJson; }
        public void setExampleJson(String exampleJson) { this.exampleJson = exampleJson; }
    }
}
