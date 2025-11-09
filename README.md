# JJQ - A Java Clone of jq

JJQ is a high-performance Java implementation of the popular [jq](https://stedolan.github.io/jq/) command-line JSON processor. It leverages Java 25 features and Eclipse Collections for optimal performance.

## Features

- Parse and process JSON data
- Apply filters to extract specific information
- Transform JSON data with powerful operations
- High performance using Eclipse Collections
- Modern Java 25 features including pattern matching and string templates

## Supported Operations

- Identity filter (`.`)
- Field access (`.field`)
- Array indexing (`.[0]`)
- Array slicing (`.[1:3]`)
- Piping (`filter1 | filter2`)
- Mapping (`map(filter)`)
- Selection (`select(filter)`)

## Building

```bash
mvn clean package
```

This will create an executable JAR file with all dependencies included.

## Native image (GraalVM)

Prerequisites:
- GraalVM is installed
- native-image.cmd is available on PATH (Windows)

Build steps (Windows):

```powershell
# 1) Build the fat jar
mvn -DskipTests package

# 2) Build the native image using GraalVM
# The script below will find the latest *-jar-with-dependencies.jar and invoke native-image.cmd
./build-native.ps1 -SkipTests
```

Manual native-image invocation (alternative):

```powershell
mvn -DskipTests package
$jar = Get-ChildItem target -Filter "*-jar-with-dependencies.jar" | Sort-Object LastWriteTime -Descending | Select -First 1
native-image.cmd -H:Name=jjq --no-fallback --install-exit-handlers -jar $jar.FullName
```

Run the native executable:

```powershell
./target/jjq.exe --help
```

## Usage

```bash
java -jar target/jjq-1.0-SNAPSHOT-jar-with-dependencies.jar [options] <filter> [file]
```

If no file is specified, JJQ reads from standard input.

### Options

- `-c, --compact-output`: Output compact JSON without whitespace
- `-C, --color-output`: Colorize JSON output
- `-r, --raw-output`: Output raw strings, not JSON texts
- `-h, --help`: Show help message
- `-V, --version`: Show version information

## Examples

### Basic Filtering

```bash
# Extract the name field from a JSON object
echo '{"name":"John","age":30}' | java -jar jjq.jar '.name'
# Output: "John"

# Extract the second element from an array
echo '[10,20,30,40]' | java -jar jjq.jar '.[1]'
# Output: 20
```

### Piping

```bash
# Extract the name field from a nested object
echo '{"user":{"name":"John","age":30}}' | java -jar jjq.jar '.user | .name'
# Output: "John"
```

### Mapping

```bash
# Extract all names from an array of objects
echo '[{"name":"John"},{"name":"Jane"}]' | java -jar jjq.jar 'map(.name)'
# Output: ["John","Jane"]
```

### Selection

```bash
# Select all users older than 25
echo '[{"name":"John","age":30},{"name":"Jane","age":25}]' | java -jar jjq.jar 'map(select(.age > 25))'
# Output: [{"name":"John","age":30}]
```

## Implementation Details

JJQ is implemented using:

- **Eclipse Collections**: For high-performance collections
- **Jackson**: For efficient JSON parsing
- **Picocli**: For command-line argument parsing
- **Java 25 Features**: Pattern matching, string templates, and more

## Performance

JJQ is designed for high performance:

- Uses Eclipse Collections' specialized collections for better memory efficiency
- Leverages pattern matching for efficient query execution
- Employs streaming for processing large JSON files

## License

MIT