package com.challenges;

import com.challenges.json.JsonNode;
import com.challenges.json.JjqJsonParser;
import com.challenges.output.OutputFormatter;
import com.challenges.query.QueryExecutor;
import com.challenges.query.QueryNode;
import com.challenges.query.QueryParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for jjq covering common jq usage patterns
 */
public class JjqComprehensiveTest {

    // ============================================================
    // Test Infrastructure
    // ============================================================

    private JsonNode parseAndExecute(String json, String query) throws IOException {
        return parseAndExecuteToList(json, query).stream().findFirst().orElse(null);
    }

    private List<JsonNode> parseAndExecuteToList(String json, String query) throws IOException {
        JjqJsonParser jsonParser = new JjqJsonParser();
        QueryParser queryParser = new QueryParser();
        QueryExecutor queryExecutor = new QueryExecutor();

        JsonNode jsonNode = jsonParser.parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        QueryNode queryNode = queryParser.parse(query);

        return queryExecutor.execute(queryNode, jsonNode).collect(Collectors.toList());
    }

    private String parseExecuteAndFormat(String json, String query, boolean compact) throws IOException {
        List<JsonNode> results = parseAndExecuteToList(json, query);
        OutputFormatter formatter = new OutputFormatter(!compact, false);
        StringBuilder sb = new StringBuilder();
        for (JsonNode result : results) {
            sb.append(formatter.format(result)).append("\n");
        }
        return sb.toString().trim();
    }

    private void assertJsonString(JsonNode node, String expected) {
        assertTrue(node instanceof JsonNode.JsonString, "Expected JsonString but got " + node.getClass().getSimpleName());
        assertEquals(expected, ((JsonNode.JsonString) node).value());
    }

    private void assertJsonNumber(JsonNode node, String expected) {
        assertTrue(node instanceof JsonNode.JsonNumber, "Expected JsonNumber but got " + node.getClass().getSimpleName());
        JsonNode.JsonNumber num = (JsonNode.JsonNumber) node;
        // Compare numeric values properly
        double expectedVal = Double.parseDouble(expected);
        double actualVal = num.numberValue().doubleValue();
        assertEquals(expectedVal, actualVal, 0.0001, "Expected " + expected + " but got " + actualVal);
    }

    private void assertJsonBoolean(JsonNode node, boolean expected) {
        assertTrue(node instanceof JsonNode.JsonBoolean, "Expected JsonBoolean but got " + node.getClass().getSimpleName());
        assertEquals(expected, ((JsonNode.JsonBoolean) node).value());
    }

    private void assertJsonNull(JsonNode node) {
        assertTrue(node instanceof JsonNode.JsonNull, "Expected JsonNull but got " + node.getClass().getSimpleName());
    }

    // ============================================================
    // CATEGORY 1: Basic Selectors (10-15 tests)
    // ============================================================

    @Test
    public void testIdentity() throws IOException {
        String json = "{\"name\":\"John\",\"age\":30}";
        JsonNode result = parseAndExecute(json, ".");
        assertTrue(result instanceof JsonNode.JsonObject);
        assertEquals(2, ((JsonNode.JsonObject) result).fields().size());
    }

    @Test
    public void testIdentityOnArray() throws IOException {
        String json = "[1,2,3]";
        JsonNode result = parseAndExecute(json, ".");
        assertTrue(result instanceof JsonNode.JsonArray);
        assertEquals(3, ((JsonNode.JsonArray) result).elements().size());
    }

    @Test
    public void testFieldAccess() throws IOException {
        String json = "{\"name\":\"John\",\"age\":30}";
        JsonNode result = parseAndExecute(json, ".name");
        assertJsonString(result, "John");
    }

    @Test
    public void testNestedFieldAccess() throws IOException {
        String json = "{\"user\":{\"profile\":{\"name\":\"John\"}}}";
        JsonNode result = parseAndExecute(json, ".user");
        assertTrue(result instanceof JsonNode.JsonObject);

        // Now test deeper nesting via pipe
        result = parseAndExecute(json, ".user | .profile | .name");
        assertJsonString(result, "John");
    }

    @Test
    public void testFieldAccessNonExistent() throws IOException {
        String json = "{\"name\":\"John\"}";
        JsonNode result = parseAndExecute(json, ".age");
        assertJsonNull(result);
    }

