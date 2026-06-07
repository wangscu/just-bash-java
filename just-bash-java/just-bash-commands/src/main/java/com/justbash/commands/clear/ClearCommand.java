package com.justbash.commands.clear;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ClearCommand implements Command {
    @Override
    public String name() { return "clear"; }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            for (String arg : args) {
                if (arg.equals("--help")) {
                    return help();
                } else if (arg.startsWith("-") && !arg.equals("-")) {
                    return new ExecResult("", "clear: invalid option -- '" + arg.substring(1) + "'\n", 1);
                }
            }

            // ANSI escape sequence: clear screen and move cursor to top-left
            String clearSequence = "[2J[H";
            return new ExecResult(clearSequence, "", 0);
        });
    }

    private ExecResult help() {
        String help = "Usage: clear [OPTIONS]\n\n" +
            "    --help    display this help and exit\n";
        return new ExecResult(help, "", 0);
    }
}
