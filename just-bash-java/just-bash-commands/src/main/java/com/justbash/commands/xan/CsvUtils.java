package com.justbash.commands.xan;

import com.justbash.CommandContext;
import com.justbash.ExecResult;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.*;

public class CsvUtils {

    public record CsvTable(List<String> headers, List<Map<String, Object>> rows) {}

    public static CsvTable readCsv(String input) {
        try (CSVParser parser = new CSVParser(new StringReader(input.trim()),
                CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true).build())) {
            List<String> headers = new ArrayList<>(parser.getHeaderNames());
            List<Map<String, Object>> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (String header : headers) {
                    String value = record.isSet(header) ? record.get(header) : "";
                    row.put(header, detectType(value));
                }
                rows.add(row);
            }
            return new CsvTable(headers, rows);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static CsvTable readCsvNoHeader(String input) {
        try (CSVParser parser = new CSVParser(new StringReader(input.trim()),
                CSVFormat.DEFAULT.builder().setIgnoreEmptyLines(true).build())) {
            List<Map<String, Object>> rows = new ArrayList<>();
            List<String> headers = new ArrayList<>();
            for (CSVRecord record : parser) {
                if (headers.isEmpty()) {
                    for (int i = 0; i < record.size(); i++) {
                        headers.add(record.get(i));
                    }
                } else {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 0; i < headers.size() && i < record.size(); i++) {
                        row.put(headers.get(i), detectType(record.get(i)));
                    }
                    rows.add(row);
                }
            }
            return new CsvTable(headers, rows);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String writeCsv(List<String> headers, List<Map<String, Object>> rows) {
        StringWriter sw = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder()
                .setHeader(headers.toArray(new String[0]))
                .setRecordSeparator("\n")
                .build())) {
            for (Map<String, Object> row : rows) {
                List<String> values = new ArrayList<>();
                for (String header : headers) {
                    Object val = row.get(header);
                    values.add(formatForCsv(val));
                }
                printer.printRecord(values);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return sw.toString();
    }

    public static String writeRawCsv(List<List<String>> rows) {
        StringWriter sw = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder()
                .setRecordSeparator("\n")
                .build())) {
            for (List<String> row : rows) {
                printer.printRecord(row);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return sw.toString();
    }

    private static String formatForCsv(Object value) {
        if (value == null) return "";
        if (value instanceof Double d) {
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return String.valueOf(d.longValue());
            }
        }
        return String.valueOf(value);
    }

    public static Object detectType(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.equals("true")) return Boolean.TRUE;
        if (value.equals("false")) return Boolean.FALSE;
        try {
            // Try integer first
            if (value.matches("-?\\d+")) {
                return Double.valueOf(value);
            }
            // Try double
            return Double.valueOf(value);
        } catch (NumberFormatException e) {
            return value;
        }
    }

    public static Result readInput(List<String> args, CommandContext ctx) {
        String file = null;
        for (String a : args) {
            if (!a.startsWith("-") && !a.equals("-")) {
                file = a;
                break;
            }
        }

        String input;
        if (file == null || file.equals("-")) {
            input = ctx.stdin().decodeUtf8();
        } else {
            try {
                input = ctx.fs().readFile(ctx.fs().resolvePath(ctx.cwd(), file)).join();
            } catch (Exception e) {
                return new Result(null, new ExecResult("", "xan: " + file + ": No such file or directory\n", 1));
            }
        }

        if (input.trim().isEmpty()) {
            return new Result(new CsvTable(List.of(), List.of()), null);
        }
        return new Result(readCsv(input), null);
    }

    public record Result(CsvTable table, ExecResult error) {}

    public static String formatValue(Object value) {
        if (value == null) return "";
        String s = formatForCsv(value);
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
