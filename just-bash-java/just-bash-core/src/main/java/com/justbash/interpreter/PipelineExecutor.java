package com.justbash.interpreter;

import com.justbash.ExecResult;
import com.justbash.ast.PipelineNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PipelineExecutor {

    private final Interpreter interpreter;

    public PipelineExecutor(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    public ExecResult executePipeline(PipelineNode pipeline, InterpreterState state) {
        String stdin = state.groupStdin != null ? state.groupStdin : "";
        List<Integer> pipeStatus = new ArrayList<>();
        List<ExecResult> results = new ArrayList<>();

        List<com.justbash.ast.command.CommandNode> commands = pipeline.commands();
        List<Boolean> pipeStderr = pipeline.pipeStderr().orElse(Collections.emptyList());

        for (int i = 0; i < commands.size(); i++) {
            var cmd = commands.get(i);

            ExecResult result;
            if (commands.size() > 1) {
                // Subshell isolation: each segment runs with a copy of state
                InterpreterState segmentState = state.copy();
                segmentState.groupStdin = stdin;
                result = interpreter.executeCommand(cmd, stdin, segmentState);
            } else {
                result = interpreter.executeCommand(cmd, stdin);
            }
            pipeStatus.add(result.exitCode());
            results.add(result);

            // Determine what to pass to next command
            boolean pipeBoth = i < pipeStderr.size() && pipeStderr.get(i);
            if (pipeBoth) {
                // |&: pipe stdout and stderr together
                stdin = result.stdout() + result.stderr();
            } else {
                stdin = result.stdout();
            }
        }

        // Pipeline's stdout is the last command's stdout
        String stdout = results.isEmpty() ? "" : results.get(results.size() - 1).stdout();

        // Pipeline's stderr: only from commands whose stderr was not piped away
        StringBuilder stderr = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            boolean wasPiped = i < pipeStderr.size() && pipeStderr.get(i);
            if (!wasPiped) {
                stderr.append(results.get(i).stderr());
            }
        }

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

        return new ExecResult(stdout, stderr.toString(), exitCode);
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
