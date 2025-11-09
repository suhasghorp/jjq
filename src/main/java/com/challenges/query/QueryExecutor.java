package com.challenges.query;

import com.challenges.json.JsonNode;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.factory.Lists;

import java.util.stream.Stream;

public class QueryExecutor {
    public Stream<JsonNode> execute(QueryNode query, JsonNode input) {
        return switch (query) {
            case QueryNode.Identity i -> Stream.of(input);
            
            case QueryNode.ObjectAccess oa when input instanceof JsonNode.JsonObject obj -> {
                JsonNode result = obj.fields().get(oa.field());
                yield result != null ? Stream.of(result) : Stream.of(new JsonNode.JsonNull());
            }

            case QueryNode.ObjectAccess oa -> Stream.of(new JsonNode.JsonNull());

            case QueryNode.ArrayIndex ai when input instanceof JsonNode.JsonArray arr -> {
                int index = ai.index();
                // Handle negative indices
                if (index < 0) {
                    index = arr.elements().size() + index;
                }
                if (index >= 0 && index < arr.elements().size()) {
                    yield Stream.of(arr.elements().get(index));
                } else {
                    yield Stream.of(new JsonNode.JsonNull());
                }
            }

            case QueryNode.ArrayIndex ai -> Stream.of(new JsonNode.JsonNull());
            
            case QueryNode.Pipe p -> 
                execute(p.left(), input).flatMap(node -> execute(p.right(), node));
                
            case QueryNode.Iterator it when input instanceof JsonNode.JsonArray arr ->
                arr.elements().stream().flatMap(element -> execute(it.query(), element));

            case QueryNode.Iterator it when input instanceof JsonNode.JsonObject obj ->
                obj.fields().values().stream().flatMap(value -> execute(it.query(), value));

            case QueryNode.Map m when input instanceof JsonNode.JsonArray arr ->
                arr.elements().stream().flatMap(element -> execute(m.query(), element));

            case QueryNode.Select s -> {
                // Select should check the condition on the input element
                // and return the element if condition is truthy
                Stream<JsonNode> conditionResult = execute(s.condition(), input);
                JsonNode firstResult = conditionResult.findFirst().orElse(null);

                // Check if the condition result is truthy
                boolean isTruthy = firstResult != null &&
                                   !(firstResult instanceof JsonNode.JsonNull) &&
                                   !(firstResult instanceof JsonNode.JsonBoolean b && !b.value());

                yield isTruthy ? Stream.of(input) : Stream.empty();
            }

            case QueryNode.ArraySlice slice when input instanceof JsonNode.JsonArray arr -> {
                MutableList<JsonNode> elements = arr.elements();
                int start = slice.start() != null ? slice.start() : 0;
                int end = slice.end() != null ? slice.end() : elements.size();
                int step = slice.step() != null ? slice.step() : 1;

                MutableList<JsonNode> result = Lists.mutable.empty();
                for (int i = start; i < end; i += step) {
                    if (i >= 0 && i < elements.size()) {
                        result.add(elements.get(i));
                    }
                }
                // Return as a single JsonArray node
                yield Stream.of(new JsonNode.JsonArray(result));
            }
            
            default -> Stream.empty();
        };
    }
}