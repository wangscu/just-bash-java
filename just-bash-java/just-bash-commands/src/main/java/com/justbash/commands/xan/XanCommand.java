package com.justbash.commands.xan;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import com.justbash.commands.queryengine.AstNode;
import com.justbash.commands.queryengine.EvalContext;
import com.justbash.commands.queryengine.Evaluator;
import com.justbash.commands.queryengine.Parser;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class XanCommand implements Command {

    @Override
    public String name() { return "xan"; }

    private static final String MAIN_HELP = """
        xan - CSV toolkit for data manipulation

        Usage: xan <COMMAND> [OPTIONS] [FILE]

        COMMANDS:
          Core:
            headers    Show column names
            count      Count rows
            head       Show first N rows
            tail       Show last N rows
            slice      Extract row range
            reverse    Reverse row order
            behead     Remove header row
            sample     Random sample of rows

          Column operations:
            select     Select columns (supports glob, ranges, negation)
            drop       Drop columns
            rename     Rename columns
            enum       Add row index column

          Row operations:
            filter     Filter rows by jq expression
            search     Filter rows by regex match
            sort       Sort rows
            dedup      Remove duplicates
            top        Get top N by column

          Transformations:
            map        Add computed columns
            transform  Modify existing columns
            explode    Split column into multiple rows
            implode    Combine rows, join column values
            flatmap    Map returning multiple rows
            pivot      Reshape rows into columns
            transpose  Swap rows and columns

          Aggregation:
            agg        Aggregate values
            groupby    Group and aggregate
            frequency  Count value occurrences
            stats      Show column statistics

          Multi-file:
            cat        Concatenate CSV files
            join       Join two CSV files on key
            merge      Merge sorted CSV files
            split      Split into multiple files
            partition  Split by column value

          Data conversion:
            to         Convert CSV to other formats (json)
            from       Convert other formats to CSV (json)
            shuffle    Randomly reorder rows
            fixlengths Fix ragged CSV files

          Output:
            view       Pretty print as table
            flatten    Display records vertically (alias: f)
            fmt        Format output

        EXAMPLES:
          xan headers data.csv
          xan count data.csv
          xan head -n 5 data.csv
          xan select name,email data.csv
          xan select 'vec_*' data.csv
          xan filter '.age > 30' data.csv
          xan sort -N price data.csv
          xan agg 'sum(amount) as total' data.csv
          xan groupby region 'count() as n' data.csv
          xan join id file1.csv id file2.csv
        """;

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> run(args, ctx));
    }

    private ExecResult run(List<String> args, CommandContext ctx) {
        if (args.isEmpty() || args.contains("--help") || args.contains("-h")) {
            return new ExecResult(MAIN_HELP, "", 0);
        }

        String sub = args.get(0);
        List<String> subArgs = args.subList(1, args.size());

        // Subcommand help
        if (subArgs.contains("--help") || subArgs.contains("-h")) {
            return new ExecResult(MAIN_HELP, "", 0);
        }

        return switch (sub) {
            case "headers" -> cmdHeaders(subArgs, ctx);
            case "count" -> cmdCount(subArgs, ctx);
            case "head" -> cmdHead(subArgs, ctx);
            case "tail" -> cmdTail(subArgs, ctx);
            case "slice" -> cmdSlice(subArgs, ctx);
            case "reverse" -> cmdReverse(subArgs, ctx);
            case "behead" -> cmdBehead(subArgs, ctx);
            case "sample" -> cmdSample(subArgs, ctx);
            case "select" -> cmdSelect(subArgs, ctx);
            case "drop" -> cmdDrop(subArgs, ctx);
            case "rename" -> cmdRename(subArgs, ctx);
            case "enum" -> cmdEnum(subArgs, ctx);
            case "filter" -> cmdFilter(subArgs, ctx);
            case "search" -> cmdSearch(subArgs, ctx);
            case "sort" -> cmdSort(subArgs, ctx);
            case "dedup" -> cmdDedup(subArgs, ctx);
            case "top" -> cmdTop(subArgs, ctx);
            case "map" -> cmdMap(subArgs, ctx);
            case "transform" -> cmdTransform(subArgs, ctx);
            case "explode" -> cmdExplode(subArgs, ctx);
            case "implode" -> cmdImplode(subArgs, ctx);
            case "flatmap" -> cmdFlatmap(subArgs, ctx);
            case "pivot" -> cmdPivot(subArgs, ctx);
            case "agg" -> cmdAgg(subArgs, ctx);
            case "groupby" -> cmdGroupby(subArgs, ctx);
            case "frequency", "freq" -> cmdFrequency(subArgs, ctx);
            case "stats" -> cmdStats(subArgs, ctx);
            case "join" -> cmdJoin(subArgs, ctx);
            case "merge" -> cmdMerge(subArgs, ctx);
            case "transpose" -> cmdTranspose(subArgs, ctx);
            case "shuffle" -> cmdShuffle(subArgs, ctx);
            case "fixlengths" -> cmdFixlengths(subArgs, ctx);
            case "split" -> cmdSplit(subArgs, ctx);
            case "partition" -> cmdPartition(subArgs, ctx);
            case "to" -> cmdTo(subArgs, ctx);
            case "from" -> cmdFrom(subArgs, ctx);
            case "cat" -> cmdCat(subArgs, ctx);
            case "view" -> cmdView(subArgs, ctx);
            case "flatten", "f" -> cmdFlatten(subArgs, ctx);
            case "fmt" -> cmdFmt(subArgs, ctx);
            default -> new ExecResult("", "xan: unknown command '" + sub + "'\nRun 'xan --help' for usage.\n", 1);
        };
    }

    // ========== Core commands ==========

    private ExecResult cmdHeaders(List<String> args, CommandContext ctx) {
        boolean justNames = args.contains("-j") || args.contains("--just-names");
        CsvUtils.Result res = CsvUtils.readInput(args, ctx);
        if (res.error() != null) return res.error();
        List<String> headers = res.table().headers();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < headers.size(); i++) {
            if (justNames) sb.append(headers.get(i));
            else sb.append(i).append("   ").append(headers.get(i));
            sb.append("\n");
        }
        return new ExecResult(sb.toString(), "", 0);
    }

    private ExecResult cmdCount(List<String> args, CommandContext ctx) {
        CsvUtils.Result res = CsvUtils.readInput(args, ctx);
        if (res.error() != null) return res.error();
        return new ExecResult(res.table().rows().size() + "\n", "", 0);
    }

    private ExecResult cmdHead(List<String> args, CommandContext ctx) {
        int n = 10;
        List<String> fileArgs = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            if ((args.get(i).equals("-l") || args.get(i).equals("-n")) && i + 1 < args.size()) {
                n = Integer.parseInt(args.get(++i));
            } else {
                fileArgs.add(args.get(i));
            }
        }
        CsvUtils.Result res = CsvUtils.readInput(fileArgs, ctx);
        if (res.error() != null) return res.error();
        List<Map<String, Object>> rows = res.table().rows().subList(0, Math.min(n, res.table().rows().size()));
        return new ExecResult(CsvUtils.writeCsv(res.table().headers(), rows), "", 0);
    }

    private ExecResult cmdTail(List<String> args, CommandContext ctx) {
        int n = 10;
        List<String> fileArgs = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            if ((args.get(i).equals("-l") || args.get(i).equals("-n")) && i + 1 < args.size()) {
                n = Integer.parseInt(args.get(++i));
            } else {
                fileArgs.add(args.get(i));
            }
        }
        CsvUtils.Result res = CsvUtils.readInput(fileArgs, ctx);
        if (res.error() != null) return res.error();
        List<Map<String, Object>> rows = res.table().rows();
        int start = Math.max(0, rows.size() - n);
        List<Map<String, Object>> tail = rows.subList(start, rows.size());
        return new ExecResult(CsvUtils.writeCsv(res.table().headers(), tail), "", 0);
    }

    private ExecResult cmdSlice(List<String> args, CommandContext ctx) {
        Integer start = null, end = null, len = null;
        List<String> fileArgs = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if ((a.equals("-s") || a.equals("--start")) && i + 1 < args.size()) {
                start = Integer.parseInt(args.get(++i));
            } else if ((a.equals("-e") || a.equals("--end")) && i + 1 < args.size()) {
                end = Integer.parseInt(args.get(++i));
            } else if ((a.equals("-l") || a.equals("--len")) && i + 1 < args.size()) {
                len = Integer.parseInt(args.get(++i));
            } else if (!a.startsWith("-")) {
                fileArgs.add(a);
            }
        }
        CsvUtils.Result res = CsvUtils.readInput(fileArgs, ctx);
        if (res.error() != null) return res.error();
        int s = start != null ? start : 0;
        int e;
        if (len != null) e = s + len;
        else if (end != null) e = end;
        else e = res.table().rows().size();
        List<Map<String, Object>> rows = res.table().rows().subList(Math.min(s, res.table().rows().size()), Math.min(e, res.table().rows().size()));
        return new ExecResult(CsvUtils.writeCsv(res.table().headers(), rows), "", 0);
    }

    private ExecResult cmdReverse(List<String> args, CommandContext ctx) {
        CsvUtils.Result res = CsvUtils.readInput(args, ctx);
        if (res.error() != null) return res.error();
        List<Map<String, Object>> rows = new ArrayList<>(res.table().rows());
        Collections.reverse(rows);
        return new ExecResult(CsvUtils.writeCsv(res.table().headers(), rows), "", 0);
    }

    private ExecResult cmdBehead(List<String> args, CommandContext ctx) {
        CsvUtils.Result res = CsvUtils.readInput(args, ctx);
        if (res.error() != null) return res.error();
        List<String> headers = res.table().headers();
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> row : res.table().rows()) {
            for (int i = 0; i < headers.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(CsvUtils.formatValue(row.get(headers.get(i))));
            }
            sb.append("\n");
        }
        return new ExecResult(sb.toString(), "", 0);
    }

    private ExecResult cmdSample(List<String> args, CommandContext ctx) {
        Integer num = null;
        Integer seed = null;
        List<String> fileArgs = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if (a.equals("--seed") && i + 1 < args.size()) {
                seed = Integer.parseInt(args.get(++i));
            } else if (!a.startsWith("-")) {
                try {
                    int parsed = Integer.parseInt(a);
                    if (num == null && parsed > 0) num = parsed;
                    else fileArgs.add(a);
                } catch (NumberFormatException e) {
                    fileArgs.add(a);
                }
            }
        }
        if (num == null) return new ExecResult("", "xan sample: usage: xan sample <sample-size> [FILE]\n", 1);
        CsvUtils.Result res = CsvUtils.readInput(fileArgs, ctx);
        if (res.error() != null) return res.error();
        List<Map<String, Object>> rows = res.table().rows();
        if (rows.size() <= num) {
            return new ExecResult(CsvUtils.writeCsv(res.table().headers(), rows), "", 0);
        }
        long rng = seed != null ? seed : System.currentTimeMillis();
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) indices.add(i);
        for (int i = indices.size() - 1; i > 0; i--) {
            rng = (rng * 1103515245 + 12345) & 0x7fffffffL;
            int j = (int) (rng % (i + 1));
            Collections.swap(indices, i, j);
        }
        List<Integer> sampled = indices.subList(0, num);
        sampled.sort(Integer::compare);
        List<Map<String, Object>> result = new ArrayList<>();
        for (int idx : sampled) result.add(rows.get(idx));
        return new ExecResult(CsvUtils.writeCsv(res.table().headers(), result), "", 0);
    }

    // ========== Column operations ==========

    private ExecResult cmdSelect(List<String> args, CommandContext ctx) {
        String colSpec = null;
        List<String> fileArgs = new ArrayList<>();
        for (String a : args) {
            if (a.startsWith("-")) continue;
            if (colSpec == null) colSpec = a;
            else fileArgs.add(a);
        }
        if (colSpec == null) return new ExecResult("", "xan select: no columns specified\n", 1);
        CsvUtils.Result res = CsvUtils.readInput(fileArgs, ctx);
        if (res.error() != null) return res.error();
        List<String> newHeaders = ColumnSelection.parseColumnSpec(colSpec, res.table().headers());
        List<Map<String, Object>> newRows = new ArrayList<>();
        for (Map<String, Object> row : res.table().rows()) {
            Map<String, Object> newRow = new LinkedHashMap<>();
            for (String col : newHeaders) newRow.put(col, row.get(col));
            newRows.add(newRow);
        }
        return new ExecResult(CsvUtils.writeCsv(newHeaders, newRows), "", 0);
    }

    private ExecResult cmdDrop(List<String> args, CommandContext ctx) {
        String colSpec = null;
        List<String> fileArgs = new ArrayList<>();
        for (String a : args) {
            if (a.startsWith("-")) continue;
            if (colSpec == null) colSpec = a;
            else fileArgs.add(a);
        }
        if (colSpec == null) return new ExecResult("", "xan drop: no columns specified\n", 1);
        CsvUtils.Result res = CsvUtils.readInput(fileArgs, ctx);
        if (res.error() != null) return res.error();
        Set<String> dropCols = new HashSet<>(ColumnSelection.parseColumnSpec(colSpec, res.table().headers()));
        List<String> newHeaders = res.table().headers().stream().filter(h -> !dropCols.contains(h)).toList();
        List<Map<String, Object>> newRows = new ArrayList<>();
        for (Map<String, Object> row : res.table().rows()) {
            Map<String, Object> newRow = new LinkedHashMap<>();
            for (String col : newHeaders) newRow.put(col, row.get(col));
            newRows.add(newRow);
        }
        return new ExecResult(CsvUtils.writeCsv(newHeaders, newRows), "", 0);
    }

    private ExecResult cmdRename(List<String> args, CommandContext ctx) {
        String newNames = null;
        String selectCols = null;
        List<String> fileArgs = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if (a.equals("-s") && i + 1 < args.size()) {
                selectCols = args.get(++i);
            } else if (!a.startsWith("-")) {
                if (newNames == null) newNames = a;
                else fileArgs.add(a);
            }
        }
        if (newNames == null) return new ExecResult("", "xan rename: no new name(s) specified\n", 1);
        CsvUtils.Result res = CsvUtils.readInput(fileArgs, ctx);
        if (res.error() != null) return res.error();
        List<String> headers = res.table().headers();
        List<String> newHeaders;
        if (selectCols != null) {
            List<String> oldCols = List.of(selectCols.split(","));
            List<String> nameList = List.of(newNames.split(","));
            Map<String, String> renames = new HashMap<>();
            for (int i = 0; i < oldCols.size() && i < nameList.size(); i++) {
                renames.put(oldCols.get(i).trim(), nameList.get(i).trim());
            }
            newHeaders = headers.stream().map(h -> renames.getOrDefault(h, h)).toList();
        } else {
            List<String> nameList = List.of(newNames.split(","));
            newHeaders = new ArrayList<>();
            for (int i = 0; i < headers.size(); i++) {
                newHeaders.add(i < nameList.size() ? nameList.get(i).trim() : headers.get(i));
            }
        }
        List<Map<String, Object>> newRows = new ArrayList<>();
        for (Map<String, Object> row : res.table().rows()) {
            Map<String, Object> newRow = new LinkedHashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                newRow.put(newHeaders.get(i), row.get(headers.get(i)));
            }
            newRows.add(newRow);
        }
        return new ExecResult(CsvUtils.writeCsv(newHeaders, newRows), "", 0);
    }

    private ExecResult cmdEnum(List<String> args, CommandContext ctx) {
        String colName = "index";
        List<String> fileArgs = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            if (args.get(i).equals("-c") && i + 1 < args.size()) {
                colName = args.get(++i);
            } else {
                fileArgs.add(args.get(i));
            }
        }
        CsvUtils.Result res = CsvUtils.readInput(fileArgs, ctx);
        if (res.error() != null) return res.error();
        List<String> headers = new ArrayList<>();
        headers.add(colName);
        headers.addAll(res.table().headers());
        List<Map<String, Object>> newRows = new ArrayList<>();
        for (int i = 0; i < res.table().rows().size(); i++) {
            Map<String, Object> newRow = new LinkedHashMap<>();
            newRow.put(colName, (double) i);
            for (String h : res.table().headers()) newRow.put(h, res.table().rows().get(i).get(h));
            newRows.add(newRow);
        }
        return new ExecResult(CsvUtils.writeCsv(headers, newRows), "", 0);
    }

    // ========== Row operations ==========

    private ExecResult cmdFilter(List<String> args, CommandContext ctx) {
        boolean invert = false;
        int limit = 0;
        String expr = null;
        List<String> fileArgs = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if (a.equals("-v") || a.equals("--invert")) invert = true;
            else if ((a.equals("-l") || a.equals("--limit")) && i + 1 < args.size()) limit = Integer.parseInt(args.get(++i));
            else if (!a.startsWith("-")) {
                if (expr == null) expr = a;
                else fileArgs.add(a);
            }
        }
        if (expr == null) return new ExecResult("", "xan filter: no expression specified\n", 1);
        CsvUtils.Result res = CsvUtils.readInput(fileArgs, ctx);
        if (res.error() != null) return res.error();

        EvalContext evalCtx = EvalContext.create(ctx.env(), new EvalContext.Limits());
        String jqExpr = toJqExpr(expr);
        AstNode ast;
        try {
            ast = Parser.parse(jqExpr);
        } catch (Exception e) {
            return new ExecResult("", "xan filter: parse error: " + e.getMessage() + "\n", 1);
        }

        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> row : res.table().rows()) {
            if (limit > 0 && filtered.size() >= limit) break;
            try {
                List<Object> results = Evaluator.evaluate(row, ast, evalCtx);
                boolean matches = !results.isEmpty() && results.stream().anyMatch(r -> r != null && !Boolean.FALSE.equals(r));
                if (invert ? !matches : matches) filtered.add(row);
            } catch (Exception e) {
                // skip rows that fail evaluation
            }
        }
        return new ExecResult(CsvUtils.writeCsv(res.table().headers(), filtered), "", 0);
    }

    private ExecResult cmdSearch(List<String> args, CommandContext ctx) {
        String pattern = null;
        List<String> selectCols = new ArrayList<>();
        boolean invert = false;
        boolean ignoreCase = false;
        List<String> fileArgs = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if ((a.equals("-s") || a.equals("--select")) && i + 1 < args.size()) {
                selectCols = List.of(args.get(++i).split(","));
            } else if (a.equals("-v") || a.equals("--invert")) invert = true;
            else if (a.equals("-i") || a.equals("--ignore-case")) ignoreCase = true;
            else if (!a.startsWith("-")) {
                if (pattern == null) pattern = a;
                else fileArgs.add(a);
            }
        }
        if (pattern == null) return new ExecResult("", "xan search: no pattern specified\n", 1);
        CsvUtils.Result res = CsvUtils.readInput(fileArgs, ctx);
        if (res.error() != null) return res.error();
        Pattern regex;
        try {
            regex = Pattern.compile(pattern, ignoreCase ? Pattern.CASE_INSENSITIVE : 0);
        } catch (PatternSyntaxException e) {
            return new ExecResult("", "xan search: invalid regex pattern '" + pattern + "'\n", 1);
        }
        List<String> searchCols = selectCols.isEmpty() ? res.table().headers() : selectCols.stream().map(String::trim).toList();
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> row : res.table().rows()) {
            boolean matches = false;
            for (String col : searchCols) {
                Object val = row.get(col);
                if (val != null && regex.matcher(String.valueOf(val)).find()) {
                    matches = true;
                    break;
                }
            }
            if (invert ? !matches : matches) filtered.add(row);
        }
        return new ExecResult(CsvUtils.writeCsv(res.table().headers(), filtered), "", 0);
    }

    private ExecResult cmdSort(List<String> args, CommandContext ctx) {
        String column = null;
        boolean numeric = false;
        boolean reverse = false;
        List<String> fileArgs = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if (a.equals("-N") || a.equals("--numeric")) numeric = true;
            else if (a.equals("-R") || a.equals("-r") || a.equals("--reverse")) reverse = true;
            else if (a.equals("-s") && i + 1 < args.size()) column = args.get(++i);
            else if (!a.startsWith("-")) fileArgs.add(a);
        }
        CsvUtils.Result res = CsvUtils.readInput(fileArgs, ctx);
        if (res.error() != null) return res.error();
        if (column == null && !res.table().headers().isEmpty()) column = res.table().headers().get(0);
        final String sortCol = column;
        final boolean finalNumeric = numeric;
        final boolean finalReverse = reverse;
        List<Map<String, Object>> sorted = new ArrayList<>(res.table().rows());
        sorted.sort((a, b) -> {
            Object va = a.get(sortCol);
            Object vb = b.get(sortCol);
            int cmp;
            if (finalNumeric) {
                double na = toDouble(va, 0);
                double nb = toDouble(vb, 0);
                cmp = Double.compare(na, nb);
            } else {
                cmp = String.valueOf(va).compareTo(String.valueOf(vb));
            }
            return finalReverse ? -cmp : cmp;
        });
        return new ExecResult(CsvUtils.writeCsv(res.table().headers(), sorted), "", 0);
    }

    private ExecResult cmdDedup(List<String> args, CommandContext ctx) {
        String column = null;
        List<String> fileArgs = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if (a.equals("-s") && i + 1 < args.size()) column = args.get(++i);
            else if (!a.startsWith("-")) fileArgs.add(a);
        }
        CsvUtils.Result res = CsvUtils.readInput(fileArgs, ctx);
        if (res.error() != null) return res.error();
        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> deduped = new ArrayList<>();
        for (Map<String, Object> row : res.table().rows()) {
            String key = column != null ? String.valueOf(row.get(column)) : row.toString();
            if (!seen.contains(key)) {
                seen.add(key);
                deduped.add(row);
            }
        }
        return new ExecResult(CsvUtils.writeCsv(res.table().headers(), deduped), "", 0);
    }

    private ExecResult cmdTop(List<String> args, CommandContext ctx) {
        int n = 10;
        String column = null;
        boolean reverse = false;
        List<String> fileArgs = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if ((a.equals("-l") || a.equals("-n")) && i + 1 < args.size()) n = Integer.parseInt(args.get(++i));
            else if (a.equals("-R") || a.equals("-r") || a.equals("--reverse")) reverse = true;
            else if (!a.startsWith("-")) {
                if (column == null) column = a;
                else fileArgs.add(a);
            }
        }
        CsvUtils.Result res = CsvUtils.readInput(fileArgs, ctx);
        if (res.error() != null) return res.error();
        if (column == null && !res.table().headers().isEmpty()) column = res.table().headers().get(0);
        final String sortCol = column;
        final boolean finalReverse = reverse;
        List<Map<String, Object>> sorted = new ArrayList<>(res.table().rows());
        sorted.sort((a, b) -> {
            double na = toDouble(a.get(sortCol), 0);
            double nb = toDouble(b.get(sortCol), 0);
            return finalReverse ? Double.compare(na, nb) : Double.compare(nb, na);
        });
        List<Map<String, Object>> top = sorted.subList(0, Math.min(n, sorted.size()));
        return new ExecResult(CsvUtils.writeCsv(res.table().headers(), top), "", 0);
    }

    // ========== Expression-based commands ==========

    private ExecResult cmdMap(List<String> args, CommandContext ctx) {
        String mapExpr = null;
        boolean overwrite = false;
        boolean filterNull = false;
        List<String> fileArgs = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if (a.equals("-O") || a.equals("--overwrite")) overwrite = true;
            else if (a.equals("--filter")) filterNull = true;
            else if (!a.startsWith("-")) {
                if (mapExpr == null) mapExpr = a;
                else fileArgs.add(a);
            }
        }
        if (mapExpr == null) return new ExecResult("", "xan map: no expression specified\n", 1);
        CsvUtils.Result res = CsvUtils.readInput(fileArgs, ctx);
        if (res.error() != null) return res.error();

        EvalContext evalCtx = EvalContext.create(ctx.env(), new EvalContext.Limits());
        List<MapSpec> specs = parseMapSpecs(mapExpr);

        List<String> headers = new ArrayList<>(res.table().headers());
        if (!overwrite) {
            for (MapSpec spec : specs) {
                if (!headers.contains(spec.name)) headers.add(spec.name);
            }
        }

        List<Map<String, Object>> newRows = new ArrayList<>();
        for (int rowIdx = 0; rowIdx < res.table().rows().size(); rowIdx++) {
            Map<String, Object> row = res.table().rows().get(rowIdx);
            Map<String, Object> newRow = new LinkedHashMap<>(row);
            boolean skip = false;
            for (MapSpec spec : specs) {
                try {
                    AstNode ast = Parser.parse(toJqExpr(spec.expr));
                    Map<String, Object> rowWithIndex = new LinkedHashMap<>(row);
                    rowWithIndex.put("_row_index", (double) rowIdx);
                    List<Object> results = Evaluator.evaluate(rowWithIndex, ast, evalCtx);
                    Object value = results.isEmpty() ? null : results.get(0);
                    if (filterNull && value == null) { skip = true; break; }
                    newRow.put(spec.name, value);
                } catch (Exception e) {
                    newRow.put(spec.name, null);
                }
            }
            if (!skip) newRows.add(newRow);
        }
        return new ExecResult(CsvUtils.writeCsv(headers, newRows), "", 0);
    }

    private ExecResult cmdTransform(List<String> args, CommandContext ctx) {
        String targetCol = null;
        String transformExpr = null;
        String rename = null;
        List<String> fileArgs = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if ((a.equals("-r") || a.equals("--rename")) && i + 1 < args.size()) rename = args.get(++i);
            else if (!a.startsWith("-")) {
                if (targetCol == null) targetCol = a;
                else if (transformExpr == null) transformExpr = a;
                else fileArgs.add(a);
            }
        }
        if (targetCol == null || transformExpr == null) {
            return new ExecResult("", "xan transform: usage: xan transform COLUMN EXPR [FILE]\n", 1);
        }
        CsvUtils.Result res = CsvUtils.readInput(fileArgs, ctx);
        if (res.error() != null) return res.error();
        List<String> headers = res.table().headers();
        List<String> targetCols = List.of(targetCol.split(","));
        List<String> renameCols = rename != null ? List.of(rename.split(",")) : List.of();
        for (String col : targetCols) {
            if (!headers.contains(col.trim())) {
                return new ExecResult("", "xan transform: column '" + col.trim() + "' not found\n", 1);
            }
        }

        EvalContext evalCtx = EvalContext.create(ctx.env(), new EvalContext.Limits());
        List<String> newHeaders = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            String h = headers.get(i);
            int idx = targetCols.indexOf(h);
            if (idx != -1 && idx < renameCols.size()) newHeaders.add(renameCols.get(idx).trim());
            else newHeaders.add(h);
        }

        List<Map<String, Object>> newRows = new ArrayList<>();
        for (Map<String, Object> row : res.table().rows()) {
            Map<String, Object> newRow = new LinkedHashMap<>(row);
            for (int i = 0; i < targetCols.size(); i++) {
                String col = targetCols.get(i).trim();
                try {
                    AstNode ast = Parser.parse(toJqExpr(transformExpr));
                    Map<String, Object> rowWithUnderscore = new LinkedHashMap<>(row);
                    rowWithUnderscore.put("_", row.get(col));
                    List<Object> results = Evaluator.evaluate(rowWithUnderscore, ast, evalCtx);
                    Object value = results.isEmpty() ? null : results.get(0);
                    String newColName = (i < renameCols.size()) ? renameCols.get(i).trim() : col;
                    if (!newColName.equals(col)) newRow.remove(col);
                    newRow.put(newColName, value);
                } catch (Exception e) {
                    // keep original
                }
            }
            newRows.add(newRow);
        }
        return new ExecResult(CsvUtils.writeCsv(newHeaders, newRows), "", 0);
    }

    private ExecResult cmdFlatmap(List<String> args, CommandContext ctx) {
        String expr = null;
        List<String> fileArgs = new ArrayList<>();
        for (String a : args) {
            if (!a.startsWith("-")) {
                if (expr == null) expr = a;
                else fileArgs.add(a);
            }
        }
        if (expr == null) return new ExecResult("", "xan flatmap: no expression specified\n", 1);
        CsvUtils.Result res = CsvUtils.readInput(fileArgs, ctx);
        if (res.error() != null) return res.error();

        EvalContext evalCtx = EvalContext.create(ctx.env(), new EvalContext.Limits());
        List<MapSpec> specs = parseMapSpecs(expr);
        List<String> headers = new ArrayList<>(res.table().headers());
        for (MapSpec spec : specs) {
            if (!headers.contains(spec.name)) headers.add(spec.name);
        }

        List<Map<String, Object>> newRows = new ArrayList<>();
        for (Map<String, Object> row : res.table().rows()) {
            List<List<Object>> resultsList = new ArrayList<>();
            int maxLen = 1;
            for (MapSpec spec : specs) {
                try {
                    AstNode ast = Parser.parse(toJqExpr(spec.expr));
                    List<Object> evalResults = Evaluator.evaluate(row, ast, evalCtx);
                    List<Object> expanded = (evalResults.size() == 1 && evalResults.get(0) instanceof List<?>)
                        ? new ArrayList<>((List<?>) evalResults.get(0)) : new ArrayList<>(evalResults);
                    resultsList.add(expanded);
                    maxLen = Math.max(maxLen, expanded.size());
                } catch (Exception e) {
                    resultsList.add(List.of());
                }
            }
            for (int i = 0; i < maxLen; i++) {
                Map<String, Object> newRow = new LinkedHashMap<>(row);
                for (int j = 0; j < specs.size(); j++) {
                    List<Object> r = resultsList.get(j);
                    Object val = i < r.size() ? r.get(i) : null;
                    newRow.put(specs.get(j).name, val);
                }
                newRows.add(newRow);
            }
        }
        return new ExecResult(CsvUtils.writeCsv(headers, newRows), "", 0);
    }

    // ========== Aggregation commands ==========

    private ExecResult cmdAgg(List<String> args, CommandContext ctx) {
        String expr = null;
        List<String> fileArgs = new ArrayList<>();
        for (String a : args) {
            if (!a.startsWith("-")) {
                if (expr == null) expr = a;
                else fileArgs.add(a);
            }
        }
        if (expr == null) return new ExecResult("", "xan agg: no aggregation expression\n", 1);
        CsvUtils.Result res = CsvUtils.readInput(fileArgs, ctx);
        if (res.error() != null) return res.error();

        EvalContext evalCtx = EvalContext.create(ctx.env(), new EvalContext.Limits());
        List<AggParser.AggSpec> specs = AggParser.parseAggExpr(expr);
        List<String> headers = specs.stream().map(AggParser.AggSpec::alias).toList();
        Map<String, Object> row = AggParser.buildAggRow(res.table().rows(), specs, evalCtx);
        return new ExecResult(CsvUtils.writeCsv(headers, List.of(row)), "", 0);
    }

    private ExecResult cmdGroupby(List<String> args, CommandContext ctx) {
        String groupCols = null;
        String aggExpr = null;
        List<String> fileArgs = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if (a.equals("--sorted")) {
                // no-op
            } else if (!a.startsWith("-")) {
                if (groupCols == null) groupCols = a;
                else if (aggExpr == null) aggExpr = a;
                else fileArgs.add(a);
            }
        }
        if (groupCols == null || aggExpr == null) {
            return new ExecResult("", "xan groupby: usage: xan groupby COLS EXPR [FILE]\n", 1);
        }
        CsvUtils.Result res = CsvUtils.readInput(fileArgs, ctx);
        if (res.error() != null) return res.error();

        EvalContext evalCtx = EvalContext.create(ctx.env(), new EvalContext.Limits());
        List<String> groupKeys = List.of(groupCols.split(","));
        List<AggParser.AggSpec> specs = AggParser.parseAggExpr(aggExpr);

        List<String> groupOrder = new ArrayList<>();
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (Map<String, Object> row : res.table().rows()) {
            StringBuilder key = new StringBuilder();
            for (String k : groupKeys) {
                if (key.length() > 0) key.append("\0");
                key.append(String.valueOf(row.get(k)));
            }
            String k = key.toString();
            if (!groups.containsKey(k)) {
                groups.put(k, new ArrayList<>());
                groupOrder.add(k);
            }
            groups.get(k).add(row);
        }

        List<String> headers = new ArrayList<>(groupKeys);
        headers.addAll(specs.stream().map(AggParser.AggSpec::alias).toList());
        List<Map<String, Object>> newRows = new ArrayList<>();
        for (String key : groupOrder) {
            List<Map<String, Object>> groupData = groups.get(key);
            Map<String, Object> row = new LinkedHashMap<>();
            for (String k : groupKeys) row.put(k, groupData.get(0).get(k));
            for (AggParser.AggSpec spec : specs) {
                row.put(spec.alias(), AggParser.computeAgg(groupData, spec, evalCtx));
            }
            newRows.add(row);
        }
        return new ExecResult(CsvUtils.writeCsv(headers, newRows), "", 0);
    }

    private ExecResult cmdFrequency(List<String> args, CommandContext ctx) {
        List<String> selectCols = new ArrayList<>();
        String groupCol = null;
        int limit = 10;
        boolean noExtra = false;
        boolean all = false;
        List<String> fileArgs = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if ((a.equals("-s") || a.equals("--select")) && i + 1 < args.size()) {
                selectCols = List.of(args.get(++i).split(","));
            } else if ((a.equals("-g") || a.equals("--groupby")) && i + 1 < args.size()) {
                groupCol = args.get(++i);
            } else if ((a.equals("-l") || a.equals("--limit")) && i + 1 < args.size()) {
                limit = Integer.parseInt(args.get(++i));
            } else if (a.equals("--no-extra")) noExtra = true;
            else if (a.equals("-A") || a.equals("--all")) all = true;
            else if (!a.startsWith("-")) fileArgs.add(a);
        }
        if (all) limit = 0;
        CsvUtils.Result res = CsvUtils.readInput(fileArgs, ctx);
        if (res.error() != null) return res.error();

        List<String> headers = res.table().headers();
        final String finalGroupCol = groupCol;
        final List<String> finalSelectCols = selectCols;
        List<String> targetCols = !finalSelectCols.isEmpty() ? finalSelectCols.stream().map(String::trim).toList()
            : headers.stream().filter(h -> !h.equals(finalGroupCol)).toList();
        if (finalGroupCol != null && finalSelectCols.isEmpty()) {
            targetCols = headers.stream().filter(h -> !h.equals(finalGroupCol)).toList();
        }

        List<String> resultHeaders = finalGroupCol != null
            ? List.of("field", finalGroupCol, "value", "count")
            : List.of("field", "value", "count");
        List<Map<String, Object>> newRows = new ArrayList<>();

        if (finalGroupCol != null) {
            Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
            for (Map<String, Object> row : res.table().rows()) {
                String key = String.valueOf(row.getOrDefault(finalGroupCol, ""));
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
            }
            for (String col : targetCols) {
                for (Map.Entry<String, List<Map<String, Object>>> entry : groups.entrySet()) {
                    Map<String, Integer> counts = new HashMap<>();
                    for (Map<String, Object> row : entry.getValue()) {
                        Object val = row.get(col);
                        String key = val == null || val.equals("") ? "" : String.valueOf(val);
                        counts.put(key, counts.getOrDefault(key, 0) + 1);
                    }
                    addFreqRows(newRows, col, counts, limit, noExtra, finalGroupCol, entry.getKey());
                }
            }
        } else {
            for (String col : targetCols) {
                Map<String, Integer> counts = new HashMap<>();
                for (Map<String, Object> row : res.table().rows()) {
                    Object val = row.get(col);
                    String key = val == null || val.equals("") ? "" : String.valueOf(val);
                    counts.put(key, counts.getOrDefault(key, 0) + 1);
                }
                addFreqRows(newRows, col, counts, limit, noExtra, null, null);
            }
        }
        return new ExecResult(CsvUtils.writeCsv(resultHeaders, newRows), "", 0);
    }

    private void addFreqRows(List<Map<String, Object>> rows, String col, Map<String, Integer> counts,
                              int limit, boolean noExtra, String groupCol, String groupVal) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort((a, b) -> {
            if (!b.getValue().equals(a.getValue())) return b.getValue() - a.getValue();
            return a.getKey().compareTo(b.getKey());
        });
        if (noExtra) entries = entries.stream().filter(e -> !e.getKey().isEmpty()).toList();
        if (limit > 0) entries = entries.subList(0, Math.min(limit, entries.size()));
        for (Map.Entry<String, Integer> e : entries) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("field", col);
            if (groupCol != null) row.put(groupCol, groupVal);
            row.put("value", e.getKey().isEmpty() ? "<empty>" : e.getKey());
            row.put("count", (double) e.getValue());
            rows.add(row);
        }
    }

    private ExecResult cmdStats(List<String> args, CommandContext ctx) {
        List<String> columns = new ArrayList<>();
        List<String> fileArgs = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if (a.equals("-s") && i + 1 < args.size()) columns = List.of(args.get(++i).split(","));
            else if (!a.startsWith("-")) fileArgs.add(a);
        }
        CsvUtils.Result res = CsvUtils.readInput(fileArgs, ctx);
        if (res.error() != null) return res.error();
        List<String> targetCols = !columns.isEmpty() ? columns.stream().map(String::trim).toList() : res.table().headers();
        List<String> statsHeaders = List.of("field", "type", "count", "min", "max", "mean");
        List<Map<String, Object>> newRows = new ArrayList<>();
        for (String col : targetCols) {
            List<Object> values = new ArrayList<>();
            for (Map<String, Object> row : res.table().rows()) {
                if (row.get(col) != null) values.add(row.get(col));
            }
            List<Double> nums = new ArrayList<>();
            for (Object v : values) {
                Double n = toDouble(v, null);
                if (n != null) nums.add(n);
            }
            boolean isNumeric = nums.size() == values.size() && nums.size() > 0;
            Map<String, Object> statsRow = new LinkedHashMap<>();
            statsRow.put("field", col);
            statsRow.put("type", isNumeric ? "Number" : "String");
            statsRow.put("count", (double) values.size());
            if (isNumeric) {
                statsRow.put("min", Collections.min(nums));
                statsRow.put("max", Collections.max(nums));
                statsRow.put("mean", nums.stream().mapToDouble(d -> d).average().orElse(0));
            } else {
                statsRow.put("min", "");
                statsRow.put("max", "");
                statsRow.put("mean", "");
            }
            newRows.add(statsRow);
        }
        return new ExecResult(CsvUtils.writeCsv(statsHeaders, newRows), "", 0);
    }

    // ========== Reshape commands ==========

    private ExecResult cmdExplode(List<String> args, CommandContext ctx) {
        String column = null;
        String separator = "|";
        boolean dropEmpty = false;
        String rename = null;
        List<String> fileArgs = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if ((a.equals("-s") || a.equals("--separator")) && i + 1 < args.size()) separator = args.get(++i);
            else if (a.equals("--drop-empty")) dropEmpty = true;
            else if ((a.equals("-r") || a.equals("--rename")) && i + 1 < args.size()) rename = args.get(++i);
            else if (!a.startsWith("-")) {
                if (column == null) column = a;
                else fileArgs.add(a);
            }
        }
        if (column == null) return new ExecResult("", "xan explode: usage: xan explode COLUMN [FILE]\n", 1);
        CsvUtils.Result res = CsvUtils.readInput(fileArgs, ctx);
        if (res.error() != null) return res.error();
        if (!res.table().headers().contains(column)) {
            return new ExecResult("", "xan explode: column '" + column + "' not found\n", 1);
        }
        final String finalColumn = column;
        final String finalRename = rename;
        List<String> headers = finalRename != null
            ? res.table().headers().stream().map(h -> h.equals(finalColumn) ? finalRename : h).toList()
            : res.table().headers();
        String targetCol = finalRename != null ? finalRename : finalColumn;
        List<Map<String, Object>> newRows = new ArrayList<>();
        for (Map<String, Object> row : res.table().rows()) {
            Object value = row.get(column);
            String strValue = value == null ? "" : String.valueOf(value);
            if (strValue.isEmpty()) {
                if (!dropEmpty) {
                    Map<String, Object> newRow = new LinkedHashMap<>(row);
                    if (rename != null) { newRow.remove(column); newRow.put(targetCol, ""); }
                    newRows.add(newRow);
                }
            } else {
                for (String part : strValue.split(Pattern.quote(separator))) {
                    Map<String, Object> newRow = new LinkedHashMap<>(row);
                    if (rename != null) newRow.remove(column);
                    newRow.put(targetCol, part);
                    newRows.add(newRow);
                }
            }
        }
        return new ExecResult(CsvUtils.writeCsv(headers, newRows), "", 0);
    }

    private ExecResult cmdImplode(List<String> args, CommandContext ctx) {
        String column = null;
        String separator = "|";
        String rename = null;
        List<String> fileArgs = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if ((a.equals("-s") || a.equals("--sep")) && i + 1 < args.size()) separator = args.get(++i);
            else if ((a.equals("-r") || a.equals("--rename")) && i + 1 < args.size()) rename = args.get(++i);
            else if (!a.startsWith("-")) {
                if (column == null) column = a;
                else fileArgs.add(a);
            }
        }
        if (column == null) return new ExecResult("", "xan implode: usage: xan implode COLUMN [FILE]\n", 1);
        CsvUtils.Result res = CsvUtils.readInput(fileArgs, ctx);
        if (res.error() != null) return res.error();
        if (!res.table().headers().contains(column)) {
            return new ExecResult("", "xan implode: column '" + column + "' not found\n", 1);
        }
        final String finalColumn = column;
        final String finalRename = rename;
        List<String> keyCols = res.table().headers().stream().filter(h -> !h.equals(finalColumn)).toList();
        List<String> headers = finalRename != null
            ? res.table().headers().stream().map(h -> h.equals(finalColumn) ? finalRename : h).toList()
            : res.table().headers();
        String targetCol = finalRename != null ? finalRename : finalColumn;
        List<Map<String, Object>> newRows = new ArrayList<>();
        String currentKey = null;
        List<String> currentValues = new ArrayList<>();
        Map<String, Object> currentRow = null;
        for (Map<String, Object> row : res.table().rows()) {
            StringBuilder key = new StringBuilder();
            for (String k : keyCols) {
                if (key.length() > 0) key.append("\0");
                key.append(String.valueOf(row.getOrDefault(k, "")));
            }
            String k = key.toString();
            Object value = row.get(column);
            String strValue = value == null ? "" : String.valueOf(value);
            if (!k.equals(currentKey)) {
                if (currentRow != null) {
                    Map<String, Object> newRow = new LinkedHashMap<>(currentRow);
                    if (rename != null) newRow.remove(column);
                    newRow.put(targetCol, String.join(separator, currentValues));
                    newRows.add(newRow);
                }
                currentKey = k;
                currentValues = new ArrayList<>();
                currentValues.add(strValue);
                currentRow = row;
            } else {
                currentValues.add(strValue);
            }
        }
        if (currentRow != null) {
            Map<String, Object> newRow = new LinkedHashMap<>(currentRow);
            if (rename != null) newRow.remove(column);
            newRow.put(targetCol, String.join(separator, currentValues));
            newRows.add(newRow);
        }
        return new ExecResult(CsvUtils.writeCsv(headers, newRows), "", 0);
    }

    private ExecResult cmdJoin(List<String> args, CommandContext ctx) {
        String key1 = null, file1 = null, key2 = null, file2 = null;
        String joinType = "inner";
        String defaultValue = "";
        int posCount = 0;
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if (a.equals("--left")) joinType = "left";
            else if (a.equals("--right")) joinType = "right";
            else if (a.equals("--full")) joinType = "full";
            else if ((a.equals("-D") || a.equals("--default")) && i + 1 < args.size()) defaultValue = args.get(++i);
            else if (!a.startsWith("-")) {
                posCount++;
                if (posCount == 1) key1 = a;
                else if (posCount == 2) file1 = a;
                else if (posCount == 3) key2 = a;
                else if (posCount == 4) file2 = a;
            }
        }
        if (key1 == null || file1 == null || key2 == null || file2 == null) {
            return new ExecResult("", "xan join: usage: xan join KEY1 FILE1 KEY2 FILE2 [OPTIONS]\n", 1);
        }
        CsvUtils.Result res1 = CsvUtils.readInput(List.of(file1), ctx);
        if (res1.error() != null) return res1.error();
        CsvUtils.Result res2 = CsvUtils.readInput(List.of(file2), ctx);
        if (res2.error() != null) return res2.error();
        if (!res1.table().headers().contains(key1)) {
            return new ExecResult("", "xan join: column '" + key1 + "' not found in first file\n", 1);
        }
        if (!res2.table().headers().contains(key2)) {
            return new ExecResult("", "xan join: column '" + key2 + "' not found in second file\n", 1);
        }

        List<String> headers1 = res1.table().headers();
        List<String> headers2 = res2.table().headers();
        Set<String> headers1Set = new HashSet<>(headers1);
        List<String> headers2Unique = headers2.stream().filter(h -> !headers1Set.contains(h)).toList();
        List<String> newHeaders = new ArrayList<>(headers1);
        newHeaders.addAll(headers2Unique);

        Map<String, List<Map<String, Object>>> index2 = new HashMap<>();
        for (Map<String, Object> row : res2.table().rows()) {
            String keyVal = String.valueOf(row.getOrDefault(key2, ""));
            index2.computeIfAbsent(keyVal, k -> new ArrayList<>()).add(row);
        }

        List<Map<String, Object>> newRows = new ArrayList<>();
        Set<String> matched2 = new HashSet<>();
        for (Map<String, Object> row1 : res1.table().rows()) {
            String keyVal = String.valueOf(row1.getOrDefault(key1, ""));
            List<Map<String, Object>> matches = index2.get(keyVal);
            if (matches != null && !matches.isEmpty()) {
                matched2.add(keyVal);
                for (Map<String, Object> row2 : matches) {
                    Map<String, Object> newRow = new LinkedHashMap<>();
                    for (String h : headers1) newRow.put(h, row1.get(h));
                    for (String h : headers2Unique) newRow.put(h, row2.get(h));
                    newRows.add(newRow);
                }
            } else if (joinType.equals("left") || joinType.equals("full")) {
                Map<String, Object> newRow = new LinkedHashMap<>();
                for (String h : headers1) newRow.put(h, row1.get(h));
                for (String h : headers2Unique) newRow.put(h, defaultValue);
                newRows.add(newRow);
            }
        }
        if (joinType.equals("right") || joinType.equals("full")) {
            for (Map<String, Object> row2 : res2.table().rows()) {
                String keyVal = String.valueOf(row2.getOrDefault(key2, ""));
                if (!matched2.contains(keyVal)) {
                    Map<String, Object> newRow = new LinkedHashMap<>();
                    for (String h : headers1) newRow.put(h, headers2.contains(h) ? row2.get(h) : defaultValue);
                    for (String h : headers2Unique) newRow.put(h, row2.get(h));
                    newRows.add(newRow);
                }
            }
        }
        return new ExecResult(CsvUtils.writeCsv(newHeaders, newRows), "", 0);
    }

    private ExecResult cmdPivot(List<String> args, CommandContext ctx) {
        String pivotCol = null;
        String aggExpr = null;
        List<String> groupCols = new ArrayList<>();
        List<String> fileArgs = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if ((a.equals("-g") || a.equals("--groupby")) && i + 1 < args.size()) {
                groupCols = List.of(args.get(++i).split(","));
            } else if (!a.startsWith("-")) {
                if (pivotCol == null) pivotCol = a;
                else if (aggExpr == null) aggExpr = a;
                else fileArgs.add(a);
            }
        }
        if (pivotCol == null || aggExpr == null) {
            return new ExecResult("", "xan pivot: usage: xan pivot COLUMN AGG_EXPR [OPTIONS] [FILE]\n", 1);
        }
        CsvUtils.Result res = CsvUtils.readInput(fileArgs, ctx);
        if (res.error() != null) return res.error();
        if (!res.table().headers().contains(pivotCol)) {
            return new ExecResult("", "xan pivot: column '" + pivotCol + "' not found\n", 1);
        }
        var aggMatch = java.util.regex.Pattern.compile("^(\\w+)\\((\\w+)\\)$").matcher(aggExpr);
        if (!aggMatch.matches()) {
            return new ExecResult("", "xan pivot: invalid aggregation expression '" + aggExpr + "'\n", 1);
        }
        String aggFunc = aggMatch.group(1);
        String aggCol = aggMatch.group(2);
        final String finalPivotCol = pivotCol;
        final String finalAggCol = aggCol;
        if (groupCols.isEmpty()) {
            groupCols = res.table().headers().stream().filter(h -> !h.equals(finalPivotCol) && !h.equals(finalAggCol)).toList();
        }

        List<String> pivotValues = new ArrayList<>();
        for (Map<String, Object> row : res.table().rows()) {
            String val = String.valueOf(row.getOrDefault(pivotCol, ""));
            if (!pivotValues.contains(val)) pivotValues.add(val);
        }

        List<String> groupOrder = new ArrayList<>();
        Map<String, Map<String, List<Object>>> groups = new LinkedHashMap<>();
        for (Map<String, Object> row : res.table().rows()) {
            StringBuilder key = new StringBuilder();
            for (String g : groupCols) {
                if (key.length() > 0) key.append("\0");
                key.append(String.valueOf(row.getOrDefault(g, "")));
            }
            String k = key.toString();
            if (!groups.containsKey(k)) { groups.put(k, new LinkedHashMap<>()); groupOrder.add(k); }
            String pv = String.valueOf(row.getOrDefault(pivotCol, ""));
            groups.get(k).computeIfAbsent(pv, p -> new ArrayList<>()).add(row.get(aggCol));
        }

        List<String> newHeaders = new ArrayList<>(groupCols);
        newHeaders.addAll(pivotValues);
        List<Map<String, Object>> newRows = new ArrayList<>();
        for (String key : groupOrder) {
            Map<String, Object> row = new LinkedHashMap<>();
            String[] parts = key.split("\0");
            for (int i = 0; i < groupCols.size(); i++) row.put(groupCols.get(i), parts[i]);
            Map<String, List<Object>> group = groups.get(key);
            for (String pv : pivotValues) {
                row.put(pv, computeSimpleAgg(aggFunc, group.getOrDefault(pv, List.of())));
            }
            newRows.add(row);
        }
        return new ExecResult(CsvUtils.writeCsv(newHeaders, newRows), "", 0);
    }

    private ExecResult cmdMerge(List<String> args, CommandContext ctx) {
        String sortCol = null;
        List<String> fileArgs = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if ((a.equals("-s") || a.equals("--sort")) && i + 1 < args.size()) sortCol = args.get(++i);
            else if (!a.startsWith("-")) fileArgs.add(a);
        }
        if (fileArgs.size() < 2) {
            return new ExecResult("", "xan merge: usage: xan merge [OPTIONS] FILE1 FILE2 ...\n", 1);
        }
        List<CsvUtils.CsvTable> tables = new ArrayList<>();
        List<String> commonHeaders = null;
        for (String file : fileArgs) {
            CsvUtils.Result res = CsvUtils.readInput(List.of(file), ctx);
            if (res.error() != null) return res.error();
            if (commonHeaders == null) commonHeaders = res.table().headers();
            else if (!commonHeaders.equals(res.table().headers())) {
                return new ExecResult("", "xan merge: all files must have the same headers\n", 1);
            }
            tables.add(res.table());
        }
        if (commonHeaders == null) return new ExecResult("", "", 0);
        List<Map<String, Object>> merged = new ArrayList<>();
        for (CsvUtils.CsvTable t : tables) merged.addAll(t.rows());
        if (sortCol != null) {
            if (!commonHeaders.contains(sortCol)) {
                return new ExecResult("", "xan merge: column '" + sortCol + "' not found\n", 1);
            }
            final String finalSortCol = sortCol;
            merged.sort((a, b) -> {
                double na = toDouble(a.get(finalSortCol), 0);
                double nb = toDouble(b.get(finalSortCol), 0);
                return Double.compare(na, nb);
            });
        }
        return new ExecResult(CsvUtils.writeCsv(commonHeaders, merged), "", 0);
    }

    // ========== Data conversion ==========

    private ExecResult cmdTranspose(List<String> args, CommandContext ctx) {
        List<String> fileArgs = args.stream().filter(a -> !a.startsWith("-")).toList();
        CsvUtils.Result res = CsvUtils.readInput(fileArgs, ctx);
        if (res.error() != null) return res.error();
        List<String> headers = res.table().headers();
        List<Map<String, Object>> rows = res.table().rows();
        if (rows.isEmpty()) {
            return new ExecResult(CsvUtils.writeCsv(List.of("column"), List.of()), "", 0);
        }
        String firstCol = headers.get(0);
        List<String> newHeaders = new ArrayList<>();
        newHeaders.add(firstCol);
        for (int i = 0; i < rows.size(); i++) {
            newHeaders.add(String.valueOf(rows.get(i).getOrDefault(firstCol, "row_" + i)));
        }
        List<Map<String, Object>> newRows = new ArrayList<>();
        for (int i = 1; i < headers.size(); i++) {
            String col = headers.get(i);
            Map<String, Object> newRow = new LinkedHashMap<>();
            newRow.put(firstCol, col);
            for (int j = 0; j < rows.size(); j++) {
                newRow.put(newHeaders.get(j + 1), rows.get(j).get(col));
            }
            newRows.add(newRow);
        }
        return new ExecResult(CsvUtils.writeCsv(newHeaders, newRows), "", 0);
    }

    private ExecResult cmdShuffle(List<String> args, CommandContext ctx) {
        Integer seed = null;
        List<String> fileArgs = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if (a.equals("--seed") && i + 1 < args.size()) seed = Integer.parseInt(args.get(++i));
            else if (!a.startsWith("-")) fileArgs.add(a);
        }
        CsvUtils.Result res = CsvUtils.readInput(fileArgs, ctx);
        if (res.error() != null) return res.error();
        List<Map<String, Object>> rows = new ArrayList<>(res.table().rows());
        long rng = seed != null ? seed : System.currentTimeMillis();
        for (int i = rows.size() - 1; i > 0; i--) {
            rng = (rng * 1103515245 + 12345) & 0x7fffffffL;
            int j = (int) (rng % (i + 1));
            Collections.swap(rows, i, j);
        }
        return new ExecResult(CsvUtils.writeCsv(res.table().headers(), rows), "", 0);
    }

    private ExecResult cmdFixlengths(List<String> args, CommandContext ctx) {
        Integer targetLen = null;
        String defaultValue = "";
        List<String> fileArgs = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if ((a.equals("-l") || a.equals("--length")) && i + 1 < args.size()) targetLen = Integer.parseInt(args.get(++i));
            else if ((a.equals("-d") || a.equals("--default")) && i + 1 < args.size()) defaultValue = args.get(++i);
            else if (!a.startsWith("-")) fileArgs.add(a);
        }
        String file = fileArgs.isEmpty() ? null : fileArgs.get(0);
        String input;
        if (file == null || file.equals("-")) {
            input = ctx.stdin().decodeUtf8();
        } else {
            try {
                input = ctx.fs().readFile(ctx.fs().resolvePath(ctx.cwd(), file)).join();
            } catch (Exception e) {
                return new ExecResult("", "xan fixlengths: " + file + ": No such file or directory\n", 1);
            }
        }
        List<List<String>> rows = new ArrayList<>();
        for (String line : input.trim().split("\n")) {
            if (line.trim().isEmpty()) continue;
            List<String> fields = new ArrayList<>();
            for (String f : line.split(",")) fields.add(f);
            rows.add(fields);
        }
        if (rows.isEmpty()) return new ExecResult("", "", 0);
        int maxLen = rows.stream().mapToInt(List::size).max().orElse(0);
        int len = targetLen != null ? targetLen : maxLen;
        List<List<String>> fixed = new ArrayList<>();
        for (List<String> row : rows) {
            if (row.size() == len) fixed.add(row);
            else if (row.size() < len) {
                List<String> padded = new ArrayList<>(row);
                while (padded.size() < len) padded.add(defaultValue);
                fixed.add(padded);
            } else fixed.add(row.subList(0, len));
        }
        return new ExecResult(CsvUtils.writeRawCsv(fixed), "", 0);
    }

    private ExecResult cmdSplit(List<String> args, CommandContext ctx) {
        Integer numParts = null, partSize = null;
        String outputDir = ".";
        List<String> fileArgs = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if ((a.equals("-c") || a.equals("--chunks")) && i + 1 < args.size()) numParts = Integer.parseInt(args.get(++i));
            else if ((a.equals("-S") || a.equals("--size")) && i + 1 < args.size()) partSize = Integer.parseInt(args.get(++i));
            else if ((a.equals("-o") || a.equals("--output")) && i + 1 < args.size()) outputDir = args.get(++i);
            else if (!a.startsWith("-")) fileArgs.add(a);
        }
        if (numParts == null && partSize == null) {
            return new ExecResult("", "xan split: must specify -c or -S\n", 1);
        }
        CsvUtils.Result res = CsvUtils.readInput(fileArgs, ctx);
        if (res.error() != null) return res.error();
        List<Map<String, Object>> data = res.table().rows();
        List<List<Map<String, Object>>> parts = new ArrayList<>();
        if (numParts != null) {
            int size = (int) Math.ceil((double) data.size() / numParts);
            for (int i = 0; i < numParts; i++) {
                parts.add(data.subList(i * size, Math.min((i + 1) * size, data.size())));
            }
        } else {
            for (int i = 0; i < data.size(); i += partSize) {
                parts.add(data.subList(i, Math.min(i + partSize, data.size())));
            }
        }
        String baseName = fileArgs.isEmpty() ? "part" : fileArgs.get(0).replaceAll("\\.csv$", "");
        try {
            String outPath = ctx.fs().resolvePath(ctx.cwd(), outputDir);
            int count = 0;
            for (int i = 0; i < parts.size(); i++) {
                if (parts.get(i).isEmpty()) continue;
                String fileName = String.format("%s_%03d.csv", baseName, i + 1);
                ctx.fs().writeFile(ctx.fs().resolvePath(outPath, fileName),
                    new com.justbash.fs.IFileSystem.StringContent(CsvUtils.writeCsv(res.table().headers(), parts.get(i)))).join();
                count++;
            }
            return new ExecResult("Split into " + count + " parts\n", "", 0);
        } catch (Exception e) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.size(); i++) {
                if (parts.get(i).isEmpty()) continue;
                sb.append("Part ").append(i + 1).append(": ").append(parts.get(i).size()).append(" rows\n");
            }
            return new ExecResult(sb.toString(), "", 0);
        }
    }

    private ExecResult cmdPartition(List<String> args, CommandContext ctx) {
        String column = null;
        String outputDir = ".";
        List<String> fileArgs = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if ((a.equals("-o") || a.equals("--output")) && i + 1 < args.size()) outputDir = args.get(++i);
            else if (!a.startsWith("-")) {
                if (column == null) column = a;
                else fileArgs.add(a);
            }
        }
        if (column == null) return new ExecResult("", "xan partition: usage: xan partition COLUMN [FILE]\n", 1);
        CsvUtils.Result res = CsvUtils.readInput(fileArgs, ctx);
        if (res.error() != null) return res.error();
        if (!res.table().headers().contains(column)) {
            return new ExecResult("", "xan partition: column '" + column + "' not found\n", 1);
        }
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (Map<String, Object> row : res.table().rows()) {
            String val = String.valueOf(row.getOrDefault(column, ""));
            groups.computeIfAbsent(val, k -> new ArrayList<>()).add(row);
        }
        try {
            String outPath = ctx.fs().resolvePath(ctx.cwd(), outputDir);
            for (Map.Entry<String, List<Map<String, Object>>> entry : groups.entrySet()) {
                String safe = entry.getKey().replaceAll("[^a-zA-Z0-9_-]", "_");
                if (safe.isEmpty()) safe = "empty";
                String fileName = safe + ".csv";
                ctx.fs().writeFile(ctx.fs().resolvePath(outPath, fileName),
                    new com.justbash.fs.IFileSystem.StringContent(CsvUtils.writeCsv(res.table().headers(), entry.getValue()))).join();
            }
            return new ExecResult("Partitioned into " + groups.size() + " files by '" + column + "'\n", "", 0);
        } catch (Exception e) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, List<Map<String, Object>>> entry : groups.entrySet()) {
                sb.append(entry.getKey()).append(": ").append(entry.getValue().size()).append(" rows\n");
            }
            return new ExecResult(sb.toString(), "", 0);
        }
    }

    private ExecResult cmdTo(List<String> args, CommandContext ctx) {
        if (args.isEmpty()) return new ExecResult("", "xan to: usage: xan to <format> [FILE]\n", 1);
        String format = args.get(0);
        List<String> fileArgs = args.subList(1, args.size()).stream().filter(a -> !a.startsWith("-")).toList();
        CsvUtils.Result res = CsvUtils.readInput(fileArgs, ctx);
        if (res.error() != null) return res.error();
        if (format.equals("json")) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                return new ExecResult(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(res.table().rows()) + "\n", "", 0);
            } catch (Exception e) {
                return new ExecResult("", "xan to: JSON serialization error\n", 1);
            }
        }
        return new ExecResult("", "xan to: unsupported format '" + format + "'\n", 1);
    }

    private ExecResult cmdFrom(List<String> args, CommandContext ctx) {
        String format = null;
        List<String> fileArgs = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if ((a.equals("-f") || a.equals("--format")) && i + 1 < args.size()) format = args.get(++i);
            else if (!a.startsWith("-")) fileArgs.add(a);
        }
        if (format == null) return new ExecResult("", "xan from: usage: xan from -f <format> [FILE]\n", 1);
        if (!format.equals("json")) return new ExecResult("", "xan from: unsupported format '" + format + "'\n", 1);
        String file = fileArgs.isEmpty() ? null : fileArgs.get(0);
        String input;
        if (file == null || file.equals("-")) {
            input = ctx.stdin().decodeUtf8();
        } else {
            try {
                input = ctx.fs().readFile(ctx.fs().resolvePath(ctx.cwd(), file)).join();
            } catch (Exception e) {
                return new ExecResult("", "xan from: " + file + ": No such file or directory\n", 1);
            }
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            Object data = mapper.readValue(input.trim(), Object.class);
            if (!(data instanceof List<?> list)) {
                return new ExecResult("", "xan from: JSON input must be an array\n", 1);
            }
            if (list.isEmpty()) return new ExecResult("\n", "", 0);
            if (list.get(0) instanceof List<?> firstRow) {
                List<String> headers = new ArrayList<>();
                for (Object o : firstRow) headers.add(String.valueOf(o));
                List<Map<String, Object>> rows = new ArrayList<>();
                for (int i = 1; i < list.size(); i++) {
                    List<?> row = (List<?>) list.get(i);
                    Map<String, Object> map = new LinkedHashMap<>();
                    for (int j = 0; j < headers.size() && j < row.size(); j++) map.put(headers.get(j), row.get(j));
                    rows.add(map);
                }
                return new ExecResult(CsvUtils.writeCsv(headers, rows), "", 0);
            }
            List<String> headers = new ArrayList<>(new TreeSet<>(
                ((Map<?, ?>) list.get(0)).keySet().stream().map(String::valueOf).toList()));
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Object o : list) {
                Map<?, ?> m = (Map<?, ?>) o;
                Map<String, Object> row = new LinkedHashMap<>();
                for (String h : headers) row.put(h, m.get(h));
                rows.add(row);
            }
            return new ExecResult(CsvUtils.writeCsv(headers, rows), "", 0);
        } catch (Exception e) {
            return new ExecResult("", "xan from: invalid JSON input\n", 1);
        }
    }

    // ========== Output commands ==========

    private ExecResult cmdView(List<String> args, CommandContext ctx) {
        int n = 0;
        List<String> fileArgs = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if (a.equals("-n") && i + 1 < args.size()) n = Integer.parseInt(args.get(++i));
            else if (!a.startsWith("-")) fileArgs.add(a);
        }
        CsvUtils.Result res = CsvUtils.readInput(fileArgs, ctx);
        if (res.error() != null) return res.error();
        List<String> headers = res.table().headers();
        List<Map<String, Object>> rows = n > 0 ? res.table().rows().subList(0, Math.min(n, res.table().rows().size())) : res.table().rows();
        if (rows.isEmpty()) return new ExecResult(CsvUtils.writeCsv(headers, rows), "", 0);

        int[] widths = new int[headers.size()];
        for (int i = 0; i < headers.size(); i++) widths[i] = headers.get(i).length();
        for (Map<String, Object> row : rows) {
            for (int i = 0; i < headers.size(); i++) {
                widths[i] = Math.max(widths[i], String.valueOf(row.getOrDefault(headers.get(i), "")).length());
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("┌");
        for (int i = 0; i < widths.length; i++) {
            sb.append("─".repeat(widths[i] + 2));
            if (i < widths.length - 1) sb.append("┬");
        }
        sb.append("┐\n");
        for (int i = 0; i < headers.size(); i++) {
            sb.append("│ ").append(String.format("%-" + widths[i] + "s", headers.get(i))).append(" ");
        }
        sb.append("│\n");
        sb.append("├");
        for (int i = 0; i < widths.length; i++) {
            sb.append("─".repeat(widths[i] + 2));
            if (i < widths.length - 1) sb.append("┼");
        }
        sb.append("┤\n");
        for (Map<String, Object> row : rows) {
            for (int i = 0; i < headers.size(); i++) {
                sb.append("│ ").append(String.format("%-" + widths[i] + "s", String.valueOf(row.getOrDefault(headers.get(i), "")))).append(" ");
            }
            sb.append("│\n");
        }
        sb.append("└");
        for (int i = 0; i < widths.length; i++) {
            sb.append("─".repeat(widths[i] + 2));
            if (i < widths.length - 1) sb.append("┴");
        }
        sb.append("┘\n");
        return new ExecResult(sb.toString(), "", 0);
    }

    private ExecResult cmdFlatten(List<String> args, CommandContext ctx) {
        int limit = 0;
        List<String> selectCols = new ArrayList<>();
        List<String> fileArgs = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if ((a.equals("-l") || a.equals("--limit")) && i + 1 < args.size()) limit = Integer.parseInt(args.get(++i));
            else if ((a.equals("-s") || a.equals("--select")) && i + 1 < args.size()) selectCols = List.of(args.get(++i).split(","));
            else if (!a.startsWith("-")) fileArgs.add(a);
        }
        CsvUtils.Result res = CsvUtils.readInput(fileArgs, ctx);
        if (res.error() != null) return res.error();
        List<String> headers = res.table().headers();
        List<String> displayCols = !selectCols.isEmpty()
            ? selectCols.stream().filter(headers::contains).map(String::trim).toList() : headers;
        List<Map<String, Object>> rows = limit > 0 ? res.table().rows().subList(0, Math.min(limit, res.table().rows().size())) : res.table().rows();
        int maxWidth = displayCols.stream().mapToInt(String::length).max().orElse(0);
        String sep = "─".repeat(80);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            sb.append("Row n°").append(i).append("\n").append(sep).append("\n");
            for (String h : displayCols) {
                sb.append(String.format("%-" + maxWidth + "s %s\n", h, String.valueOf(rows.get(i).getOrDefault(h, ""))));
            }
            if (i < rows.size() - 1) sb.append("\n");
        }
        return new ExecResult(sb.toString(), "", 0);
    }

    private ExecResult cmdFmt(List<String> args, CommandContext ctx) {
        return cmdView(args, ctx);
    }

    // ========== Multi-file ==========

    private ExecResult cmdCat(List<String> args, CommandContext ctx) {
        boolean pad = false;
        List<String> fileArgs = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            if (a.equals("-p") || a.equals("--pad")) pad = true;
            else if (!a.startsWith("-")) fileArgs.add(a);
        }
        if (fileArgs.isEmpty()) return new ExecResult("", "xan cat: no files specified\n", 1);
        List<CsvUtils.CsvTable> tables = new ArrayList<>();
        List<String> allHeaders = new ArrayList<>();
        for (String file : fileArgs) {
            CsvUtils.Result res = CsvUtils.readInput(List.of(file), ctx);
            if (res.error() != null) {
                return new ExecResult("", res.error().stderr(), res.error().exitCode());
            }
            tables.add(res.table());
            for (String h : res.table().headers()) {
                if (!allHeaders.contains(h)) allHeaders.add(h);
            }
        }
        if (!pad) {
            List<String> firstHeaders = tables.get(0).headers();
            for (int i = 1; i < tables.size(); i++) {
                if (!tables.get(i).headers().equals(firstHeaders)) {
                    return new ExecResult("", "xan cat: headers do not match (use -p to pad)\n", 1);
                }
            }
            allHeaders = firstHeaders;
        }
        List<Map<String, Object>> allData = new ArrayList<>();
        for (CsvUtils.CsvTable t : tables) {
            for (Map<String, Object> row : t.rows()) {
                Map<String, Object> newRow = new LinkedHashMap<>();
                for (String h : allHeaders) {
                    newRow.put(h, t.headers().contains(h) ? row.get(h) : "");
                }
                allData.add(newRow);
            }
        }
        return new ExecResult(CsvUtils.writeCsv(allHeaders, allData), "", 0);
    }

    // ========== Helpers ==========

    private String toJqExpr(String expr) {
        expr = expr.trim();
        if (expr.isEmpty()) return ".";
        char first = expr.charAt(0);
        if (first != '.' && first != '[' && Character.isLetterOrDigit(first)) {
            return "." + expr;
        }
        return expr;
    }

    private record MapSpec(String expr, String name) {}

    private List<MapSpec> parseMapSpecs(String input) {
        List<MapSpec> specs = new ArrayList<>();
        for (String part : input.split(",")) {
            part = part.trim();
            if (part.isEmpty()) continue;
            int asIdx = part.toLowerCase().indexOf(" as ");
            if (asIdx != -1) {
                String expr = part.substring(0, asIdx).trim();
                String name = part.substring(asIdx + 4).trim();
                specs.add(new MapSpec(expr, name));
            } else {
                specs.add(new MapSpec(part, part));
            }
        }
        if (specs.isEmpty()) specs.add(new MapSpec(input, input));
        return specs;
    }

    private double toDouble(Object value, double defaultVal) {
        if (value == null) return defaultVal;
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private Double toDouble(Object value, Double defaultVal) {
        if (value == null) return defaultVal;
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private Object computeSimpleAgg(String func, List<Object> values) {
        List<Double> nums = new ArrayList<>();
        for (Object v : values) {
            Double n = toDouble(v, null);
            if (n != null) nums.add(n);
        }
        switch (func) {
            case "count": return (double) values.size();
            case "sum": return nums.stream().mapToDouble(d -> d).sum();
            case "mean": case "avg": return nums.isEmpty() ? null : nums.stream().mapToDouble(d -> d).average().orElse(0);
            case "min": return nums.isEmpty() ? null : Collections.min(nums);
            case "max": return nums.isEmpty() ? null : Collections.max(nums);
            case "first": return values.isEmpty() ? null : String.valueOf(values.get(0));
            case "last": return values.isEmpty() ? null : String.valueOf(values.get(values.size() - 1));
            default: return null;
        }
    }
}
