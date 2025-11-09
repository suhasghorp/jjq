package com.challenges.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.eclipse.collections.impl.factory.Maps;
import org.eclipse.collections.impl.factory.Lists;

import java.io.IOException;
import java.io.InputStream;

public class JjqJsonParser {
    private final JsonFactory factory = new JsonFactory();

    public JsonNode parse(InputStream input) throws IOException {
        try (JsonParser parser = factory.createParser(input)) {
            return parseValue(parser);
        }
    }

    private JsonNode parseValue(JsonParser parser) throws IOException {
        JsonToken token = parser.nextToken();

        return switch (token) {
            case START_OBJECT -> parseObject(parser);
            case START_ARRAY -> parseArray(parser);
            case VALUE_STRING -> new JsonNode.JsonString(parser.getText());
            case VALUE_NUMBER_INT -> JsonNode.JsonNumber.of(parser.getLongValue());
            case VALUE_NUMBER_FLOAT -> JsonNode.JsonNumber.of(parser.getDoubleValue());
            case VALUE_TRUE -> new JsonNode.JsonBoolean(true);
            case VALUE_FALSE -> new JsonNode.JsonBoolean(false);
            case VALUE_NULL -> new JsonNode.JsonNull();
            default -> throw new IOException("Unexpected JSON token: " + token);
        };
    }
    
    private JsonNode.JsonObject parseObject(JsonParser parser) throws IOException {
        var fields = Maps.mutable.<String, JsonNode>empty();
        
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String fieldName = parser.getCurrentName();
            JsonNode value = parseValue(parser);
            fields.put(fieldName, value);
        }
        
        return new JsonNode.JsonObject(fields);
    }
    
    private JsonNode.JsonArray parseArray(JsonParser parser) throws IOException {
        var elements = Lists.mutable.<JsonNode>empty();

        while (true) {
            JsonToken token = parser.nextToken();
            if (token == JsonToken.END_ARRAY) {
                break;
            }
            switch (token) {
                case START_OBJECT -> elements.add(parseObject(parser));
                case START_ARRAY -> elements.add(parseArray(parser));
                case VALUE_STRING -> elements.add(new JsonNode.JsonString(parser.getText()));
                case VALUE_NUMBER_INT -> elements.add(JsonNode.JsonNumber.of(parser.getLongValue()));
                case VALUE_NUMBER_FLOAT -> elements.add(JsonNode.JsonNumber.of(parser.getDoubleValue()));
                case VALUE_TRUE -> elements.add(new JsonNode.JsonBoolean(true));
                case VALUE_FALSE -> elements.add(new JsonNode.JsonBoolean(false));
                case VALUE_NULL -> elements.add(new JsonNode.JsonNull());
                default -> throw new IOException("Unexpected JSON token: " + token);
            }
        }

        return new JsonNode.JsonArray(elements);
    }
}