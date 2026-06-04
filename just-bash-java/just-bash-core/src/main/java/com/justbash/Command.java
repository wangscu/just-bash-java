package com.justbash;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface Command {
    String name();
    default boolean trusted() { return false; }
    CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx);
}
