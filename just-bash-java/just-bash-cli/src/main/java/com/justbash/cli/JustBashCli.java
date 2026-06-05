package com.justbash.cli;

import com.justbash.Bash;
import com.justbash.BashExecResult;
import com.justbash.commands.CommandRegistry;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;

public class JustBashCli {

    private static final String VERSION = "0.1.0-SNAPSHOT";

    public static void main(String[] args) {
        try {
            int exitCode = run(args);
            System.exit(exitCode);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    static int run(String[] args) {
        CliOptions opts = parseArgs(args);

        if (opts.help) {
            printHelp();
            return 0;
        }

        if (opts.version) {
            System.out.println("just-bash " + VERSION);
            return 0;
        }

        String script = getScript(opts);
        if (script == null || script.trim().isEmpty()) {
            printHelp();
            return 1;
        }

        // Prepend set -e if errexit is enabled
        if (opts.errexit) {
            script = "set -e\n" + script;
        }

        Bash bash = new Bash();
        CommandRegistry.registerAll(bash);

        try {
            BashExecResult result = bash.exec(script).join();

            if (opts.json) {
                printJson(result);
            } else {
                if (!result.stdout().isEmpty()) {
                    System.out.print(result.stdout());
                }
                if (!result.stderr().isEmpty()) {
                    System.err.print(result.stderr());
                }
            }

            return result.exitCode();
        } finally {
            bash.shutdown();
        }
    }

    private static String getScript(CliOptions opts) {
        if (opts.script != null) {
            return opts.script;
        }

        // Read from stdin
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static void printJson(BashExecResult result) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"stdout\": ").append(escapeJson(result.stdout())).append(",\n");
        json.append("  \"stderr\": ").append(escapeJson(result.stderr())).append(",\n");
        json.append("  \"exitCode\": ").append(result.exitCode()).append("\n");
        json.append("}\n");
        System.out.print(json.toString());
    }

    private static String escapeJson(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    private static CliOptions parseArgs(String[] args) {
        CliOptions opts = new CliOptions();
        int i = 0;
        while (i < args.length) {
            String arg = args[i];
            if (arg.equals("-h") || arg.equals("--help")) {
                opts.help = true;
                i++;
            } else if (arg.equals("-v") || arg.equals("--version")) {
                opts.version = true;
                i++;
            } else if (arg.equals("-c")) {
                if (i + 1 >= args.length) {
                    System.err.println("Error: -c requires a script argument");
                    System.exit(1);
                }
                opts.script = args[i + 1];
                i += 2;
            } else if (arg.equals("-e") || arg.equals("--errexit")) {
                opts.errexit = true;
                i++;
            } else if (arg.equals("--json")) {
                opts.json = true;
                i++;
            } else if (arg.startsWith("-") && arg.length() > 2 && !arg.startsWith("--")) {
                // Combined short flags like -ec
                for (char c : arg.substring(1).toCharArray()) {
                    switch (c) {
                        case 'e' -> opts.errexit = true;
                        case 'h' -> opts.help = true;
                        case 'v' -> opts.version = true;
                        case 'c' -> {
                            if (i + 1 >= args.length) {
                                System.err.println("Error: -c requires a script argument");
                                System.exit(1);
                            }
                            opts.script = args[i + 1];
                            i++;
                        }
                        default -> {
                            System.err.println("Error: Unknown option: -" + c);
                            System.exit(1);
                        }
                    }
                }
                i++;
            } else if (arg.startsWith("-")) {
                System.err.println("Error: Unknown option: " + arg);
                System.exit(1);
            } else {
                // Positional argument treated as script
                if (opts.script == null) {
                    opts.script = arg;
                }
                i++;
            }
        }
        return opts;
    }

    private static void printHelp() {
        System.out.println("""
            just-bash - A secure bash environment for AI agents

            Usage:
              just-bash [options] [script]
              just-bash -c 'script' [options]
              echo 'script' | just-bash [options]

            Options:
              -c <script>       Execute the script from command line argument
              -e, --errexit     Exit immediately if a command exits with non-zero status
              --json            Output results as JSON (stdout, stderr, exitCode)
              -h, --help        Show this help message
              -v, --version     Show version

            Examples:
              # Execute inline script
              just-bash -c 'echo hello'

              # Execute with JSON output
              just-bash -c 'echo hello' --json

              # Pipe script from stdin
              echo 'echo hello world' | just-bash

              # Exit on first error
              just-bash -e -c 'false; echo not reached'
            """);
    }

    private static class CliOptions {
        String script;
        boolean errexit;
        boolean json;
        boolean help;
        boolean version;
    }
}
