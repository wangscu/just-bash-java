package com.justbash.commands.yq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.justbash.commands.queryengine.SafeObject;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;

/**
 * Format parsing and output for the yq command.
 * Supports YAML, JSON, XML, INI, CSV, and TOML.
 */
public final class QueryFormats {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final XmlMapper XML_MAPPER = new XmlMapper();
    private static final Yaml YAML = new Yaml();

    private QueryFormats() {}

    public enum InputFormat { YAML, XML, JSON, INI, CSV, TOML }
    public enum OutputFormat { YAML, XML, JSON, INI, CSV, TOML }

    public static boolean isValidInputFormat(String value) {
        try {
            InputFormat.valueOf(value.toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static boolean isValidOutputFormat(String value) {
        try {
            OutputFormat.valueOf(value.toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static InputFormat parseInputFormat(String value) {
        return InputFormat.valueOf(value.toUpperCase());
    }

    public static OutputFormat parseOutputFormat(String value) {
        return OutputFormat.valueOf(value.toUpperCase());
    }

    public static InputFormat detectFormatFromExtension(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) return InputFormat.YAML;
        if (lower.endsWith(".json")) return InputFormat.JSON;
        if (lower.endsWith(".xml")) return InputFormat.XML;
        if (lower.endsWith(".ini")) return InputFormat.INI;
        if (lower.endsWith(".csv") || lower.endsWith(".tsv")) return InputFormat.CSV;
        if (lower.endsWith(".toml")) return InputFormat.TOML;
        return null;
    }

    @SuppressWarnings("unchecked")
    public static Object parseInput(String input, InputFormat format) {
        String trimmed = input.trim();
        if (trimmed.isEmpty()) return null;

        Object result = switch (format) {
            case YAML -> YAML.load(trimmed);
            case JSON -> {
                try {
                    yield JSON_MAPPER.readValue(trimmed, Object.class);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("Invalid JSON: " + e.getMessage());
                }
            }
            case XML -> {
                try {
                    yield XML_MAPPER.readValue(trimmed, Object.class);
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("Invalid XML: " + e.getMessage());
                }
            }
            case INI -> parseIni(trimmed);
            case CSV -> parseCsv(trimmed, true);
            case TOML -> parseToml(trimmed);
        };

        return SafeObject.sanitizeParsedData(result);
    }

    public static List<Object> parseAllYamlDocuments(String input) {
        List<Object> result = new ArrayList<>();
        for (Object doc : YAML.loadAll(input)) {
            result.add(SafeObject.sanitizeParsedData(doc));
        }
        return result;
    }

    public static String formatOutput(Object value, OutputFormat format, FormatOptions options) {
        if (value == null) return "";

        return switch (format) {
            case YAML -> formatYaml(value);
            case JSON -> formatJson(value, options);
            case XML -> formatXml(value, options);
            case INI -> formatIni(value);
            case CSV -> formatCsv(value);
            case TOML -> formatToml(value);
        };
    }

    // ========================================================================
    // YAML
    // ========================================================================

    private static String formatYaml(Object value) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        Yaml yaml = new Yaml(options);
        return yaml.dump(value).trim();
    }

    // ========================================================================
    // JSON
    // ========================================================================

    private static String formatJson(Object value, FormatOptions options) {
        try {
            if (options.raw() && value instanceof String s) {
                return s;
            }
            if (options.compact()) {
                return JSON_MAPPER.writeValueAsString(value);
            }
            return JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "";
        }
    }

    // ========================================================================
    // XML
    // ========================================================================

    private static String formatXml(Object value, FormatOptions options) {
        try {
            return XML_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "";
        }
    }

    // ========================================================================
    // CSV
    // ========================================================================

    @SuppressWarnings("unchecked")
    private static Object parseCsv(String input, boolean hasHeader) {
        try {
            CSVFormat format = hasHeader ? CSVFormat.DEFAULT.withFirstRecordAsHeader() : CSVFormat.DEFAULT;
            CSVParser parser = new CSVParser(new StringReader(input), format);
            List<Object> result = new ArrayList<>();

            if (hasHeader) {
                for (CSVRecord record : parser) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (String header : parser.getHeaderNames()) {
                        row.put(header, record.get(header));
                    }
                    result.add(row);
                }
            } else {
                for (CSVRecord record : parser) {
                    List<Object> row = new ArrayList<>();
                    for (String field : record) {
                        row.add(field);
                    }
                    result.add(row);
                }
            }
            parser.close();
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Invalid CSV: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static String formatCsv(Object value) {
        List<?> list;
        if (value instanceof List<?> l) {
            list = l;
        } else {
            list = List.of(value);
        }
        if (list.isEmpty()) return "";

        try {
            StringWriter sw = new StringWriter();
            CSVPrinter printer;

            Object first = list.get(0);
            if (first instanceof Map) {
                // Header from first row keys
                List<String> headers = new ArrayList<>(((Map<String, Object>) first).keySet());
                printer = new CSVPrinter(sw, CSVFormat.DEFAULT.withHeader(headers.toArray(new String[0])));
                for (Object item : list) {
                    Map<String, Object> row = (Map<String, Object>) item;
                    List<Object> values = new ArrayList<>();
                    for (String h : headers) values.add(row.getOrDefault(h, ""));
                    printer.printRecord(values);
                }
            } else if (first instanceof List) {
                printer = new CSVPrinter(sw, CSVFormat.DEFAULT);
                for (Object item : list) {
                    printer.printRecord((List<?>) item);
                }
            } else {
                printer = new CSVPrinter(sw, CSVFormat.DEFAULT);
                for (Object item : list) {
                    printer.printRecord(item);
                }
            }
            printer.flush();
            printer.close();
            return sw.toString().trim();
        } catch (IOException e) {
            return "";
        }
    }

    // ========================================================================
    // INI
    // ========================================================================

    @SuppressWarnings("unchecked")
    private static Object parseIni(String input) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> currentSection = result;
        String currentSectionName = null;

        for (String line : input.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) continue;

            if (line.startsWith("[") && line.endsWith("]")) {
                currentSectionName = line.substring(1, line.length() - 1).trim();
                currentSection = new LinkedHashMap<>();
                result.put(currentSectionName, currentSection);
            } else {
                int eq = line.indexOf('=');
                if (eq >= 0) {
                    String key = line.substring(0, eq).trim();
                    String val = line.substring(eq + 1).trim();
                    currentSection.put(key, val);
                }
            }
        }

        // If no sections, return the root directly
        if (currentSectionName == null) {
            return result;
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static String formatIni(Object value) {
        Map<String, Object> obj = SafeObject.asQueryRecord(value);
        if (obj == null) return "";

        StringBuilder sb = new StringBuilder();
        // Write top-level keys first
        for (Map.Entry<String, Object> e : obj.entrySet()) {
            if (!(e.getValue() instanceof Map)) {
                sb.append(e.getKey()).append("=").append(e.getValue()).append("\n");
            }
        }
        // Write sections
        for (Map.Entry<String, Object> e : obj.entrySet()) {
            if (e.getValue() instanceof Map) {
                sb.append("[").append(e.getKey()).append("]\n");
                Map<String, Object> section = (Map<String, Object>) e.getValue();
                for (Map.Entry<String, Object> se : section.entrySet()) {
                    sb.append(se.getKey()).append("=").append(se.getValue()).append("\n");
                }
            }
        }
        return sb.toString().trim();
    }

    // ========================================================================
    // TOML
    // ========================================================================

    private static Object parseToml(String input) {
        com.moandjiezana.toml.Toml toml = new com.moandjiezana.toml.Toml();
        toml.read(input);
        return toml.toMap();
    }

    @SuppressWarnings("unchecked")
    private static String formatToml(Object value) {
        Map<String, Object> obj = SafeObject.asQueryRecord(value);
        if (obj == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : obj.entrySet()) {
            sb.append(e.getKey()).append(" = ").append(formatTomlValue(e.getValue())).append("\n");
        }
        return sb.toString().trim();
    }

    private static String formatTomlValue(Object value) {
        if (value == null) return "\"\"";
        if (value instanceof String s) return "\"" + s + "\"";
        if (value instanceof Number n) return n.toString();
        if (value instanceof Boolean b) return b.toString();
        if (value instanceof List<?> list) {
            return list.stream().map(QueryFormats::formatTomlValue).collect(java.util.stream.Collectors.joining(", ", "[", "]"));
        }
        return value.toString();
    }

    // ========================================================================
    // Front matter extraction
    // ========================================================================

    public static FrontMatterResult extractFrontMatter(String input) {
        String trimmed = input.trim();

        // YAML front-matter: ---\n...\n---
        if (trimmed.startsWith("---")) {
            int endIdx = trimmed.indexOf("\n---", 3);
            if (endIdx > 0) {
                String yamlContent = trimmed.substring(3, endIdx).trim();
                String remaining = trimmed.substring(endIdx + 4).trim();
                Object frontMatter = SafeObject.sanitizeParsedData(YAML.load(yamlContent));
                return new FrontMatterResult(frontMatter, remaining);
            }
        }

        // TOML front-matter: +++\n...\n+++
        if (trimmed.startsWith("+++")) {
            int endIdx = trimmed.indexOf("\n+++", 3);
            if (endIdx > 0) {
                String tomlContent = trimmed.substring(3, endIdx).trim();
                String remaining = trimmed.substring(endIdx + 4).trim();
                Object frontMatter = SafeObject.sanitizeParsedData(parseToml(tomlContent));
                return new FrontMatterResult(frontMatter, remaining);
            }
        }

        // JSON front-matter: {{{\n...\n}}}
        if (trimmed.startsWith("{{{")) {
            int endIdx = trimmed.indexOf("\n}}}", 3);
            if (endIdx > 0) {
                String jsonContent = trimmed.substring(3, endIdx).trim();
                String remaining = trimmed.substring(endIdx + 4).trim();
                try {
                    Object frontMatter = SafeObject.sanitizeParsedData(JSON_MAPPER.readValue(jsonContent, Object.class));
                    return new FrontMatterResult(frontMatter, remaining);
                } catch (JsonProcessingException e) {
                    return null;
                }
            }
        }

        return null;
    }

    public record FrontMatterResult(Object frontMatter, String content) {}

    // ========================================================================
    // Format options
    // ========================================================================

    public record FormatOptions(
        InputFormat inputFormat,
        OutputFormat outputFormat,
        boolean raw,
        boolean compact,
        boolean prettyPrint,
        int indent,
        String xmlAttributePrefix,
        String xmlContentName,
        String csvDelimiter,
        boolean csvHeader
    ) {
        public FormatOptions() {
            this(InputFormat.YAML, OutputFormat.YAML, false, false, false, 2, "+@", "+content", "", true);
        }
    }
}
