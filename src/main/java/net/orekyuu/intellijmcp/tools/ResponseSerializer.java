package net.orekyuu.intellijmcp.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utility class for serializing tool responses to JSON.
 */
public final class ResponseSerializer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ResponseSerializer() {
        // Utility class
    }

    /**
     * Serializes a response object to JSON string.
     * If the response is already a String, returns it as-is.
     *
     * @param response the response object to serialize
     * @return JSON string representation
     */
    public static String serialize(Object response) {
        if (response instanceof String str) {
            return str;
        }
        try {
            return MAPPER.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"Failed to serialize response: " + e.getMessage() + "\"}";
        }
    }
}