    @Test
    public void testArrayIndexPositive() throws IOException {
        String json = "[10,20,30,40,50]";
        JsonNode result = parseAndExecute(json, ".[0]");
        assertJsonNumber(result, "10");

        result = parseAndExecute(json, ".[2]");
        assertJsonNumber(result, "30");

        result = parseAndExecute(json, ".[4]");
        assertJsonNumber(result, "50");
    }

    @Test
    public void testArrayIndexNegative() throws IOException {
        String json = "[10,20,30,40,50]";
        JsonNode result = parseAndExecute(json, ".[-1]");
        assertJsonNumber(result, "50");

        result = parseAndExecute(json, ".[-2]");
        assertJsonNumber(result, "40");
    }

    @Test
    public void testArrayIndexOutOfBounds() throws IOException {
        String json = "[10,20,30]";
        JsonNode result = parseAndExecute(json, ".[10]");
        assertJsonNull(result);

        result = parseAndExecute(json, ".[-10]");
        assertJsonNull(result);
    }

    @Test
    public void testArraySliceBasic() throws IOException {
        String json = "[0,1,2,3,4,5,6,7,8,9]";
        JsonNode result = parseAndExecute(json, ".[2:5]");
        assertTrue(result instanceof JsonNode.JsonArray);
        JsonNode.JsonArray arr = (JsonNode.JsonArray) result;
        assertEquals(3, arr.elements().size());
        assertJsonNumber(arr.elements().get(0), "2");
        assertJsonNumber(arr.elements().get(1), "3");
        assertJsonNumber(arr.elements().get(2), "4");
    }

    @Test
    public void testArraySliceFromStart() throws IOException {
        String json = "[0,1,2,3,4]";
        JsonNode result = parseAndExecute(json, ".[:3]");
        assertTrue(result instanceof JsonNode.JsonArray);
        JsonNode.JsonArray arr = (JsonNode.JsonArray) result;
        assertEquals(3, arr.elements().size());
        assertJsonNumber(arr.elements().get(0), "0");
        assertJsonNumber(arr.elements().get(2), "2");
    }

    @Test
    public void testArraySliceToEnd() throws IOException {
        String json = "[0,1,2,3,4]";
        JsonNode result = parseAndExecute(json, ".[2:]");
        assertTrue(result instanceof JsonNode.JsonArray);
        JsonNode.JsonArray arr = (JsonNode.JsonArray) result;
        assertEquals(3, arr.elements().size());
        assertJsonNumber(arr.elements().get(0), "2");
        assertJsonNumber(arr.elements().get(2), "4");
    }

    @Test
    public void testArraySliceWithStep() throws IOException {
        String json = "[0,1,2,3,4,5,6,7,8,9]";
        JsonNode result = parseAndExecute(json, ".[::2]");
        assertTrue(result instanceof JsonNode.JsonArray);
        JsonNode.JsonArray arr = (JsonNode.JsonArray) result;
        assertEquals(5, arr.elements().size());
        assertJsonNumber(arr.elements().get(0), "0");
        assertJsonNumber(arr.elements().get(1), "2");
        assertJsonNumber(arr.elements().get(2), "4");
    }

    @Test
    public void testArraySliceWithStepAndRange() throws IOException {
        String json = "[0,1,2,3,4,5,6,7,8,9]";
        JsonNode result = parseAndExecute(json, ".[1:8:2]");
        assertTrue(result instanceof JsonNode.JsonArray);
        JsonNode.JsonArray arr = (JsonNode.JsonArray) result;
        assertEquals(4, arr.elements().size());
        assertJsonNumber(arr.elements().get(0), "1");
        assertJsonNumber(arr.elements().get(1), "3");
        assertJsonNumber(arr.elements().get(2), "5");
        assertJsonNumber(arr.elements().get(3), "7");
    }

    // ============================================================
    // CATEGORY 2: Array Operations (10-15 tests)
    // ============================================================

    @Test
    public void testArrayIterator() throws IOException {
        String json = "[1,2,3]";
        List<JsonNode> results = parseAndExecuteToList(json, ".[]");
        assertEquals(3, results.size());
        assertJsonNumber(results.get(0), "1");
        assertJsonNumber(results.get(1), "2");
        assertJsonNumber(results.get(2), "3");
    }

