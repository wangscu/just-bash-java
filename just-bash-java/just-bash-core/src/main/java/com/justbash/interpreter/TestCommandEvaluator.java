package com.justbash.interpreter;

import com.justbash.fs.FsStat;
import com.justbash.fs.IFileSystem;
import java.util.List;

/**
 * Evaluates test / [ conditional expressions.
 *
 * Returns:
 *   0 — expression is true
 *   1 — expression is false
 *   2 — syntax error or invalid argument
 */
public class TestCommandEvaluator {

    public static int evaluate(List<String> args, IFileSystem fs, InterpreterState state, boolean bracketMode) {
        if (args.isEmpty()) {
            return bracketMode ? 2 : 1; // empty [ ] is true, empty test is false
        }

        // If called as [ ... ], last arg must be ]
        if (bracketMode) {
            if (!args.get(args.size() - 1).equals("]")) {
                return 2; // missing ]
            }
            args = args.subList(0, args.size() - 1);
        }

        if (args.isEmpty()) {
            return bracketMode ? 0 : 1;
        }

        try {
            Parser p = new Parser(args, fs, state);
            boolean result = p.parseOr();
            if (p.hasRemaining()) {
                return 2; // unexpected trailing tokens
            }
            return result ? 0 : 1;
        } catch (SyntaxError e) {
            return 2;
        }
    }

    private static class SyntaxError extends RuntimeException {}

    private static class Parser {
        private final List<String> args;
        private final IFileSystem fs;
        private final InterpreterState state;
        private int pos = 0;

        Parser(List<String> args, IFileSystem fs, InterpreterState state) {
            this.args = args;
            this.fs = fs;
            this.state = state;
        }

        boolean hasRemaining() {
            return pos < args.size();
        }

        String peek() {
            return pos < args.size() ? args.get(pos) : null;
        }

        String consume() {
            return pos < args.size() ? args.get(pos++) : null;
        }

        boolean match(String expected) {
            if (peek() != null && peek().equals(expected)) {
                pos++;
                return true;
            }
            return false;
        }

        // OR has lowest precedence: left-associative
        boolean parseOr() {
            boolean result = parseAnd();
            while (match("-o")) {
                result = result || parseAnd();
            }
            return result;
        }

        // AND: left-associative
        boolean parseAnd() {
            boolean result = parseUnary();
            while (match("-a")) {
                result = result && parseUnary();
            }
            return result;
        }

        // Unary: ! expr or ( expr ) or primary
        boolean parseUnary() {
            if (match("!")) {
                return !parseUnary();
            }
            if (match("(")) {
                boolean result = parseOr();
                if (!match(")")) {
                    throw new SyntaxError();
                }
                return result;
            }
            return parsePrimary();
        }

        // Primary: file tests, string tests, numeric comparisons
        boolean parsePrimary() {
            if (!hasRemaining()) {
                throw new SyntaxError();
            }

            String first = consume();

            // File tests (unary)
            if (isFileTestOperator(first)) {
                if (!hasRemaining()) throw new SyntaxError();
                String operand = consume();
                return evaluateFileTest(first, operand);
            }

            // String tests: -z, -n
            if (first.equals("-z") || first.equals("-n")) {
                if (!hasRemaining()) throw new SyntaxError();
                String operand = consume();
                boolean empty = operand.isEmpty();
                return first.equals("-z") ? empty : !empty;
            }

            // Single argument: non-empty string is true
            if (!hasRemaining()) {
                return !first.isEmpty();
            }

            String second = consume();

            // Binary comparisons
            if (!hasRemaining()) {
                throw new SyntaxError();
            }

            String third = consume();

            // String comparisons
            if (second.equals("=") || second.equals("==")) {
                return first.equals(third);
            }
            if (second.equals("!=")) {
                return !first.equals(third);
            }

            // Numeric comparisons
            if (isNumericOp(second)) {
                long a = parseLong(first);
                long b = parseLong(third);
                return compareNumeric(a, b, second);
            }

            throw new SyntaxError();
        }

        boolean isFileTestOperator(String op) {
            return switch (op) {
                case "-e", "-f", "-d", "-r", "-w", "-x", "-s", "-h", "-L",
                     "-p", "-S", "-b", "-c", "-u", "-g", "-k", "-t" -> true;
                default -> false;
            };
        }

        boolean isNumericOp(String op) {
            return switch (op) {
                case "-eq", "-ne", "-lt", "-le", "-gt", "-ge" -> true;
                default -> false;
            };
        }

        boolean evaluateFileTest(String op, String path) {
            String resolved = path.startsWith("/") ? path : state.cwd + "/" + path;
            return switch (op) {
                case "-e" -> fileExists(resolved);
                case "-f" -> {
                    FsStat stat = safeStat(resolved);
                    yield stat != null && stat.isFile() && !stat.isSymbolicLink();
                }
                case "-d" -> {
                    FsStat stat = safeStat(resolved);
                    yield stat != null && stat.isDirectory() && !stat.isSymbolicLink();
                }
                case "-r" -> {
                    FsStat stat = safeStat(resolved);
                    yield stat != null && (stat.mode() & 0444) != 0;
                }
                case "-w" -> {
                    FsStat stat = safeStat(resolved);
                    yield stat != null && (stat.mode() & 0222) != 0;
                }
                case "-x" -> {
                    FsStat stat = safeStat(resolved);
                    yield stat != null && (stat.mode() & 0111) != 0;
                }
                case "-s" -> {
                    FsStat stat = safeStat(resolved);
                    yield stat != null && stat.size() > 0;
                }
                case "-h", "-L" -> {
                    FsStat stat = safeLstat(resolved);
                    yield stat != null && stat.isSymbolicLink();
                }
                case "-u" -> {
                    FsStat stat = safeStat(resolved);
                    yield stat != null && (stat.mode() & 04000) != 0;
                }
                case "-g" -> {
                    FsStat stat = safeStat(resolved);
                    yield stat != null && (stat.mode() & 02000) != 0;
                }
                case "-k" -> {
                    FsStat stat = safeStat(resolved);
                    yield stat != null && (stat.mode() & 01000) != 0;
                }
                case "-t" -> {
                    // Check if fd is a terminal
                    try {
                        int fd = Integer.parseInt(path);
                        yield fd >= 0 && fd <= 2; // stdin, stdout, stderr are "terminals"
                    } catch (NumberFormatException e) {
                        yield false;
                    }
                }
                // These file types are not supported by our in-memory FS
                case "-p", "-S", "-b", "-c" -> false;
                default -> false;
            };
        }

        boolean fileExists(String path) {
            try {
                return fs.exists(path).join();
            } catch (Exception e) {
                return false;
            }
        }

        FsStat safeStat(String path) {
            try {
                return fs.stat(path).join();
            } catch (Exception e) {
                return null;
            }
        }

        FsStat safeLstat(String path) {
            try {
                return fs.lstat(path).join();
            } catch (Exception e) {
                return null;
            }
        }

        long parseLong(String s) throws NumberFormatException {
            // Bash allows leading + and -
            return Long.parseLong(s);
        }

        boolean compareNumeric(long a, long b, String op) {
            return switch (op) {
                case "-eq" -> a == b;
                case "-ne" -> a != b;
                case "-lt" -> a < b;
                case "-le" -> a <= b;
                case "-gt" -> a > b;
                case "-ge" -> a >= b;
                default -> false;
            };
        }
    }
}
