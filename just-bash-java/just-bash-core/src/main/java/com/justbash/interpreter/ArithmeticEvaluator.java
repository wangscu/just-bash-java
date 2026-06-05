package com.justbash.interpreter;

import com.justbash.ast.expression.*;

/**
 * Evaluates arithmetic expression AST nodes.
 *
 * Uses long (64-bit signed) arithmetic like bash.
 */
public class ArithmeticEvaluator {

    public static long evaluate(ArithExpr expr, InterpreterState state) {
        return switch (expr) {
            case ArithNumberNode n -> n.value();
            case ArithVariableNode v -> resolveVariable(v.name(), state);
            case ArithSpecialVarNode s -> resolveSpecialVar(s.name(), state);
            case ArithBinaryNode b -> evaluateBinary(b, state);
            case ArithUnaryNode u -> evaluateUnary(u, state);
            case ArithTernaryNode t -> evaluateTernary(t, state);
            case ArithAssignmentNode a -> evaluateAssignment(a, state);
            case ArithGroupNode g -> evaluate(g.expression(), state);
            case ArithNestedNode n -> evaluate(n.expression(), state);
            case ArithArrayElementNode a -> evaluateArrayElement(a, state);
            case ArithCommandSubstNode c -> 0; // Not supported in MVP
        };
    }

    private static long resolveVariable(String name, InterpreterState state) {
        String value = state.env.get(name);
        if (value == null || value.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long resolveSpecialVar(String name, InterpreterState state) {
        return switch (name) {
            case "?" -> state.lastExitCode;
            case "#" -> {
                String count = state.env.get("#");
                yield count != null ? parseLongOrZero(count) : 0;
            }
            case "$" -> state.bashPid;
            case "*" -> {
                String value = state.env.get("*");
                yield value != null ? parseLongOrZero(value) : 0;
            }
            case "@" -> {
                String value = state.env.get("@");
                yield value != null ? parseLongOrZero(value) : 0;
            }
            case "-" -> 0; // Flags as string — not meaningful as number
            case "!" -> state.lastBackgroundPid;
            case "0" -> 0; // Script name
            default -> 0;
        };
    }

    private static long parseLongOrZero(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long evaluateBinary(ArithBinaryNode node, InterpreterState state) {
        long left = evaluate(node.left(), state);
        long right = evaluate(node.right(), state);
        return switch (node.operator()) {
            case "+" -> left + right;
            case "-" -> left - right;
            case "*" -> left * right;
            case "/" -> right != 0 ? left / right : 0;
            case "%" -> right != 0 ? left % right : 0;
            case "**" -> (long) Math.pow(left, right);
            case "<<" -> left << right;
            case ">>" -> left >> right;
            case "&" -> left & right;
            case "|" -> left | right;
            case "^" -> left ^ right;
            case "<" -> left < right ? 1 : 0;
            case ">" -> left > right ? 1 : 0;
            case "<=" -> left <= right ? 1 : 0;
            case ">=" -> left >= right ? 1 : 0;
            case "==" -> left == right ? 1 : 0;
            case "!=" -> left != right ? 1 : 0;
            case "&&" -> (left != 0 && right != 0) ? 1 : 0;
            case "||" -> (left != 0 || right != 0) ? 1 : 0;
            default -> 0;
        };
    }

    private static long evaluateUnary(ArithUnaryNode node, InterpreterState state) {
        if (node.prefix()) {
            return switch (node.operator()) {
                case "+" -> evaluate(node.operand(), state);
                case "-" -> -evaluate(node.operand(), state);
                case "!" -> evaluate(node.operand(), state) == 0 ? 1 : 0;
                case "~" -> ~evaluate(node.operand(), state);
                case "++" -> {
                    long val = evaluate(node.operand(), state) + 1;
                    setVariable(node.operand(), val, state);
                    yield val;
                }
                case "--" -> {
                    long val = evaluate(node.operand(), state) - 1;
                    setVariable(node.operand(), val, state);
                    yield val;
                }
                default -> 0;
            };
        } else {
            // Postfix
            long val = evaluate(node.operand(), state);
            return switch (node.operator()) {
                case "++" -> {
                    setVariable(node.operand(), val + 1, state);
                    yield val;
                }
                case "--" -> {
                    setVariable(node.operand(), val - 1, state);
                    yield val;
                }
                default -> val;
            };
        }
    }

    private static long evaluateTernary(ArithTernaryNode node, InterpreterState state) {
        long condition = evaluate(node.condition(), state);
        return condition != 0
            ? evaluate(node.consequent(), state)
            : evaluate(node.alternate(), state);
    }

    private static long evaluateAssignment(ArithAssignmentNode node, InterpreterState state) {
        long right = evaluate(node.value(), state);
        long current = resolveVariable(node.variable(), state);
        long result = switch (node.operator()) {
            case "=" -> right;
            case "+=" -> current + right;
            case "-=" -> current - right;
            case "*=" -> current * right;
            case "/=" -> right != 0 ? current / right : 0;
            case "%=" -> right != 0 ? current % right : 0;
            case "&=" -> current & right;
            case "|=" -> current | right;
            case "^=" -> current ^ right;
            case "<<=" -> current << right;
            case ">>=" -> current >> right;
            default -> right;
        };
        state.env.put(node.variable(), String.valueOf(result));
        return result;
    }

    private static long evaluateArrayElement(ArithArrayElementNode node, InterpreterState state) {
        // For MVP, treat array element as variable with index suffix
        long index = evaluate(node.index(), state);
        String key = node.array() + "[" + index + "]";
        return resolveVariable(key, state);
    }

    private static void setVariable(ArithExpr expr, long value, InterpreterState state) {
        switch (expr) {
            case ArithVariableNode v -> state.env.put(v.name(), String.valueOf(value));
            case ArithArrayElementNode a -> {
                long index = evaluate(a.index(), state);
                state.env.put(a.array() + "[" + index + "]", String.valueOf(value));
            }
            default -> { /* no-op */ }
        }
    }
}