    @Test
    public void testArrayIteratorOnObject() throws IOException {
        String json = "{\"a\":1,\"b\":2,\"c\":3}";
        List<JsonNode> results = parseAndExecuteToList(json, ".[]");
        assertEquals(3, results.size());
        // Note: order may vary, but all values should be present
        assertTrue(results.stream().anyMatch(n -> n instanceof JsonNode.JsonNumber &&
                   ((JsonNode.JsonNumber) n).numberValue().longValue() == 1L));
    }

    @Test
    public void testArrayIteratorWithFieldAccess() throws IOException {
        String json = "[{\"name\":\"John\"},{\"name\":\"Jane\"},{\"name\":\"Bob\"}]";
        List<JsonNode> results = parseAndExecuteToList(json, ".[].name");
        assertEquals(3, results.size());
        assertJsonString(results.get(0), "John");
        assertJsonString(results.get(1), "Jane");
        assertJsonString(results.get(2), "Bob");
    }

    @Test
    public void testMapSimple() throws IOException {
        String json = "[1,2,3]";
        List<JsonNode> results = parseAndExecuteToList(json, "map(.)");
        assertEquals(3, results.size());
        assertJsonNumber(results.get(0), "1");
        assertJsonNumber(results.get(1), "2");
        assertJsonNumber(results.get(2), "3");
    }

    @Test
    public void testMapWithFieldAccess() throws IOException {
        String json = "[{\"name\":\"John\",\"age\":30},{\"name\":\"Jane\",\"age\":25}]";
        List<JsonNode> results = parseAndExecuteToList(json, "map(.name)");
        assertEquals(2, results.size());
        assertJsonString(results.get(0), "John");
        assertJsonString(results.get(1), "Jane");
    }

    @Test
    public void testMapWithNestedAccess() throws IOException {
        String json = "[{\"user\":{\"name\":\"John\"}},{\"user\":{\"name\":\"Jane\"}}]";
        List<JsonNode> results = parseAndExecuteToList(json, "map(.user | .name)");
        assertEquals(2, results.size());
        assertJsonString(results.get(0), "John");
        assertJsonString(results.get(1), "Jane");
    }

    @Test
    public void testSelectWithFieldValue() throws IOException {
        String json = "[{\"name\":\"John\",\"active\":true},{\"name\":\"Jane\",\"active\":false},{\"name\":\"Bob\",\"active\":true}]";
        List<JsonNode> results = parseAndExecuteToList(json, ".[] | select(.active)");
        assertEquals(2, results.size());
        assertTrue(results.get(0) instanceof JsonNode.JsonObject);
        assertEquals("John", ((JsonNode.JsonString)((JsonNode.JsonObject) results.get(0)).fields().get("name")).value());
        assertEquals("Bob", ((JsonNode.JsonString)((JsonNode.JsonObject) results.get(1)).fields().get("name")).value());
    }

    @Test
    public void testSelectWithNullCheck() throws IOException {
        String json = "[{\"name\":\"John\"},{\"name\":null},{\"age\":30}]";
        List<JsonNode> results = parseAndExecuteToList(json, ".[] | select(.name)");
        // Should select objects that have a non-null name field
        assertEquals(1, results.size());
    }

    @Test
    public void testEmptyArray() throws IOException {
        String json = "[]";
        List<JsonNode> results = parseAndExecuteToList(json, ".[]");
        assertEquals(0, results.size());
    }

    @Test
    public void testMapOnEmptyArray() throws IOException {
        String json = "[]";
        List<JsonNode> results = parseAndExecuteToList(json, "map(.)");
        assertEquals(0, results.size());
    }

    // ============================================================
    // CATEGORY 3: Object Operations (8-10 tests)
    // ============================================================

    @Test
    public void testObjectFieldIteration() throws IOException {
        String json = "{\"name\":\"John\",\"age\":30,\"city\":\"NYC\"}";
        List<JsonNode> results = parseAndExecuteToList(json, ".[]");
        assertEquals(3, results.size());
        // Values should be extracted (order may vary)
    }

    @Test
    public void testNestedObjectAccess() throws IOException {
        String json = "{\"user\":{\"profile\":{\"email\":\"john@example.com\"}}}";
        JsonNode result = parseAndExecute(json, ".user | .profile | .email");
        assertJsonString(result, "john@example.com");
    }

