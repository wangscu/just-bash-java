package com.justbash.commands.queryengine;

import java.util.*;

/**
 * Evaluation context for the query engine.
 * Contains variables, limits, environment, functions, labels, and path tracking.
 */
public final class EvalContext {
    private final Map<String, Object> vars;
    private final Limits limits;
    private final Map<String, String> env;
    private final Object root;
    private final List<Object> currentPath;
    private final Map<String, UserFunc> funcs;
    private final Set<String> labels;

    public record Limits(int maxIterations, int maxDepth) {
        public Limits {
            if (maxIterations <= 0) maxIterations = 10000;
            if (maxDepth <= 0) maxDepth = 2000;
        }

        public Limits() {
            this(10000, 2000);
        }
    }

    public record UserFunc(List<String> params, AstNode body, Map<String, UserFunc> closure) {
        public UserFunc {
            params = params != null ? List.copyOf(params) : List.of();
            closure = closure != null ? new LinkedHashMap<>(closure) : new LinkedHashMap<>();
        }
    }

    private EvalContext(Map<String, Object> vars, Limits limits, Map<String, String> env,
                        Object root, List<Object> currentPath, Map<String, UserFunc> funcs,
                        Set<String> labels) {
        this.vars = vars;
        this.limits = limits;
        this.env = env;
        this.root = root;
        this.currentPath = currentPath;
        this.funcs = funcs;
        this.labels = labels;
    }

    public static EvalContext create() {
        return create(Map.of(), new Limits());
    }

    public static EvalContext create(Map<String, String> env, Limits limits) {
        return new EvalContext(
            new LinkedHashMap<>(),
            limits != null ? limits : new Limits(),
            env != null ? env : Map.of(),
            null,
            List.of(),
            new LinkedHashMap<>(),
            new LinkedHashSet<>()
        );
    }

    public Map<String, Object> vars() { return vars; }
    public Limits limits() { return limits; }
    public Map<String, String> env() { return env; }
    public Object root() { return root; }
    public List<Object> currentPath() { return currentPath; }
    public Map<String, UserFunc> funcs() { return funcs; }
    public Set<String> labels() { return labels; }

    public EvalContext withVar(String name, Object value) {
        Map<String, Object> newVars = new LinkedHashMap<>(vars);
        newVars.put(name, value);
        return new EvalContext(newVars, limits, env, root, currentPath, funcs, labels);
    }

    public EvalContext withFuncs(Map<String, UserFunc> newFuncs) {
        Map<String, UserFunc> merged = new LinkedHashMap<>(funcs);
        merged.putAll(newFuncs);
        return new EvalContext(vars, limits, env, root, currentPath, merged, labels);
    }

    public EvalContext withLabels(Set<String> newLabels) {
        Set<String> merged = new LinkedHashSet<>(labels);
        merged.addAll(newLabels);
        return new EvalContext(vars, limits, env, root, currentPath, funcs, merged);
    }

    public EvalContext withCurrentPath(List<Object> path) {
        return new EvalContext(vars, limits, env, root, path, funcs, labels);
    }

    public EvalContext withRoot(Object root) {
        return new EvalContext(vars, limits, env, root, currentPath, funcs, labels);
    }
}
