package com.justbash.commands.sleep;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SleepCommand implements Command {
    private static final long MAX_SLEEP_MS = 3_600_000; // 1 hour
    private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+\\.?\\d*)([smhd])?$");

    @Override
    public String name() { return "sleep"; }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> operands = new java.util.ArrayList<>();

            for (String arg : args) {
                if (arg.equals("--help")) {
                    return help();
                } else if (arg.equals("--")) {
                    continue;
                } else if (arg.startsWith("-") && !arg.equals("-")) {
                    return new ExecResult("", "sleep: invalid option -- '" + arg.substring(1) + "'\n", 1);
                } else {
                    operands.add(arg);
                }
            }

            if (operands.isEmpty()) {
                return new ExecResult("", "sleep: missing operand\n", 1);
            }

            long totalMs = 0;
            for (String arg : operands) {
                Long ms = parseDuration(arg);
                if (ms == null) {
                    return new ExecResult("", "sleep: invalid time interval '" + arg + "'\n", 1);
                }
                totalMs += ms;
            }

            if (totalMs > MAX_SLEEP_MS) {
                totalMs = MAX_SLEEP_MS;
            }

            if (totalMs > 0) {
                try {
                    if (ctx.sleep().isPresent()) {
                        ctx.sleep().get().apply(totalMs).join();
                    } else {
                        Thread.sleep(totalMs);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return new ExecResult("", "", 0);
                }
            }

            return new ExecResult("", "", 0);
        });
    }

    private Long parseDuration(String arg) {
        Matcher m = DURATION_PATTERN.matcher(arg);
        if (!m.matches()) {
            return null;
        }

        double value;
        try {
            value = Double.parseDouble(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }

        String suffix = m.group(2);
        if (suffix == null) {
            suffix = "s";
        }

        return switch (suffix) {
            case "s" -> (long) (value * 1000);
            case "m" -> (long) (value * 60 * 1000);
            case "h" -> (long) (value * 60 * 60 * 1000);
            case "d" -> (long) (value * 24 * 60 * 60 * 1000);
            default -> null;
        };
    }

    private ExecResult help() {
        String help = "Usage: sleep NUMBER[SUFFIX]\n" +
            "Pause for NUMBER seconds. SUFFIX may be:\n" +
            "  s - seconds (default)\n" +
            "  m - minutes\n" +
            "  h - hours\n" +
            "  d - days\n\n" +
            "NUMBER may be a decimal number.\n" +
            "    --help    display this help and exit\n";
        return new ExecResult(help, "", 0);
    }
}
