package com.justbash.interpreter;

import com.justbash.ExecResult;
import com.justbash.ast.PipelineNode;
import java.util.ArrayList;
import java.util.List;

public class PipelineExecutor {

    private final Interpreter interpreter;

    public PipelineExecutor(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    public ExecResult executePipeline(PipelineNode pipeline, InterpreterState state) {
        String stdin = "";
        String accumulatedStderr = "";
        List<Integer> pipeStatus = new ArrayList<>();

        List<com.justbash.ast.command.CommandNode> commands = pipeline.commands();

        for (int i = 0; i < commands.size(); i++) {
            var cmd = commands.get(i);
            var result = interpreter.executeCommand(cmd, stdin);
            pipeStatus.add(result.exitCode());
            accumulatedStderr += result.stderr();
            // Pass stdout as stdin to next command
            stdin = result.stdout();
        }

        // The pipeline's stdout is the last command's stdout
        String stdout = stdin;
        String stderr = accumulatedStderr;

        // Determine pipeline exit code
        int exitCode;
        if (state.options.pipefail) {
            // pipefail: rightmost non-zero exit code, or 0 if all succeeded
            exitCode = 0;
            for (int i = pipeStatus.size() - 1; i >= 0; i--) {
                if (pipeStatus.get(i) != 0) {
                    exitCode = pipeStatus.get(i);
                    break;
                }
            }
        } else {
            exitCode = pipeStatus.isEmpty() ? 0 : pipeStatus.get(pipeStatus.size() - 1);
        }

        // Handle negation: ! pipeline
        if (pipeline.negated()) {
            exitCode = (exitCode == 0) ? 1 : 0;
        }

        // Store PIPESTATUS
        state.env.put("PIPESTATUS", formatPipeStatus(pipeStatus));

        return new ExecResult(stdout, stderr, exitCode);
    }

    private String formatPipeStatus(List<Integer> statuses) {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < statuses.size(); i++) {
            if (i > 0) sb.append(" ");
            sb.append(statuses.get(i));
        }
        sb.append(")");
        return sb.toString();
    }
}
