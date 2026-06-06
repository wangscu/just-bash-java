package com.justbash.commands.env;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class EnvCommand implements Command {
    @Override
    public String name() { return "env"; }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            boolean ignoreEnv = false;
            List<String> unsetVars = new ArrayList<>();
            Map<String, String> setVars = new LinkedHashMap<>();
            int commandStart = -1;

            for (int i = 0; i < args.size(); i++) {
                String arg = args.get(i);
                if (arg.equals("-i") || arg.equals("--ignore-environment")) {
                    ignoreEnv = true;
                } else if (arg.equals("-u") && i + 1 < args.size()) {
                    unsetVars.add(args.get(++i));
                } else if (arg.startsWith("-u")) {
                    unsetVars.add(arg.substring(2));
                } else if (arg.startsWith("--unset=")) {
                    unsetVars.add(arg.substring(8));
                } else if (arg.equals("--")) {
                    i++;
                    if (i < args.size()) {
                        commandStart = i;
                    }
                    break;
                } else if (arg.startsWith("-") && !arg.equals("-")) {
                    boolean unknown = false;
                    for (char c : arg.substring(1).toCharArray()) {
                        if (c != 'i' && c != 'u') {
                            unknown = true;
                        }
                    }
                    if (unknown) {
                        return new ExecResult("", "env: invalid option -- '" + arg.substring(1) + "'\n", 1);
                    }
                    if (arg.contains("i")) ignoreEnv = true;
                } else if (arg.contains("=") && commandStart == -1) {
                    int eqIdx = arg.indexOf('=');
                    setVars.put(arg.substring(0, eqIdx), arg.substring(eqIdx + 1));
                } else {
                    commandStart = i;
                    break;
                }
            }

            Map<String, String> newEnv = new LinkedHashMap<>();
            if (ignoreEnv) {
                newEnv.putAll(setVars);
            } else {
                newEnv.putAll(ctx.env());
                for (String name : unsetVars) {
                    newEnv.remove(name);
                }
                newEnv.putAll(setVars);
            }

            if (commandStart == -1) {
                StringBuilder stdout = new StringBuilder();
                for (Map.Entry<String, String> entry : newEnv.entrySet()) {
                    stdout.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
                }
                return new ExecResult(stdout.toString(), "", 0);
            }

            // Execute command with modified environment
            if (ctx.exec().isEmpty()) {
                return new ExecResult("", "env: command execution not supported in this context\n", 1);
            }

            List<String> cmdArgs = args.subList(commandStart, args.size());
            String cmdLine = String.join(" ", cmdArgs);
            var execOpts = new com.justbash.ExecOptions(
                java.util.Optional.of(newEnv),
                true,
                java.util.Optional.of(ctx.cwd()),
                false,
                java.util.Optional.of(ctx.stdin().decodeUtf8()),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                java.util.Optional.of(cmdArgs)
            );
            try {
                var result = ctx.exec().get().apply(cmdLine, execOpts).join();
                return new ExecResult(result.stdout(), result.stderr(), result.exitCode());
            } catch (Exception e) {
                return new ExecResult("", "env: " + e.getMessage() + "\n", 1);
            }
        });
    }
}
