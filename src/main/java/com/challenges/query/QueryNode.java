package com.challenges.query;

public sealed interface QueryNode {
    record Identity() implements QueryNode {}
    record ObjectAccess(String field) implements QueryNode {}
    record ArrayIndex(int index) implements QueryNode {}
    record Pipe(QueryNode left, QueryNode right) implements QueryNode {}
    record ArraySlice(Integer start, Integer end, Integer step) implements QueryNode {}
    record Iterator(QueryNode query) implements QueryNode {}  // For .[] syntax
    record Map(QueryNode query) implements QueryNode {}       // For map() function
    record Select(QueryNode condition) implements QueryNode {}
}