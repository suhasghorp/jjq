package com.challenges;

import com.challenges.json.JjqJsonParser;
import com.challenges.json.JsonNode;
import com.challenges.query.QueryExecutor;
import com.challenges.query.QueryNode;
import com.challenges.query.QueryParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests inspired by https://nooshu.com/blog/2020/10/09/using-jq-with-the-webpagetest-api/
 * These tests first try to hit the live WebPageTest API for a known, historical test result
 * (so no API key is required). If the live call fails (no network, API down), they fall back
 * to a representative fixture to keep tests stable.
 */
public class WebPageTestJqExamplesTest {

    private static byte[] cachedJsonBytes;

    @BeforeAll
    static void fetchLiveIfPossible() {
        // Known historical result used in the blog-style examples
        String testId = System.getProperty("WPT_TEST_ID", "210910_AiDc3");
        String url = "https://www.webpagetest.org/jsonResult.php?test=" + testId;
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("User-Agent", "jjq-test/1.0")
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() == 200 && resp.body() != null && resp.body().length > 0) {
                // Basic sanity: response should contain the testId
                String body = new String(resp.body(), StandardCharsets.UTF_8);
                if (body.contains(testId)) {
                    cachedJsonBytes = resp.body();
                }
            }
        } catch (Exception ignored) {
            // We'll fallback to the fixture in individual tests
        }
    }

    private InputStream openLiveOrFixture() {
        if (cachedJsonBytes != null && cachedJsonBytes.length > 0) {
            return new ByteArrayInputStream(cachedJsonBytes);
        }
        InputStream is = getClass().getResourceAsStream("/webpagetest/sample-result.json");
        assertNotNull(is, "Fixture not found");
        return is;
    }

    private JsonNode runQuery(String query) throws IOException {
        try (InputStream is = openLiveOrFixture()) {
            JjqJsonParser jsonParser = new JjqJsonParser();
            JsonNode root = jsonParser.parse(is);
            QueryParser qp = new QueryParser();
            QueryNode q = qp.parse(query);
            QueryExecutor exec = new QueryExecutor();
            return exec.execute(q, root).findFirst().orElse(null);
        }
    }

    private List<JsonNode> runQueryToList(String query) throws IOException {
        try (InputStream is = openLiveOrFixture()) {
            JjqJsonParser jsonParser = new JjqJsonParser();
            JsonNode root = jsonParser.parse(is);
            QueryParser qp = new QueryParser();
            QueryNode q = qp.parse(query);
            QueryExecutor exec = new QueryExecutor();
            return exec.execute(q, root).collect(Collectors.toList());
        }
    }

    @Test
    public void extractsMedianFirstViewSpeedIndex() throws Exception {
        JsonNode node = runQuery(".data | .median | .firstView | .SpeedIndex");
        assertTrue(node instanceof JsonNode.JsonNumber);
        // When using live API, the exact number can vary; when using fixture, it will match
        if (cachedJsonBytes == null) {
            assertEquals(1234L, ((JsonNode.JsonNumber) node).numberValue().longValue());
        } else {
            // Live: just assert it's a positive integer
            assertTrue(((JsonNode.JsonNumber) node).numberValue().doubleValue() > 0);
        }
    }

    @Test
    public void extractsMedianFirstViewTTFB() throws Exception {
        JsonNode node = runQuery(".data | .median | .firstView | .TTFB");
        assertTrue(node instanceof JsonNode.JsonNumber);
        if (cachedJsonBytes == null) {
            assertEquals(321L, ((JsonNode.JsonNumber) node).numberValue().longValue());
        } else {
            assertTrue(((JsonNode.JsonNumber) node).numberValue().doubleValue() > 0);
        }
    }

    @Test
    public void extractsRunSpeedIndexesViaMap() throws Exception {
        // Take runs["1"].firstView.SpeedIndex and runs["2"].firstView.SpeedIndex
        // Our simple parser doesn't support string keys in [] so we map manually using pipe
        JsonNode one = runQuery(".data | .runs | .1 | .firstView | .SpeedIndex");
        JsonNode two = runQuery(".data | .runs | .2 | .firstView | .SpeedIndex");
        assertTrue(one instanceof JsonNode.JsonNumber);
        assertTrue(two instanceof JsonNode.JsonNumber);
        if (cachedJsonBytes == null) {
            assertEquals(1300L, ((JsonNode.JsonNumber) one).numberValue().longValue());
            assertEquals(1250L, ((JsonNode.JsonNumber) two).numberValue().longValue());
        } else {
            assertTrue(((JsonNode.JsonNumber) one).numberValue().doubleValue() > 0);
            assertTrue(((JsonNode.JsonNumber) two).numberValue().doubleValue() > 0);
        }
    }

    @Test
    public void extractsSummaryUrlAndTestId() throws Exception {
        JsonNode summary = runQuery(".data | .summary");
        JsonNode testId = runQuery(".data | .testId");
        assertTrue(summary instanceof JsonNode.JsonString);
        assertTrue(testId instanceof JsonNode.JsonString);
        if (cachedJsonBytes == null) {
            assertEquals("https://www.webpagetest.org/result/210910_AiDc3/", ((JsonNode.JsonString) summary).value());
            assertEquals("210910_AiDc3", ((JsonNode.JsonString) testId).value());
        } else {
            // Live: summary should contain the testId, and be a valid WPT result URL
            String summaryVal = ((JsonNode.JsonString) summary).value();
            String testIdVal = ((JsonNode.JsonString) testId).value();
            assertTrue(summaryVal.contains(testIdVal));
            assertTrue(summaryVal.startsWith("https://www.webpagetest.org/result/"));
        }
    }
}