    @Test
    public void testObjectWithArrayField() throws IOException {
        String json = "{\"users\":[{\"name\":\"John\"},{\"name\":\"Jane\"}]}";
        List<JsonNode> results = parseAndExecuteToList(json, ".users | .[] | .name");
        assertEquals(2, results.size());
        assertJsonString(results.get(0), "John");
        assertJsonString(results.get(1), "Jane");
    }

    @Test
    public void testMultipleFieldAccess() throws IOException {
        String json = "{\"first\":\"John\",\"last\":\"Doe\",\"age\":30}";
        JsonNode first = parseAndExecute(json, ".first");
        JsonNode last = parseAndExecute(json, ".last");
        JsonNode age = parseAndExecute(json, ".age");

        assertJsonString(first, "John");
        assertJsonString(last, "Doe");
        assertJsonNumber(age, "30");
    }

    @Test
    public void testObjectNullFieldAccess() throws IOException {
        String json = "{\"name\":\"John\",\"email\":null}";
        JsonNode result = parseAndExecute(json, ".email");
        assertJsonNull(result);
    }

    @Test
    public void testDeepNestedObject() throws IOException {
        String json = "{\"a\":{\"b\":{\"c\":{\"d\":{\"e\":\"deep\"}}}}}";
        JsonNode result = parseAndExecute(json, ".a | .b | .c | .d | .e");
        assertJsonString(result, "deep");
    }

    @Test
    public void testObjectWithMixedTypes() throws IOException {
        String json = "{\"string\":\"text\",\"number\":42,\"boolean\":true,\"null\":null,\"array\":[1,2],\"object\":{\"nested\":\"value\"}}";

        assertJsonString(parseAndExecute(json, ".string"), "text");
        assertJsonNumber(parseAndExecute(json, ".number"), "42");
        assertJsonBoolean(parseAndExecute(json, ".boolean"), true);
        assertJsonNull(parseAndExecute(json, ".null"));
        assertTrue(parseAndExecute(json, ".array") instanceof JsonNode.JsonArray);
        assertTrue(parseAndExecute(json, ".object") instanceof JsonNode.JsonObject);
    }

    // ============================================================
    // CATEGORY 4: Pipe Combinations (10-15 tests)
    // ============================================================

    @Test
    public void testSimplePipe() throws IOException {
        String json = "{\"user\":{\"name\":\"John\"}}";
        JsonNode result = parseAndExecute(json, ".user | .name");
        assertJsonString(result, "John");
    }

    @Test
    public void testTriplePipe() throws IOException {
        String json = "{\"data\":{\"user\":{\"profile\":{\"name\":\"John\"}}}}";
        JsonNode result = parseAndExecute(json, ".data | .user | .profile | .name");
        assertJsonString(result, "John");
    }

    @Test
    public void testPipeWithArrayIndex() throws IOException {
        String json = "{\"users\":[{\"name\":\"John\"},{\"name\":\"Jane\"}]}";
        JsonNode result = parseAndExecute(json, ".users | .[0] | .name");
        assertJsonString(result, "John");
    }

    @Test
    public void testPipeWithArrayIterator() throws IOException {
        String json = "{\"items\":[{\"id\":1},{\"id\":2},{\"id\":3}]}";
        List<JsonNode> results = parseAndExecuteToList(json, ".items | .[] | .id");
        assertEquals(3, results.size());
        assertJsonNumber(results.get(0), "1");
        assertJsonNumber(results.get(1), "2");
        assertJsonNumber(results.get(2), "3");
    }

    @Test
    public void testPipeWithMap() throws IOException {
        String json = "{\"data\":[{\"value\":1},{\"value\":2}]}";
        List<JsonNode> results = parseAndExecuteToList(json, ".data | map(.value)");
        assertEquals(2, results.size());
        assertJsonNumber(results.get(0), "1");
        assertJsonNumber(results.get(1), "2");
    }

    @Test
    public void testPipeWithSelect() throws IOException {
        String json = "{\"users\":[{\"name\":\"John\",\"active\":true},{\"name\":\"Jane\",\"active\":false}]}";
        List<JsonNode> results = parseAndExecuteToList(json, ".users | .[] | select(.active) | .name");
        assertEquals(1, results.size());
        assertJsonString(results.get(0), "John");
    }

