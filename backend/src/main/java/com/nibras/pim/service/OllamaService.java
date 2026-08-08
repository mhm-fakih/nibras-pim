package com.nibras.pim.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Thin client for a locally-running Ollama instance (http://localhost:11434), used for the
 * semantic-duplicate scan: {@code nomic-embed-text} for cheap same-language similarity, and
 * {@code aya-expanse} (a multilingual model) for a true cross-language "same meaning" judgment.
 * Both models are optional — every call degrades to null/false if Ollama isn't running or a
 * model isn't pulled, so nothing else in the app depends on this being available.
 */
@Service
public class OllamaService {

    private static final Logger log = LoggerFactory.getLogger(OllamaService.class);
    private static final String BASE_URL = "http://localhost:11434";
    private static final String EMBED_MODEL = "nomic-embed-text";
    private static final String JUDGE_MODEL = "aya-expanse";

    private final RestClient client;

    public OllamaService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(90_000);   // aya-expanse on CPU can take tens of seconds per call
        this.client = RestClient.builder().baseUrl(BASE_URL).requestFactory(factory).build();
    }

    public record Status(boolean reachable, boolean hasEmbedModel, boolean hasJudgeModel) {
    }

    /** Whether Ollama is reachable and which of the two models it has pulled. */
    public Status status() {
        try {
            Map<?, ?> res = client.get().uri("/api/tags").retrieve().body(Map.class);
            List<?> models = (List<?>) res.get("models");
            boolean hasEmbed = false;
            boolean hasJudge = false;
            for (Object m : models) {
                String name = String.valueOf(((Map<?, ?>) m).get("name"));
                if (name.startsWith(EMBED_MODEL)) {
                    hasEmbed = true;
                }
                if (name.startsWith(JUDGE_MODEL)) {
                    hasJudge = true;
                }
            }
            return new Status(true, hasEmbed, hasJudge);
        } catch (RuntimeException e) {
            return new Status(false, false, false);
        }
    }

    /**
     * Embedding vectors for a batch of texts, in one round trip (much faster than one call per
     * text — ~200 short titles embed in well under 15s batched, vs. minutes one at a time).
     * Returns an empty list if Ollama/the model is unavailable; never partial.
     */
    public List<float[]> embedBatch(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        try {
            Map<?, ?> res = client.post().uri("/api/embed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", EMBED_MODEL, "input", texts))
                    .retrieve().body(Map.class);
            List<?> raw = (List<?>) res.get("embeddings");
            List<float[]> out = new java.util.ArrayList<>(raw.size());
            for (Object row : raw) {
                List<?> nums = (List<?>) row;
                float[] v = new float[nums.size()];
                for (int i = 0; i < v.length; i++) {
                    v[i] = ((Number) nums.get(i)).floatValue();
                }
                out.add(v);
            }
            return out;
        } catch (RuntimeException e) {
            log.warn("Ollama embedBatch failed for {} texts: {}", texts.size(), e.toString());
            return List.of();
        }
    }

    /**
     * Asks aya-expanse whether two texts (possibly in different languages) describe the same
     * underlying thing. Returns false on any failure — a conservative default so a flaky model
     * call never reports a false "these are duplicates".
     */
    public boolean sameMeaning(String a, String b) {
        String prompt = """
                You compare two short records from a personal database. They may be written in
                different languages (e.g. Arabic and English). Decide whether they describe the
                SAME underlying task, goal, or item, or a DIFFERENT one.

                A: "%s"
                B: "%s"

                Reply with exactly one word: SAME or DIFFERENT.
                """.formatted(a, b);
        try {
            Map<?, ?> res = client.post().uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", JUDGE_MODEL, "prompt", prompt, "stream", false))
                    .retrieve().body(Map.class);
            String out = String.valueOf(res.get("response")).trim().toUpperCase();
            return out.contains("SAME") && !out.contains("NOT SAME") && !out.contains("DIFFERENT");
        } catch (RuntimeException e) {
            log.warn("Ollama sameMeaning judgment failed: {}", e.toString());
            return false;
        }
    }
}
