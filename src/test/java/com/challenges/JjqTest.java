package com.challenges;

import com.challenges.json.JsonNode;
import com.challenges.json.JjqJsonParser;
import com.challenges.query.QueryExecutor;
import com.challenges.query.QueryNode;
import com.challenges.query.QueryParser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class JjqTest {

    @Test
    public void testIdentityFilter() throws IOException {
        String json = "{\"name\":\"John\",\"age\":30}";
        JsonNode result = parseAndExecute(json, ".");

        assertTrue(result instanceof JsonNode.JsonObject);
        JsonNode.JsonObject obj = (JsonNode.JsonObject) result;
        assertEquals(2, obj.fields().size());
        assertEquals(new JsonNode.JsonString("John"), obj.fields().get("name"));
        assertEquals(JsonNode.JsonNumber.of(30L), obj.fields().get("age"));
    }
    
    @Test
    public void testFieldAccess() throws IOException {
        String json = "{\"name\":\"John\",\"age\":30}";
        JsonNode result = parseAndExecute(json, ".name");
        
        assertTrue(result instanceof JsonNode.JsonString);
        assertEquals("John", ((JsonNode.JsonString) result).value());
    }
    
    @Test
    public void testArrayIndex() throws IOException {
        String json = "[10, 20, 30, 40]";
        JsonNode result = parseAndExecute(json, ".[2]");

        assertTrue(result instanceof JsonNode.JsonNumber);
        assertEquals(30L, ((JsonNode.JsonNumber) result).numberValue().longValue());
    }
    
    @Test
    public void testPipe() throws IOException {
        String json = "{\"user\":{\"name\":\"John\",\"age\":30}}";
        JsonNode result = parseAndExecute(json, ".user | .name");
        
        assertTrue(result instanceof JsonNode.JsonString);
        assertEquals("John", ((JsonNode.JsonString) result).value());
    }
    
    @Test
    public void testMap() throws IOException {
        String json = "[{\"name\":\"John\"},{\"name\":\"Jane\"}]";
        List<JsonNode> results = parseAndExecuteToList(json, "map(.name)");
        
        assertEquals(2, results.size());
        assertTrue(results.get(0) instanceof JsonNode.JsonString);
        assertEquals("John", ((JsonNode.JsonString) results.get(0)).value());
        assertTrue(results.get(1) instanceof JsonNode.JsonString);
        assertEquals("Jane", ((JsonNode.JsonString) results.get(1)).value());
    }

    @Test
    public void testArrayIteratorAndField() throws IOException {
        String json = "[{\"name\":\"John\"},{\"name\":\"Jane\"}]";
        List<JsonNode> results = parseAndExecuteToList(json, ".[].name");

        assertEquals(2, results.size());
        assertTrue(results.get(0) instanceof JsonNode.JsonString);
        assertEquals("John", ((JsonNode.JsonString) results.get(0)).value());
        assertTrue(results.get(1) instanceof JsonNode.JsonString);
        assertEquals("Jane", ((JsonNode.JsonString) results.get(1)).value());
    }
    
    private JsonNode parseAndExecute(String json, String query) throws IOException {
        JjqJsonParser jsonParser = new JjqJsonParser();
        QueryParser queryParser = new QueryParser();
        QueryExecutor queryExecutor = new QueryExecutor();
        
        JsonNode jsonNode = jsonParser.parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        QueryNode queryNode = queryParser.parse(query);
        
        return queryExecutor.execute(queryNode, jsonNode).findFirst().orElse(null);
    }
    
    private List<JsonNode> parseAndExecuteToList(String json, String query) throws IOException {
        JjqJsonParser jsonParser = new JjqJsonParser();
        QueryParser queryParser = new QueryParser();
        QueryExecutor queryExecutor = new QueryExecutor();
        
        JsonNode jsonNode = jsonParser.parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        QueryNode queryNode = queryParser.parse(query);
        
        return queryExecutor.execute(queryNode, jsonNode).collect(Collectors.toList());
    }
}