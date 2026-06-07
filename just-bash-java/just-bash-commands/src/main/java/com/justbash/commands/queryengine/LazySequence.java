package com.justbash.commands.queryengine;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Lazy sequence for jq repeat/limit semantics.
 * Generates values on demand to avoid eager evaluation hitting iteration limits.
 */
public final class LazySequence implements Iterable<Object> {

    private final Object initial;
    private final AstNode expr;
    private final EvalContext ctx;
    private final Evaluator.EvaluateCallback eval;

    public LazySequence(Object initial, AstNode expr, EvalContext ctx, Evaluator.EvaluateCallback eval) {
        this.initial = initial;
        this.expr = expr;
        this.ctx = ctx;
        this.eval = eval;
    }

    @Override
    public Iterator<Object> iterator() {
        return new Iterator<>() {
            Object current = initial;
            boolean done = false;

            @Override
            public boolean hasNext() {
                return !done;
            }

            @Override
            public Object next() {
                if (done) throw new NoSuchElementException();
                Object result = current;
                List<Object> next = eval.evaluate(current, expr, ctx);
                if (next.isEmpty()) {
                    done = true;
                } else {
                    current = next.get(0);
                }
                return result;
            }
        };
    }
}
