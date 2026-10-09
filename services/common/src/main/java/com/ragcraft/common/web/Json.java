package com.ragcraft.common.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

/**
 * Small helper for columns that store JSON text (for example starter questions or
 * channel settings). Kept portable across PostgreSQL and the H2 local profile.
 */
public final class Json {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Json() {}

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Value cannot be stored as JSON.", ex);
        }
    }

    public static <T> T read(String json, Class<T> type, T fallback) {
        if (json == null || json.isBlank()) return fallback;
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException ex) {
            return fallback;
        }
    }

    public static List<String> readStrings(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }
}
