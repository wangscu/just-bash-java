package com.justbash.commands.find;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import com.justbash.fs.FsStat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class FindCommand implements Command {
    @Override
    public String name() { return "find"; }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            if (args.isEmpty()) {
                return new ExecResult("", "find: missing argument\n", 1);
            }

            int argIdx = 0;
            List<String> paths = new ArrayList<>();

            // Collect starting paths
            while (argIdx < args.size() && !args.get(argIdx).startsWith("-")) {
                paths.add(args.get(argIdx));
                argIdx++;
            }

            if (paths.isEmpty()) {
                paths.add(ctx.cwd());
            }

            // Parse predicates
            List<Predicate> predicates = new ArrayList<>();
            Integer maxDepth = null;
            int maxCount = 10000;
            int count = 0;

            while (argIdx < args.size()) {
                String arg = args.get(argIdx);
                switch (arg) {
                    case "-name":
                        if (argIdx + 1 >= args.size()) return new ExecResult("", "find: missing argument to `-name'\n", 1);
                        predicates.add(new NamePredicate(args.get(++argIdx), false));
                        break;
                    case "-iname":
                        if (argIdx + 1 >= args.size()) return new ExecResult("", "find: missing argument to `-iname'\n", 1);
                        predicates.add(new NamePredicate(args.get(++argIdx), true));
                        break;
                    case "-type":
                        if (argIdx + 1 >= args.size()) return new ExecResult("", "find: missing argument to `-type'\n", 1);
                        predicates.add(new TypePredicate(args.get(++argIdx)));
                        break;
                    case "-maxdepth":
                        if (argIdx + 1 >= args.size()) return new ExecResult("", "find: missing argument to `-maxdepth'\n", 1);
                        try {
                            maxDepth = Integer.parseInt(args.get(++argIdx));
                        } catch (NumberFormatException e) {
                            return new ExecResult("", "find: invalid maxdepth\n", 1);
                        }
                        break;
                    case "-print":
                        predicates.add(new PrintPredicate());
                        break;
                    case "-delete":
                        predicates.add(new DeletePredicate());
                        break;
                    case "-exec":
                        if (argIdx + 1 >= args.size()) return new ExecResult("", "find: missing argument to `-exec'\n", 1);
                        argIdx++;
                        List<String> execArgs = new ArrayList<>();
                        while (argIdx < args.size() && !args.get(argIdx).equals(";")) {
                            execArgs.add(args.get(argIdx));
                            argIdx++;
                        }
                        if (argIdx >= args.size()) return new ExecResult("", "find: missing `;' terminator\n", 1);
                        predicates.add(new ExecPredicate(execArgs));
                        break;
                    case "-o":
                        predicates.add(new OrPredicate());
                        break;
                    case "-a":
                        // implicit AND - no op
                        break;
                    case "!":
                        predicates.add(new NotPredicate());
                        break;
                    default:
                        if (arg.startsWith("-")) {
                            return new ExecResult("", "find: unknown predicate `" + arg + "'\n", 1);
                        }
                }
                argIdx++;
            }

            boolean hasAction = predicates.stream().anyMatch(p -> p instanceof ActionPredicate);
            if (!hasAction) {
                predicates.add(new PrintPredicate());
            }

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();
            boolean anyError = false;

            for (String path : paths) {
                String resolved = path.startsWith("/") ? path : ctx.cwd() + "/" + path;
                try {
                    findRecursive(ctx, resolved, 0, maxDepth, predicates, stdout, stderr, maxCount, count);
                } catch (Exception e) {
                    stderr.append("find: `").append(path).append("': ").append(e.getMessage()).append("\n");
                    anyError = true;
                }
            }

            return new ExecResult(stdout.toString(), stderr.toString(), anyError ? 1 : 0);
        });
    }

    private int findRecursive(CommandContext ctx, String path, int depth, Integer maxDepth,
                               List<Predicate> predicates, StringBuilder stdout, StringBuilder stderr,
                               int maxCount, int count) throws Exception {
        if (maxDepth != null && depth > maxDepth) return count;
        if (count >= maxCount) return count;

        FsStat stat;
        try {
            stat = ctx.fs().stat(path).join();
        } catch (Exception e) {
            return count;
        }

        boolean result = evaluatePredicates(ctx, path, stat, predicates, stdout, stderr);
        count++;

        if (stat.isDirectory() && (maxDepth == null || depth < maxDepth)) {
            try {
                List<String> entries = ctx.fs().readdir(path).join();
                for (String entry : entries) {
                    String childPath = path.equals("/") ? "/" + entry : path + "/" + entry;
                    count = findRecursive(ctx, childPath, depth + 1, maxDepth, predicates, stdout, stderr, maxCount, count);
                }
            } catch (Exception e) {
                // Ignore read errors for directories
            }
        }

        return count;
    }

    private boolean evaluatePredicates(CommandContext ctx, String path, FsStat stat,
                                        List<Predicate> predicates, StringBuilder stdout, StringBuilder stderr) {
        boolean current = true;
        boolean nextOr = false;
        boolean nextNot = false;

        for (Predicate pred : predicates) {
            if (pred instanceof OrPredicate) {
                nextOr = true;
                continue;
            }
            if (pred instanceof NotPredicate) {
                nextNot = true;
                continue;
            }

            boolean matches = pred.matches(path, stat, ctx);
            if (nextNot) {
                matches = !matches;
                nextNot = false;
            }

            if (nextOr) {
                current = current || matches;
                nextOr = false;
            } else {
                current = current && matches;
            }

            if (pred instanceof ActionPredicate ap && current) {
                ap.execute(path, stat, ctx, stdout, stderr);
            }
        }

        return current;
    }

    private interface Predicate {
        boolean matches(String path, FsStat stat, CommandContext ctx);
    }

    private interface ActionPredicate extends Predicate {
        void execute(String path, FsStat stat, CommandContext ctx, StringBuilder stdout, StringBuilder stderr);
    }

    private static class NamePredicate implements Predicate {
        private final String pattern;
        private final boolean ignoreCase;
        NamePredicate(String pattern, boolean ignoreCase) {
            this.pattern = pattern;
            this.ignoreCase = ignoreCase;
        }
        @Override
        public boolean matches(String path, FsStat stat, CommandContext ctx) {
            String name = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
            String regex = globToRegex(pattern);
            int flags = ignoreCase ? java.util.regex.Pattern.CASE_INSENSITIVE : 0;
            return java.util.regex.Pattern.compile(regex, flags).matcher(name).matches();
        }
    }

    private static class TypePredicate implements Predicate {
        private final char type;
        TypePredicate(String typeStr) {
            this.type = typeStr.isEmpty() ? 'f' : typeStr.charAt(0);
        }
        @Override
        public boolean matches(String path, FsStat stat, CommandContext ctx) {
            return switch (type) {
                case 'f' -> !stat.isDirectory();
                case 'd' -> stat.isDirectory();
                default -> true;
            };
        }
    }

    private static class PrintPredicate implements ActionPredicate {
        @Override
        public boolean matches(String path, FsStat stat, CommandContext ctx) { return true; }
        @Override
        public void execute(String path, FsStat stat, CommandContext ctx, StringBuilder stdout, StringBuilder stderr) {
            stdout.append(path).append("\n");
        }
    }

    private static class DeletePredicate implements ActionPredicate {
        @Override
        public boolean matches(String path, FsStat stat, CommandContext ctx) { return true; }
        @Override
        public void execute(String path, FsStat stat, CommandContext ctx, StringBuilder stdout, StringBuilder stderr) {
            try {
                if (stat.isDirectory()) {
                    ctx.fs().rm(path, new com.justbash.fs.RmOptions(true, true)).join();
                } else {
                    ctx.fs().rm(path).join();
                }
            } catch (Exception e) {
                stderr.append("find: cannot delete `").append(path).append("'\n");
            }
        }
    }

    private static class ExecPredicate implements ActionPredicate {
        private final List<String> execArgs;
        ExecPredicate(List<String> execArgs) { this.execArgs = execArgs; }
        @Override
        public boolean matches(String path, FsStat stat, CommandContext ctx) { return true; }
        @Override
        public void execute(String path, FsStat stat, CommandContext ctx, StringBuilder stdout, StringBuilder stderr) {
            // Basic exec support - replace {} with path
            List<String> args = new ArrayList<>();
            for (String arg : execArgs) {
                args.add(arg.equals("{}") ? path : arg);
            }
            // For MVP, just print the command that would be executed
            stdout.append(String.join(" ", args)).append("\n");
        }
    }

    private static class OrPredicate implements Predicate {
        @Override
        public boolean matches(String path, FsStat stat, CommandContext ctx) { return true; }
    }

    private static class NotPredicate implements Predicate {
        @Override
        public boolean matches(String path, FsStat stat, CommandContext ctx) { return true; }
    }

    private static String globToRegex(String glob) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append(".");
                case '[' -> {
                    int end = glob.indexOf(']', i);
                    if (end == -1) {
                        sb.append("\\[");
                    } else {
                        sb.append(glob, i, end + 1);
                        i = end;
                    }
                }
                default -> {
                    if ("\\^$|.+(){}".indexOf(c) >= 0) sb.append('\\');
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