    @Test
    public void testComplexPipeChain() throws IOException {
        String json = "{\"data\":{\"users\":[{\"profile\":{\"name\":\"John\",\"active\":true}},{\"profile\":{\"name\":\"Jane\",\"active\":false}}]}}";
        List<JsonNode> results = parseAndExecuteToList(json, ".data | .users | .[] | .profile | select(.active) | .name");
        assertEquals(1, results.size());
        assertJsonString(results.get(0), "John");
    }

    @Test
    public void testPipeWithSlice() throws IOException {
        String json = "{\"numbers\":[0,1,2,3,4,5,6,7,8,9]}";
        JsonNode result = parseAndExecute(json, ".numbers | .[2:5]");
        assertTrue(result instanceof JsonNode.JsonArray);
        assertEquals(3, ((JsonNode.JsonArray) result).elements().size());
    }

    @Test
    public void testMapWithPipeInside() throws IOException {
        String json = "[{\"user\":{\"name\":\"John\"}},{\"user\":{\"name\":\"Jane\"}}]";
        List<JsonNode> results = parseAndExecuteToList(json, "map(.user | .name)");
        assertEquals(2, results.size());
        assertJsonString(results.get(0), "John");
        assertJsonString(results.get(1), "Jane");
    }

    // ============================================================
    // CATEGORY 5: Edge Cases & Error Handling (10-15 tests)
    // ============================================================

    @Test
    public void testNullPropagation() throws IOException {
        String json = "{\"user\":null}";
        JsonNode result = parseAndExecute(json, ".user | .name");
        assertJsonNull(result);
    }

    @Test
    public void testNullPropagationDeep() throws IOException {
        String json = "{\"a\":{\"b\":null}}";
        JsonNode result = parseAndExecute(json, ".a | .b | .c | .d");
        assertJsonNull(result);
    }

    @Test
    public void testArrayIndexOnObject() throws IOException {
        String json = "{\"name\":\"John\"}";
        JsonNode result = parseAndExecute(json, ".[0]");
        assertJsonNull(result);
    }

    @Test
    public void testFieldAccessOnArray() throws IOException {
        String json = "[1,2,3]";
        JsonNode result = parseAndExecute(json, ".name");
        assertJsonNull(result);
    }

    @Test
    public void testFieldAccessOnPrimitive() throws IOException {
        String json = "\"hello\"";
        JsonNode result = parseAndExecute(json, ".field");
        assertJsonNull(result);
    }

    @Test
    public void testArrayIteratorOnPrimitive() throws IOException {
        String json = "42";
        List<JsonNode> results = parseAndExecuteToList(json, ".[]");
        assertEquals(0, results.size());
    }

    @Test
    public void testSelectOnEmptyArray() throws IOException {
        String json = "[]";
        List<JsonNode> results = parseAndExecuteToList(json, ".[] | select(.active)");
        assertEquals(0, results.size());
    }

    @Test
    public void testMapOnNonArray() throws IOException {
        String json = "{\"name\":\"John\"}";
        List<JsonNode> results = parseAndExecuteToList(json, "map(.name)");
        // map on non-array should return empty or handle gracefully
        assertEquals(0, results.size());
    }

    @Test
    public void testVeryDeepNesting() throws IOException {
        String json = "{\"l1\":{\"l2\":{\"l3\":{\"l4\":{\"l5\":{\"l6\":{\"l7\":{\"l8\":{\"l9\":{\"l10\":\"deep\"}}}}}}}}}}";
        JsonNode result = parseAndExecute(json, ".l1 | .l2 | .l3 | .l4 | .l5 | .l6 | .l7 | .l8 | .l9 | .l10");
        assertJsonString(result, "deep");
    }

    @Test
    public void testEmptyObject() throws IOException {
        String json = "{}";
        JsonNode result = parseAndExecute(json, ".");
        assertTrue(result instanceof JsonNode.JsonObject);
        assertEquals(0, ((JsonNode.JsonObject) result).fields().size());
    }

    @Test
    public void testEmptyString() throws IOException {
        String json = "\"\"";
        JsonNode result = parseAndExecute(json, ".");
        assertJsonString(result, "");
    }

    @Test
    public void testZeroNumber() throws IOException {
        String json = "0";
        JsonNode result = parseAndExecute(json, ".");
        assertJsonNumber(result, "0");
    }

