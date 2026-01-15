package com.docgen.model;

/**
 * Represents a single chunk of source code along with its
 * embedding vector produced by the Gemini text-embedding-004 model.
 *
 * Used by RagService to store indexed code and perform
 * cosine similarity searches during retrieval.
 */
public class CodeChunk {

    // The raw text content of this chunk (one method or block)
    private final String text;

    // 768-dimensional embedding vector from Gemini
    private final double[] embedding;

    // Human-readable label (e.g. "Chunk 3" or method name)
    private final String label;

    public CodeChunk(String text, double[] embedding, String label) {
        this.text = text;
        this.embedding = embedding;
        this.label = label;
    }

    public String getText() {
        return text;
    }

    public double[] getEmbedding() {
        return embedding;
    }

    public String getLabel() {
        return label;
    }
}
