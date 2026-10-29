package com.avtech.pams.common;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON kept as text, so the same entity maps on PostgreSQL (text) and SQL Server (nvarchar(max)),
 * which have no common JSON column type.
 */
public final class JsonText {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private JsonText() {
    }

    @Converter
    public static class StringList implements AttributeConverter<List<String>, String> {
        @Override
        public String convertToDatabaseColumn(List<String> v) {
            return MAPPER.writeValueAsString(v == null ? List.of() : v);
        }

        @Override
        public List<String> convertToEntityAttribute(String s) {
            return s == null || s.isBlank() ? new ArrayList<>() : new ArrayList<>(MAPPER.readValue(s, new TypeReference<List<String>>() { }));
        }
    }

    @Converter
    public static class ObjectMap implements AttributeConverter<Map<String, Object>, String> {
        @Override
        public String convertToDatabaseColumn(Map<String, Object> v) {
            return v == null ? null : MAPPER.writeValueAsString(v);
        }

        @Override
        public Map<String, Object> convertToEntityAttribute(String s) {
            return s == null || s.isBlank() ? null : new LinkedHashMap<>(MAPPER.readValue(s, new TypeReference<Map<String, Object>>() { }));
        }
    }
}
