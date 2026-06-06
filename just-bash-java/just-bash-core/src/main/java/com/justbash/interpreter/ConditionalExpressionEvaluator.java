package com.justbash.interpreter;

import com.justbash.ast.word.WordNode;
import com.justbash.ast.expression.*;
import com.justbash.fs.IFileSystem;
import java.util.regex.Pattern;

public class ConditionalExpressionEvaluator {

    private final ExpansionEngine expansion = new ExpansionEngine();
    private final InterpreterState state;
    private final IFileSystem fs;

    public ConditionalExpressionEvaluator(InterpreterState state, IFileSystem fs) {
        this.state = state;
        this.fs = fs;
    }

    public boolean evaluate(ConditionalExpressionNode node) {
        return switch (node) {
            case CondWordNode cw -> {
                String value = expandWord(cw.word());
                yield !value.isEmpty();
            }
            case CondUnaryNode cu -> evaluateUnary(cu.operator(), expandWord(cu.operand()));
            case CondBinaryNode cb -> evaluateBinary(cb.operator(), expandWord(cb.left()), expandWord(cb.right()));
            case CondNotNode cn -> !evaluate(cn.operand());
            case CondAndNode ca -> evaluate(ca.left()) && evaluate(ca.right());
            case CondOrNode co -> evaluate(co.left()) || evaluate(co.right());
            case CondGroupNode cg -> evaluate(cg.expression());
        };
    }

    private String expandWord(WordNode word) {
        return expansion.expandWord(word, state).get(0);
    }

    private boolean evaluateUnary(String op, String operand) {
        return switch (op) {
            case "-n" -> !operand.isEmpty();
            case "-z" -> operand.isEmpty();
            case "-f" -> fs.exists(resolvePath(operand)).join();
            case "-d" -> {
                try {
                    yield fs.stat(resolvePath(operand)).join().isDirectory();
                } catch (Exception e) {
                    yield false;
                }
            }
            case "-e" -> fs.exists(resolvePath(operand)).join();
            case "-r" -> fs.exists(resolvePath(operand)).join(); // MVP: assume readable if exists
            case "-w" -> fs.exists(resolvePath(operand)).join(); // MVP: assume writable if exists
            case "-x" -> fs.exists(resolvePath(operand)).join(); // MVP: assume executable if exists
            case "-s" -> {
                try {
                    var stat = fs.stat(resolvePath(operand)).join();
                    yield stat.size() > 0;
                } catch (Exception e) {
                    yield false;
                }
            }
            case "-L" -> {
                try {
                    var stat = fs.lstat(resolvePath(operand)).join();
                    yield stat.isSymbolicLink();
                } catch (Exception e) {
                    yield false;
                }
            }
            case "-p" -> false; // named pipe - not supported in MVP
            case "-S" -> false; // socket - not supported in MVP
            case "-b" -> false; // block device - not supported in MVP
            case "-c" -> false; // character device - not supported in MVP
            case "-t" -> false; // terminal - not supported in MVP
            case "-u" -> false; // setuid - not supported in MVP
            case "-g" -> false; // setgid - not supported in MVP
            case "-k" -> false; // sticky bit - not supported in MVP
            case "-O" -> true;  // owned by effective uid - assume true in MVP
            case "-G" -> true;  // owned by effective gid - assume true in MVP
            case "-N" -> false; // modified since read - not supported in MVP
            default -> !operand.isEmpty();
        };
    }

    private boolean evaluateBinary(String op, String left, String right) {
        return switch (op) {
            case "==", "=" -> globMatch(left, right);
            case "!=" -> !globMatch(left, right);
            case "=~" -> {
                try {
                    yield Pattern.compile(right).matcher(left).find();
                } catch (Exception e) {
                    yield false;
                }
            }
            case "<" -> left.compareTo(right) < 0;
            case ">" -> left.compareTo(right) > 0;
            case "-eq" -> compareInt(left, right) == 0;
            case "-ne" -> compareInt(left, right) != 0;
            case "-lt" -> compareInt(left, right) < 0;
            case "-le" -> compareInt(left, right) <= 0;
            case "-gt" -> compareInt(left, right) > 0;
            case "-ge" -> compareInt(left, right) >= 0;
            case "-ef" -> resolvePath(left).equals(resolvePath(right));
            case "-nt" -> false; // not supported in MVP
            case "-ot" -> false; // not supported in MVP
            default -> false;
        };
    }

    private boolean globMatch(String text, String pattern) {
        StringBuilder regex = new StringBuilder();
        int i = 0;
        while (i < pattern.length()) {
            char c = pattern.charAt(i);
            switch (c) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append(".");
                case '[' -> {
                    int end = pattern.indexOf(']', i);
                    if (end == -1) {
                        regex.append("\\[");
                    } else {
                        regex.append(pattern.substring(i, end + 1));
                        i = end;
                    }
                }
                case '\\' -> {
                    if (i + 1 < pattern.length()) {
                        regex.append('\\').append(pattern.charAt(i + 1));
                        i++;
                    } else {
                        regex.append("\\\\");
                    }
                }
                default -> {
                    if ("[](){}^$|.+".indexOf(c) >= 0) {
                        regex.append('\\');
                    }
                    regex.append(c);
                }
            }
            i++;
        }
        return text.matches(regex.toString());
    }

    private int compareInt(String a, String b) {
        try {
            return Long.compare(Long.parseLong(a.trim()), Long.parseLong(b.trim()));
        } catch (NumberFormatException e) {
            return a.compareTo(b);
        }
    }

    private String resolvePath(String path) {
        return path.startsWith("/") ? path : state.cwd + "/" + path;
    }
}
