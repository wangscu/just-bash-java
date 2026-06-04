package com.justbash.interpreter;

import com.justbash.ExecResult;
import com.justbash.ast.PipelineNode;
import java.util.List;
import java.util.Optional;

public class PipelineExecutor {

    private final Interpreter interpreter;

    public PipelineExecutor(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    public ExecResult executePipeline(PipelineNode pipeline, InterpreterState state) {
        String stdout = "";
        String stderr = "";
        int exitCode = 0;
        String stdin = "";

        List<com.justbash.ast.command.CommandNode> commands = pipeline.commands();

        for (int i = 0; i < commands.size(); i++) {
            var cmd = commands.get(i);
            var result = interpreter.executeCommand(cmd, stdin);
            stdout += result.stdout();
            stderr += result.stderr();
            exitCode = result.exitCode();
            // Pass stdout as stdin to next command
            stdin = result.stdout();
        }

        // Handle negation: ! pipeline
        if (pipeline.negated()) {
            exitCode = (exitCode == 0) ? 1 : 0;
        }

        return new ExecResult(stdout, stderr, exitCode);
    }
}
