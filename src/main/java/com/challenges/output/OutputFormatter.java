package com.challenges.output;

import com.challenges.json.JsonNode;
import org.eclipse.collections.api.tuple.Pair;

public class OutputFormatter {
    private final boolean prettyPrint;
    private final boolean colorOutput;
    private final boolean sortKeys;

    // StringBuilder pool for performance
    private static final ThreadLocal<StringBuilder> STRING_BUILDER_POOL =
        ThreadLocal.withInitial(() -> new StringBuilder(512));

    public OutputFormatter(boolean prettyPrint, boolean colorOutput) {
        this(prettyPrint, colorOutput, false);
    }

    public OutputFormatter(boolean prettyPrint, boolean colorOutput, boolean sortKeys) {
        this.prettyPrint = prettyPrint;
        this.colorOutput = colorOutput;
        this.sortKeys = sortKeys;
    }

    public String format(JsonNode node) {
        StringBuilder sb = STRING_BUILDER_POOL.get();
        sb.setLength(0); // Clear the builder

        if (prettyPrint) {
            formatPretty(node, 0, sb);
        } else {
            formatCompact(node, sb);
        }

        return sb.toString();
    }
    
    private void formatPretty(JsonNode node, int indent, StringBuilder sb) {
        String indentStr = " ".repeat(indent);

        switch (node) {
            case JsonNode.JsonObject obj -> {
                if (obj.fields().isEmpty()) {
                    sb.append("{}");
                    return;
                }

                sb.append("{\n");

                boolean first = true;
                var entries = sortKeys
                    ? obj.fields().keyValuesView().toSortedListBy(Pair::getOne)
                    : obj.fields().keyValuesView().toList();

                for (var entry : entries) {
                    if (!first) {
                        sb.append(",\n");
                    }
                    first = false;

                    sb.append(indentStr)
                      .append("  \"")
                      .append(entry.getOne())
                      .append("\": ");
                    formatPretty(entry.getTwo(), indent + 2, sb);
                }

                sb.append("\n").append(indentStr).append("}");
            }

            case JsonNode.JsonArray arr -> {
                if (arr.elements().isEmpty()) {
                    sb.append("[]");
                    return;
                }

                sb.append("[\n");

                boolean first = true;
                for (JsonNode element : arr.elements()) {
                    if (!first) {
                        sb.append(",\n");
                    }
                    first = false;

                    sb.append(indentStr).append("  ");
                    formatPretty(element, indent + 2, sb);
                }

                sb.append("\n").append(indentStr).append("]");
            }

            case JsonNode.JsonString s -> sb.append("\"").append(escapeString(s.value())).append("\"");
            case JsonNode.JsonNumber n -> sb.append(n.toJsonString());
            case JsonNode.JsonBoolean b -> sb.append(b.value());
            case JsonNode.JsonNull n -> sb.append("null");
        }
    }
    
    private void formatCompact(JsonNode node, StringBuilder sb) {
        switch (node) {
            case JsonNode.JsonObject obj -> {
                if (obj.fields().isEmpty()) {
                    sb.append("{}");
                    return;
                }

                sb.append("{");

                boolean first = true;
                var entries = sortKeys
                    ? obj.fields().keyValuesView().toSortedListBy(Pair::getOne)
                    : obj.fields().keyValuesView().toList();

                for (var entry : entries) {
                    if (!first) {
                        sb.append(",");
                    }
                    first = false;

                    sb.append("\"")
                      .append(entry.getOne())
                      .append("\":");
                    formatCompact(entry.getTwo(), sb);
                }

                sb.append("}");
            }

            case JsonNode.JsonArray arr -> {
                if (arr.elements().isEmpty()) {
                    sb.append("[]");
                    return;
                }

                sb.append("[");

                boolean first = true;
                for (JsonNode element : arr.elements()) {
                    if (!first) {
                        sb.append(",");
                    }
                    first = false;

                    formatCompact(element, sb);
                }

                sb.append("]");
            }

            case JsonNode.JsonString s -> sb.append("\"").append(escapeString(s.value())).append("\"");
            case JsonNode.JsonNumber n -> sb.append(n.toJsonString());
            case JsonNode.JsonBoolean b -> sb.append(b.value());
            case JsonNode.JsonNull n -> sb.append("null");
        }
    }
    
    private String escapeString(String s) {
        // Fast path: if no escaping needed, return original
        boolean needsEscaping = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' || c == '"' || c == '\n' || c == '\r' || c == '\t') {
                needsEscaping = true;
                break;
            }
        }

        if (!needsEscaping) {
            return s;
        }

        // Slow path: escape required characters using char array (no intermediate strings)
        StringBuilder result = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> result.append("\\\\");
                case '"'  -> result.append("\\\"");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default   -> result.append(c);
            }
        }
        return result.toString();
    }
}