    @Test
    public void testNegativeNumber() throws IOException {
        String json = "-42.5";
        JsonNode result = parseAndExecute(json, ".");
        assertJsonNumber(result, "-42.5");
    }

    @Test
    public void testBooleanTrue() throws IOException {
        String json = "true";
        JsonNode result = parseAndExecute(json, ".");
        assertJsonBoolean(result, true);
    }

    @Test
    public void testBooleanFalse() throws IOException {
        String json = "false";
        JsonNode result = parseAndExecute(json, ".");
        assertJsonBoolean(result, false);
    }

    @Test
    public void testNullValue() throws IOException {
        String json = "null";
        JsonNode result = parseAndExecute(json, ".");
        assertJsonNull(result);
    }

    // ============================================================
    // CATEGORY 6: Real-World Use Cases (8-10 tests)
    // ============================================================

    @Test
    public void testPackageJsonDependencies() throws IOException {
        String json = """
            {
              "name": "my-project",
              "version": "1.0.0",
              "dependencies": {
                "express": "^4.17.1",
                "lodash": "^4.17.21",
                "react": "^18.0.0"
              }
            }
            """;
        JsonNode result = parseAndExecute(json, ".dependencies");
        assertTrue(result instanceof JsonNode.JsonObject);
        JsonNode.JsonObject deps = (JsonNode.JsonObject) result;
        assertEquals(3, deps.fields().size());
        assertTrue(deps.fields().containsKey("express"));
    }

    @Test
    public void testAPIResponseFiltering() throws IOException {
        String json = """
            {
              "status": "success",
              "data": {
                "users": [
                  {"id": 1, "email": "john@example.com", "active": true},
                  {"id": 2, "email": "jane@example.com", "active": false},
                  {"id": 3, "email": "bob@example.com", "active": true}
                ]
              }
            }
            """;
        List<JsonNode> results = parseAndExecuteToList(json, ".data | .users | .[] | select(.active) | .email");
        assertEquals(2, results.size());
        assertJsonString(results.get(0), "john@example.com");
        assertJsonString(results.get(1), "bob@example.com");
    }

    @Test
    public void testConfigFileExtraction() throws IOException {
        String json = """
            {
              "server": {
                "port": 8080,
                "host": "localhost"
              },
              "database": {
                "connection": {
                  "host": "db.example.com",
                  "port": 5432,
                  "name": "mydb"
                }
              }
            }
            """;
        JsonNode result = parseAndExecute(json, ".database | .connection | .host");
        assertJsonString(result, "db.example.com");
    }

    @Test
    public void testLogProcessing() throws IOException {
        String json = """
            [
              {"timestamp": "2024-01-01T10:00:00", "level": "info", "message": "Server started"},
              {"timestamp": "2024-01-01T10:05:00", "level": "error", "message": "Database connection failed"},
              {"timestamp": "2024-01-01T10:10:00", "level": "error", "message": "Authentication failed"},
              {"timestamp": "2024-01-01T10:15:00", "level": "info", "message": "Request processed"}
            ]
            """;
        List<JsonNode> results = parseAndExecuteToList(json, ".[] | select(.level) | .message");
        // All have level, so all should be selected
        assertEquals(4, results.size());
    }

    @Test
    public void testNestedArrayExtraction() throws IOException {
        String json = """
            {
              "company": {
                "departments": [
                  {
                    "name": "Engineering",
                    "employees": [
                      {"name": "Alice", "role": "Developer"},
                      {"name": "Bob", "role": "Manager"}
                    ]
                  },
                  {
                    "name": "Sales",
                    "employees": [
                      {"name": "Charlie", "role": "Rep"}
                    ]
                  }
                ]
              }
            }
            """;
        List<JsonNode> results = parseAndExecuteToList(json, ".company | .departments | .[0] | .employees | .[] | .name");
        assertEquals(2, results.size());
        assertJsonString(results.get(0), "Alice");
        assertJsonString(results.get(1), "Bob");
    }

    @Test
    public void testArrayOfPrimitives() throws IOException {
        String json = """
            {
              "tags": ["javascript", "java", "python", "go"]
            }
            """;
        List<JsonNode> results = parseAndExecuteToList(json, ".tags | .[]");
        assertEquals(4, results.size());
        assertJsonString(results.get(0), "javascript");
        assertJsonString(results.get(3), "go");
    }

