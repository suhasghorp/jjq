package com.challenges.query;

import java.util.ArrayDeque;
import java.util.Deque;

public class QueryParser {
    public QueryNode parse(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return new QueryNode.Identity();
        }

        String trimmed = queryString.trim();

        // Handle pipe operator (but not inside parentheses)
        int pipeIndex = findTopLevelPipe(trimmed);
        if (pipeIndex != -1) {
            QueryNode left = parse(trimmed.substring(0, pipeIndex));
            QueryNode right = parse(trimmed.substring(pipeIndex + 1));
            return new QueryNode.Pipe(left, right);
        }
        
        // Handle basic filters
        if (trimmed.equals(".")) {
            return new QueryNode.Identity();
        }
        
        // Handle array iterator .[] and chaining like .[].name
        if (trimmed.startsWith(".[]")) {
            QueryNode iterator = new QueryNode.Iterator(new QueryNode.Identity());
            String rest = trimmed.substring(3); // after .[]
            if (rest.isEmpty()) {
                return iterator;
            }
            if (rest.startsWith(".")) {
                return new QueryNode.Pipe(iterator, parse(rest));
            }
            throw new IllegalArgumentException("Unsupported query after .[]: " + rest);
        }
        
        if (trimmed.startsWith(".") && !trimmed.startsWith(".[")) {
            String field = trimmed.substring(1);
            return new QueryNode.ObjectAccess(field);
        }
        
        if (trimmed.startsWith(".[") && trimmed.endsWith("]")) {
            String indexStr = trimmed.substring(2, trimmed.length() - 1);

            // Check if this is a slice operation (contains ':')
            if (indexStr.contains(":")) {
                String[] parts = indexStr.split(":", -1); // -1 to preserve empty strings

                Integer start = null;
                Integer end = null;
                Integer step = null;

                if (parts.length >= 1 && !parts[0].isEmpty()) {
                    start = Integer.parseInt(parts[0]);
                }
                if (parts.length >= 2 && !parts[1].isEmpty()) {
                    end = Integer.parseInt(parts[1]);
                }
                if (parts.length >= 3 && !parts[2].isEmpty()) {
                    step = Integer.parseInt(parts[2]);
                }

                return new QueryNode.ArraySlice(start, end, step);
            }

            // Single index
            try {
                int index = Integer.parseInt(indexStr);
                return new QueryNode.ArrayIndex(index);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid array index: " + indexStr);
            }
        }
        
        // Handle map operation
        if (trimmed.startsWith("map(") && trimmed.endsWith(")")) {
            String innerQuery = trimmed.substring(4, trimmed.length() - 1);
            return new QueryNode.Map(parse(innerQuery));
        }
        
        // Handle select operation
        if (trimmed.startsWith("select(") && trimmed.endsWith(")")) {
            String condition = trimmed.substring(7, trimmed.length() - 1);
            return new QueryNode.Select(parse(condition));
        }
        
        throw new IllegalArgumentException("Unsupported query: " + queryString);
    }

    /**
     * Find the index of a pipe character that's not inside parentheses
     */
    private int findTopLevelPipe(String query) {
        int depth = 0;
        for (int i = 0; i < query.length(); i++) {
            char c = query.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == '|' && depth == 0) {
                return i;
            }
        }
        return -1;
    }
}