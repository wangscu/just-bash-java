package com.justbash.interpreter;

import com.justbash.ExecResult;
import java.util.List;
import java.util.Optional;

public class BuiltinDispatcher {

    public Optional<ExecResult> dispatch(String name, List<String> args, InterpreterState state) {
        return switch (name) {
            case "echo" -> Optional.of(handleEcho(args));
            case "true" -> Optional.of(new ExecResult("", "", 0));
            case "false" -> Optional.of(new ExecResult("", "", 1));
            default -> Optional.empty();
        };
    }

    private ExecResult handleEcho(List<String> args) {
        // Simple echo: join args with space, append newline
        // For MVP, ignore -n, -e flags
        String output = String.join(" ", args) + "\n";
        return new ExecResult(output, "", 0);
    }
}
