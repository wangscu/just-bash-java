package com.justbash.commands.seq;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class SeqCommand implements Command {
    @Override
    public String name() {
        return "seq";
    }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            String separator = "\n";
            boolean equalizeWidth = false;
            List<String> nums = new ArrayList<>();

            int i = 0;
            while (i < args.size()) {
                String arg = args.get(i);
                if (arg.equals("-s") && i + 1 < args.size()) {
                    separator = args.get(++i);
                    i++;
                } else if (arg.startsWith("-s") && arg.length() > 2) {
                    separator = arg.substring(2);
                    i++;
                } else if (arg.equals("-w")) {
                    equalizeWidth = true;
                    i++;
                } else if (arg.equals("--")) {
                    i++;
                    break;
                } else if (arg.startsWith("-") && !arg.equals("-")) {
                    return new ExecResult("", "seq: invalid option '" + arg + "'\n", 1);
                } else {
                    nums.add(arg);
                    i++;
                }
            }
            while (i < args.size()) {
                nums.add(args.get(i++));
            }

            if (nums.isEmpty()) {
                return new ExecResult("", "seq: missing operand\n", 1);
            }

            double first = 1;
            double increment = 1;
            double last;

            if (nums.size() == 1) {
                last = parseNum(nums.get(0));
            } else if (nums.size() == 2) {
                first = parseNum(nums.get(0));
                last = parseNum(nums.get(1));
            } else {
                first = parseNum(nums.get(0));
                increment = parseNum(nums.get(1));
                last = parseNum(nums.get(2));
            }

            if (Double.isNaN(first) || Double.isNaN(increment) || Double.isNaN(last)) {
                return new ExecResult("", "seq: invalid floating point argument\n", 1);
            }
            if (increment == 0) {
                return new ExecResult("", "seq: invalid Zero increment value\n", 1);
            }

            int precision = Math.max(getPrecision(nums.size() >= 1 ? nums.get(0) : ""),
                Math.max(getPrecision(nums.size() >= 2 ? nums.get(1) : ""),
                    getPrecision(nums.size() >= 3 ? nums.get(2) : "")));

            List<String> results = new ArrayList<>();
            final int maxIterations = 100000;
            int iterations = 0;

            if (increment > 0) {
                for (double n = first; n <= last + 1e-10 && iterations < maxIterations; n += increment) {
                    results.add(formatNum(n, precision));
                    iterations++;
                }
            } else {
                for (double n = first; n >= last - 1e-10 && iterations < maxIterations; n += increment) {
                    results.add(formatNum(n, precision));
                    iterations++;
                }
            }

            if (equalizeWidth && !results.isEmpty()) {
                int maxLen = results.stream()
                    .mapToInt(r -> r.replace("-", "").length())
                    .max().orElse(0);
                for (int j = 0; j < results.size(); j++) {
                    String r = results.get(j);
                    boolean neg = r.startsWith("-");
                    String num = neg ? r.substring(1) : r;
                    String padded = "0".repeat(Math.max(0, maxLen - num.length())) + num;
                    results.set(j, neg ? "-" + padded : padded);
                }
            }

            String output = String.join(separator, results);
            if (!output.isEmpty()) {
                output += "\n";
            }
            return new ExecResult(output, "", 0);
        });
    }

    private double parseNum(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private int getPrecision(String s) {
        int dot = s.indexOf('.');
        return dot == -1 ? 0 : s.length() - dot - 1;
    }

    private String formatNum(double n, int precision) {
        if (precision > 0) {
            return String.format("%." + precision + "f", n);
        }
        return String.valueOf(Math.round(n));
    }
}
