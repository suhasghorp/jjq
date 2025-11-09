package com.challenges;

import com.challenges.json.JsonNode;
import com.challenges.json.JjqJsonParser;
import com.challenges.query.QueryExecutor;
import com.challenges.query.QueryNode;
import com.challenges.query.QueryParser;
import com.challenges.output.OutputFormatter;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

@Command(name = "jjq", mixinStandardHelpOptions = true, version = "1.0",
         description = "Process JSON data with jq-like filters")
public class JJQ implements Callable<Integer> {
    @Parameters(index = "0", description = "The jq filter to apply")
    private String filter;
    
    @Parameters(index = "1", arity = "0..1", description = "Input JSON file (default: stdin)")
    private File inputFile;
    
    @Option(names = {"-c", "--compact-output"}, description = "Compact output without whitespace")
    private boolean compactOutput = false;
    
    @Option(names = {"-C", "--color-output"}, description = "Colorize JSON output")
    private boolean colorOutput = false;
    
    @Option(names = {"-r", "--raw-output"}, description = "Output raw strings, not JSON texts")
    private boolean rawOutput = false;

    @Option(names = {"-S", "--sort-keys"}, description = "Sort object keys in output")
    private boolean sortKeys = false;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new JJQ()).execute(args);
        System.exit(exitCode);
    }
    
    @Override
    public Integer call() throws Exception {
        try {
            // Parse the query
            QueryParser queryParser = new QueryParser();
            QueryNode query = queryParser.parse(filter);
            
            // Parse the input JSON
            JjqJsonParser jsonParser = new JjqJsonParser();
            InputStream input = inputFile != null ? new FileInputStream(inputFile) : System.in;
            JsonNode jsonInput = jsonParser.parse(input);
            
            // Execute the query
            QueryExecutor queryExecutor = new QueryExecutor();
            Stream<JsonNode> results = queryExecutor.execute(query, jsonInput);
            
            // Format and output the results
            OutputFormatter formatter = new OutputFormatter(!compactOutput, colorOutput, sortKeys);
            results.forEach(result -> {
                if (rawOutput && result instanceof JsonNode.JsonString(String value)) {
                    System.out.println(value);
                } else {
                    System.out.println(formatter.format(result));
                }
            });
            
            return 0;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }
}