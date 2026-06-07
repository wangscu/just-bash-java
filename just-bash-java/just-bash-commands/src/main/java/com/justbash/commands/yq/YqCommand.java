package com.justbash.commands.yq;

import com.justbash.*;
import com.justbash.commands.queryengine.*;
import com.justbash.fs.IFileSystem;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * yq - Command-line YAML/XML/INI/CSV/TOML processor
 *
 * Uses jq-style query expressions to process multi-format data.
 */
public class YqCommand implements Command {

    @Override
    public String name() {
        return "yq";
    }

    private static final String HELP_TEXT = """
        yq - command-line YAML/XML/INI/CSV/TOML processor

        Usage: yq [OPTIONS] [FILTER] [FILE]

        Options:
          -p, --input-format=FMT   input format: yaml (default), xml, json, ini, csv, toml
          -o, --output-format=FMT  output format: yaml (default), json, xml, ini, csv, toml
          -i, --inplace            modify file in-place
          -r, --raw-output         output strings without quotes (json only)
          -c, --compact            compact output (json only)
          -e, --exit-status        set exit status based on output
          -s, --slurp              read entire input into array
          -n, --null-input         don't read any input
          -j, --join-output        don't print newlines after each output
          -f, --front-matter       extract and process front-matter only
          -P, --prettyPrint        pretty print output
          -I, --indent=N           set indent level (default: 2)
              --help               display this help and exit

        Examples:
          yq '.name' config.yaml
          yq -p json -o yaml '.' data.json
          yq -i '.version = "2.0"' config.yaml
        """;

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> run(args, ctx));
    }

    private ExecResult run(List<String> args, CommandContext ctx) {
        if (args.isEmpty() || args.contains("--help") || args.contains("-h")) {
            return new ExecResult(HELP_TEXT, "", 0);
        }

        ParsedArgs parsed = parseArgs(args);
        if (parsed.error != null) {
            return parsed.error;
        }

        QueryFormats.FormatOptions fmtOpts = parsed.formatOptions;

        // Auto-detect format from file extension if not explicitly set
        if (!parsed.inputFormatExplicit && !parsed.files.isEmpty() && !"-".equals(parsed.files.get(0))) {
            QueryFormats.InputFormat detected = QueryFormats.detectFormatFromExtension(parsed.files.get(0));
            if (detected != null) {
                fmtOpts = new QueryFormats.FormatOptions(
                    detected, fmtOpts.outputFormat(), fmtOpts.raw(), fmtOpts.compact(),
                    fmtOpts.prettyPrint(), fmtOpts.indent(), fmtOpts.xmlAttributePrefix(),
                    fmtOpts.xmlContentName(), fmtOpts.csvDelimiter(), fmtOpts.csvHeader()
                );
            }
        }

        // Inplace requires a file
        if (parsed.inplace && (parsed.files.isEmpty() || "-".equals(parsed.files.get(0)))) {
            return new ExecResult("", "yq: -i/--inplace requires a file argument\n", 1);
        }

        // Read input
        String input;
        String filePath = null;
        if (parsed.nullInput) {
            input = "";
        } else if (parsed.files.isEmpty() || (parsed.files.size() == 1 && "-".equals(parsed.files.get(0)))) {
            input = ctx.stdin().decodeUtf8();
        } else {
            filePath = parsed.files.get(0);
            try {
                input = ctx.fs().readFile(ctx.fs().resolvePath(ctx.cwd(), filePath)).join();
            } catch (Exception e) {
                return new ExecResult("", "yq: " + filePath + ": No such file or directory\n", 2);
            }
        }

        try {
            AstNode ast = Parser.parse(parsed.filter);
            EvalContext evalCtx = EvalContext.create(ctx.env(), new EvalContext.Limits());

            List<Object> values;
            if (parsed.nullInput) {
                values = Evaluator.evaluate(null, ast, evalCtx);
            } else if (parsed.frontMatter) {
                QueryFormats.FrontMatterResult fm = QueryFormats.extractFrontMatter(input);
                if (fm == null) {
                    return new ExecResult("", "yq: no front-matter found\n", 1);
                }
                values = Evaluator.evaluate(fm.frontMatter(), ast, evalCtx);
            } else if (parsed.slurp) {
                List<Object> items;
                if (fmtOpts.inputFormat() == QueryFormats.InputFormat.YAML) {
                    items = QueryFormats.parseAllYamlDocuments(input);
                } else {
                    items = List.of(QueryFormats.parseInput(input, fmtOpts.inputFormat()));
                }
                values = Evaluator.evaluate(items, ast, evalCtx);
            } else {
                Object parsedInput = QueryFormats.parseInput(input, fmtOpts.inputFormat());
                values = Evaluator.evaluate(parsedInput, ast, evalCtx);
            }

            // Format output
            StringBuilder output = new StringBuilder();
            for (int i = 0; i < values.size(); i++) {
                String formatted = QueryFormats.formatOutput(values.get(i), fmtOpts.outputFormat(), fmtOpts);
                if (!formatted.isEmpty()) {
                    if (i > 0 && !parsed.joinOutput) output.append("\n");
                    output.append(formatted);
                }
            }
            if (!parsed.joinOutput && output.length() > 0) {
                output.append("\n");
            }

            String finalOutput = output.toString();

            // Handle inplace mode
            if (parsed.inplace && filePath != null) {
                try {
                    ctx.fs().writeFile(ctx.fs().resolvePath(ctx.cwd(), filePath), new com.justbash.fs.IFileSystem.StringContent(finalOutput)).join();
                } catch (Exception e) {
                    return new ExecResult("", "yq: " + e.getMessage() + "\n", 1);
                }
                return new ExecResult("", "", 0);
            }

            int exitCode = parsed.exitStatus && (values.isEmpty() || values.stream().allMatch(
                v -> v == null || Boolean.FALSE.equals(v)
            )) ? 1 : 0;

            return new ExecResult(finalOutput, "", exitCode);

        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("Unknown function")) {
                return new ExecResult("", "yq: error: " + msg + "\n", 3);
            }
            return new ExecResult("", "yq: parse error: " + (msg != null ? msg : e.getClass().getSimpleName()) + "\n", 5);
        }
    }

    private record ParsedArgs(
        QueryFormats.FormatOptions formatOptions,
        String filter,
        List<String> files,
        boolean inputFormatExplicit,
        boolean exitStatus,
        boolean slurp,
        boolean nullInput,
        boolean joinOutput,
        boolean inplace,
        boolean frontMatter,
        ExecResult error
    ) {}

    private ParsedArgs parseArgs(List<String> args) {
        QueryFormats.FormatOptions fmtOpts = new QueryFormats.FormatOptions();
        boolean inputFormatExplicit = false;
        boolean exitStatus = false;
        boolean slurp = false;
        boolean nullInput = false;
        boolean joinOutput = false;
        boolean inplace = false;
        boolean frontMatter = false;

        String filter = ".";
        boolean filterSet = false;
        List<String> files = new ArrayList<>();

        int i = 0;
        while (i < args.size()) {
            String a = args.get(i);

            if (a.startsWith("--input-format=")) {
                String format = a.substring(15);
                if (!QueryFormats.isValidInputFormat(format)) {
                    return new ParsedArgs(null, null, null, false, false, false, false, false, false, false,
                        new ExecResult("", "yq: unknown input format: " + format + "\n", 1));
                }
                fmtOpts = fmtOptsWithInput(fmtOpts, QueryFormats.parseInputFormat(format));
                inputFormatExplicit = true;
            } else if (a.startsWith("--output-format=")) {
                String format = a.substring(16);
                if (!QueryFormats.isValidOutputFormat(format)) {
                    return new ParsedArgs(null, null, null, false, false, false, false, false, false, false,
                        new ExecResult("", "yq: unknown output format: " + format + "\n", 1));
                }
                fmtOpts = fmtOptsWithOutput(fmtOpts, QueryFormats.parseOutputFormat(format));
            } else if (a.startsWith("--indent=")) {
                fmtOpts = fmtOptsWithIndent(fmtOpts, Integer.parseInt(a.substring(9)));
            } else if (a.startsWith("--xml-attribute-prefix=")) {
                fmtOpts = new QueryFormats.FormatOptions(fmtOpts.inputFormat(), fmtOpts.outputFormat(), fmtOpts.raw(),
                    fmtOpts.compact(), fmtOpts.prettyPrint(), fmtOpts.indent(), a.substring(23), fmtOpts.xmlContentName(),
                    fmtOpts.csvDelimiter(), fmtOpts.csvHeader());
            } else if (a.startsWith("--xml-content-name=")) {
                fmtOpts = new QueryFormats.FormatOptions(fmtOpts.inputFormat(), fmtOpts.outputFormat(), fmtOpts.raw(),
                    fmtOpts.compact(), fmtOpts.prettyPrint(), fmtOpts.indent(), fmtOpts.xmlAttributePrefix(), a.substring(19),
                    fmtOpts.csvDelimiter(), fmtOpts.csvHeader());
            } else if (a.startsWith("--csv-delimiter=")) {
                fmtOpts = new QueryFormats.FormatOptions(fmtOpts.inputFormat(), fmtOpts.outputFormat(), fmtOpts.raw(),
                    fmtOpts.compact(), fmtOpts.prettyPrint(), fmtOpts.indent(), fmtOpts.xmlAttributePrefix(),
                    fmtOpts.xmlContentName(), a.substring(16), fmtOpts.csvHeader());
            } else if (a.equals("--csv-header")) {
                fmtOpts = new QueryFormats.FormatOptions(fmtOpts.inputFormat(), fmtOpts.outputFormat(), fmtOpts.raw(),
                    fmtOpts.compact(), fmtOpts.prettyPrint(), fmtOpts.indent(), fmtOpts.xmlAttributePrefix(),
                    fmtOpts.xmlContentName(), fmtOpts.csvDelimiter(), true);
            } else if (a.equals("--no-csv-header")) {
                fmtOpts = new QueryFormats.FormatOptions(fmtOpts.inputFormat(), fmtOpts.outputFormat(), fmtOpts.raw(),
                    fmtOpts.compact(), fmtOpts.prettyPrint(), fmtOpts.indent(), fmtOpts.xmlAttributePrefix(),
                    fmtOpts.xmlContentName(), fmtOpts.csvDelimiter(), false);
            } else if (a.equals("-p") || a.equals("--input-format")) {
                i++;
                if (i >= args.size()) {
                    return new ParsedArgs(null, null, null, false, false, false, false, false, false, false,
                        new ExecResult("", "yq: option requires an argument: " + a + "\n", 1));
                }
                String format = args.get(i);
                if (!QueryFormats.isValidInputFormat(format)) {
                    return new ParsedArgs(null, null, null, false, false, false, false, false, false, false,
                        new ExecResult("", "yq: unknown input format: " + format + "\n", 1));
                }
                fmtOpts = fmtOptsWithInput(fmtOpts, QueryFormats.parseInputFormat(format));
                inputFormatExplicit = true;
            } else if (a.equals("-o") || a.equals("--output-format")) {
                i++;
                if (i >= args.size()) {
                    return new ParsedArgs(null, null, null, false, false, false, false, false, false, false,
                        new ExecResult("", "yq: option requires an argument: " + a + "\n", 1));
                }
                String format = args.get(i);
                if (!QueryFormats.isValidOutputFormat(format)) {
                    return new ParsedArgs(null, null, null, false, false, false, false, false, false, false,
                        new ExecResult("", "yq: unknown output format: " + format + "\n", 1));
                }
                fmtOpts = fmtOptsWithOutput(fmtOpts, QueryFormats.parseOutputFormat(format));
            } else if (a.equals("-I") || a.equals("--indent")) {
                i++;
                if (i >= args.size()) {
                    return new ParsedArgs(null, null, null, false, false, false, false, false, false, false,
                        new ExecResult("", "yq: option requires an argument: " + a + "\n", 1));
                }
                fmtOpts = fmtOptsWithIndent(fmtOpts, Integer.parseInt(args.get(i)));
            } else if (a.equals("-r") || a.equals("--raw-output")) {
                fmtOpts = new QueryFormats.FormatOptions(fmtOpts.inputFormat(), fmtOpts.outputFormat(), true,
                    fmtOpts.compact(), fmtOpts.prettyPrint(), fmtOpts.indent(), fmtOpts.xmlAttributePrefix(),
                    fmtOpts.xmlContentName(), fmtOpts.csvDelimiter(), fmtOpts.csvHeader());
            } else if (a.equals("-c") || a.equals("--compact")) {
                fmtOpts = new QueryFormats.FormatOptions(fmtOpts.inputFormat(), fmtOpts.outputFormat(), fmtOpts.raw(),
                    true, fmtOpts.prettyPrint(), fmtOpts.indent(), fmtOpts.xmlAttributePrefix(),
                    fmtOpts.xmlContentName(), fmtOpts.csvDelimiter(), fmtOpts.csvHeader());
            } else if (a.equals("-e") || a.equals("--exit-status")) {
                exitStatus = true;
            } else if (a.equals("-s") || a.equals("--slurp")) {
                slurp = true;
            } else if (a.equals("-n") || a.equals("--null-input")) {
                nullInput = true;
            } else if (a.equals("-j") || a.equals("--join-output")) {
                joinOutput = true;
            } else if (a.equals("-i") || a.equals("--inplace")) {
                inplace = true;
            } else if (a.equals("-f") || a.equals("--front-matter")) {
                frontMatter = true;
            } else if (a.equals("-P") || a.equals("--prettyPrint")) {
                fmtOpts = new QueryFormats.FormatOptions(fmtOpts.inputFormat(), fmtOpts.outputFormat(), fmtOpts.raw(),
                    fmtOpts.compact(), true, fmtOpts.indent(), fmtOpts.xmlAttributePrefix(),
                    fmtOpts.xmlContentName(), fmtOpts.csvDelimiter(), fmtOpts.csvHeader());
            } else if (a.equals("-")) {
                files.add("-");
            } else if (a.startsWith("--")) {
                return new ParsedArgs(null, null, null, false, false, false, false, false, false, false,
                    new ExecResult("", "yq: unknown option: " + a + "\n", 1));
            } else if (a.startsWith("-")) {
                // Combined short options like -rc
                for (int j = 1; j < a.length(); j++) {
                    char c = a.charAt(j);
                    switch (c) {
                        case 'r' -> fmtOpts = new QueryFormats.FormatOptions(fmtOpts.inputFormat(), fmtOpts.outputFormat(), true,
                            fmtOpts.compact(), fmtOpts.prettyPrint(), fmtOpts.indent(), fmtOpts.xmlAttributePrefix(),
                            fmtOpts.xmlContentName(), fmtOpts.csvDelimiter(), fmtOpts.csvHeader());
                        case 'c' -> fmtOpts = new QueryFormats.FormatOptions(fmtOpts.inputFormat(), fmtOpts.outputFormat(), fmtOpts.raw(),
                            true, fmtOpts.prettyPrint(), fmtOpts.indent(), fmtOpts.xmlAttributePrefix(),
                            fmtOpts.xmlContentName(), fmtOpts.csvDelimiter(), fmtOpts.csvHeader());
                        case 'e' -> exitStatus = true;
                        case 's' -> slurp = true;
                        case 'n' -> nullInput = true;
                        case 'j' -> joinOutput = true;
                        case 'i' -> inplace = true;
                        case 'f' -> frontMatter = true;
                        case 'P' -> fmtOpts = new QueryFormats.FormatOptions(fmtOpts.inputFormat(), fmtOpts.outputFormat(), fmtOpts.raw(),
                            fmtOpts.compact(), true, fmtOpts.indent(), fmtOpts.xmlAttributePrefix(),
                            fmtOpts.xmlContentName(), fmtOpts.csvDelimiter(), fmtOpts.csvHeader());
                        default -> {
                            return new ParsedArgs(null, null, null, false, false, false, false, false, false, false,
                                new ExecResult("", "yq: unknown option: -" + c + "\n", 1));
                        }
                    }
                }
            } else if (!filterSet) {
                filter = a;
                filterSet = true;
            } else {
                files.add(a);
            }
            i++;
        }

        return new ParsedArgs(fmtOpts, filter, files, inputFormatExplicit, exitStatus, slurp, nullInput, joinOutput, inplace, frontMatter, null);
    }

    private QueryFormats.FormatOptions fmtOptsWithInput(QueryFormats.FormatOptions opts, QueryFormats.InputFormat format) {
        return new QueryFormats.FormatOptions(format, opts.outputFormat(), opts.raw(), opts.compact(),
            opts.prettyPrint(), opts.indent(), opts.xmlAttributePrefix(), opts.xmlContentName(),
            opts.csvDelimiter(), opts.csvHeader());
    }

    private QueryFormats.FormatOptions fmtOptsWithOutput(QueryFormats.FormatOptions opts, QueryFormats.OutputFormat format) {
        return new QueryFormats.FormatOptions(opts.inputFormat(), format, opts.raw(), opts.compact(),
            opts.prettyPrint(), opts.indent(), opts.xmlAttributePrefix(), opts.xmlContentName(),
            opts.csvDelimiter(), opts.csvHeader());
    }

    private QueryFormats.FormatOptions fmtOptsWithIndent(QueryFormats.FormatOptions opts, int indent) {
        return new QueryFormats.FormatOptions(opts.inputFormat(), opts.outputFormat(), opts.raw(), opts.compact(),
            opts.prettyPrint(), indent, opts.xmlAttributePrefix(), opts.xmlContentName(),
            opts.csvDelimiter(), opts.csvHeader());
    }
}