    @Test
    public void testComplexNestedQuery() throws IOException {
        String json = """
            {
              "results": [
                {
                  "user": {"name": "John", "age": 30},
                  "scores": [85, 90, 88]
                },
                {
                  "user": {"name": "Jane", "age": 25},
                  "scores": [92, 88, 95]
                }
              ]
            }
            """;
        List<JsonNode> results = parseAndExecuteToList(json, ".results | .[] | .user | .name");
        assertEquals(2, results.size());
        assertJsonString(results.get(0), "John");
        assertJsonString(results.get(1), "Jane");
    }

    @Test
    public void testArraySlicingInRealWorld() throws IOException {
        String json = """
            {
              "items": [
                {"id": 1, "name": "Item 1"},
                {"id": 2, "name": "Item 2"},
                {"id": 3, "name": "Item 3"},
                {"id": 4, "name": "Item 4"},
                {"id": 5, "name": "Item 5"}
              ]
            }
            """;
        JsonNode result = parseAndExecute(json, ".items | .[1:3]");
        assertTrue(result instanceof JsonNode.JsonArray);
        JsonNode.JsonArray arr = (JsonNode.JsonArray) result;
        assertEquals(2, arr.elements().size());
    }

    // ============================================================
    // CATEGORY 7: Output Format Tests (5-8 tests)
    // ============================================================

    @Test
    public void testCompactOutput() throws IOException {
        String json = "{\"name\":\"John\",\"age\":30}";
        String result = parseExecuteAndFormat(json, ".", true);
        assertFalse(result.contains("\n  ")); // No indentation
        assertTrue(result.contains("\"name\""));
    }

    @Test
    public void testPrettyOutput() throws IOException {
        String json = "{\"name\":\"John\",\"age\":30}";
        String result = parseExecuteAndFormat(json, ".", false);
        assertTrue(result.contains("\n")); // Should have newlines
    }

    @Test
    public void testArrayOutputFormatting() throws IOException {
        String json = "[1,2,3]";
        String compact = parseExecuteAndFormat(json, ".", true);
        String pretty = parseExecuteAndFormat(json, ".", false);

        assertFalse(compact.contains("\n  "));
        assertTrue(pretty.length() >= compact.length());
    }

    @Test
    public void testNestedObjectFormatting() throws IOException {
        String json = "{\"user\":{\"profile\":{\"name\":\"John\"}}}";
        String result = parseExecuteAndFormat(json, ".", false);
        assertTrue(result.contains("\"user\""));
        assertTrue(result.contains("\"profile\""));
        assertTrue(result.contains("\"name\""));
    }

    @Test
    public void testMultipleResultsFormatting() throws IOException {
        String json = "[1,2,3]";
        String result = parseExecuteAndFormat(json, ".[]", false);
        String[] lines = result.split("\n");
        assertEquals(3, lines.length);
    }

    @Test
    public void testStringValueFormatting() throws IOException {
        String json = "{\"message\":\"Hello, World!\"}";
        String result = parseExecuteAndFormat(json, ".message", false);
        assertTrue(result.contains("\"Hello, World!\""));
    }

    @Test
    public void testNumberFormatting() throws IOException {
        String json = "{\"pi\":3.14159,\"count\":42}";
        JsonNode pi = parseAndExecute(json, ".pi");
        JsonNode count = parseAndExecute(json, ".count");

        OutputFormatter formatter = new OutputFormatter(false, false);
        String piStr = formatter.format(pi);
        String countStr = formatter.format(count);

        assertTrue(piStr.contains("3.14159"));
        assertTrue(countStr.contains("42"));
    }

    @Test
    public void testBooleanAndNullFormatting() throws IOException {
        String json = "{\"active\":true,\"deleted\":false,\"metadata\":null}";
        OutputFormatter formatter = new OutputFormatter(false, false);

        String activeStr = formatter.format(parseAndExecute(json, ".active"));
        String deletedStr = formatter.format(parseAndExecute(json, ".deleted"));
        String metadataStr = formatter.format(parseAndExecute(json, ".metadata"));

        assertEquals("true", activeStr.trim());
        assertEquals("false", deletedStr.trim());
        assertEquals("null", metadataStr.trim());
    }
}
