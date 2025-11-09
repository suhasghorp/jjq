package com.challenges.json;

import org.eclipse.collections.api.map.MutableMap;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.factory.Maps;
import org.eclipse.collections.impl.factory.Lists;

public sealed interface JsonNode {
    record JsonObject(MutableMap<String, JsonNode> fields) implements JsonNode {
        public static JsonObject empty() {
            return new JsonObject(Maps.mutable.empty());
        }

        public JsonObject with(String key, JsonNode value) {
            MutableMap<String, JsonNode> newFields = Maps.mutable.ofMap(fields);
            newFields.put(key, value);
            return new JsonObject(newFields);
        }
    }

    record JsonArray(MutableList<JsonNode> elements) implements JsonNode {
        public static JsonArray empty() {
            return new JsonArray(Lists.mutable.empty());
        }

        public JsonArray with(JsonNode element) {
            MutableList<JsonNode> newElements = Lists.mutable.ofAll(elements);
            newElements.add(element);
            return new JsonArray(newElements);
        }
    }

    record JsonString(String value) implements JsonNode {}

    // Optimized number representation using primitives instead of BigDecimal
    sealed interface JsonNumber extends JsonNode {
        String toJsonString();
        Number numberValue(); // For tests and interop

        record JsonLong(long value) implements JsonNumber {
            @Override
            public String toJsonString() {
                return Long.toString(value);
            }

            @Override
            public Number numberValue() {
                return value;
            }
        }

        record JsonDouble(double value) implements JsonNumber {
            @Override
            public String toJsonString() {
                // Fast path for whole numbers (common case)
                if (value == (long) value && !Double.isInfinite(value) && !Double.isNaN(value)) {
                    return Long.toString((long) value);
                }
                // Use faster conversion for doubles
                return Double.toString(value);
            }

            @Override
            public Number numberValue() {
                return value;
            }
        }

        static JsonNumber of(long value) {
            return new JsonLong(value);
        }

        static JsonNumber of(double value) {
            return new JsonDouble(value);
        }
    }

    record JsonBoolean(boolean value) implements JsonNode {}
    record JsonNull() implements JsonNode {}
}