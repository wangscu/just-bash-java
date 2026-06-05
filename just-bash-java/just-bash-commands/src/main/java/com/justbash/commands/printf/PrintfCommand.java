package com.justbash.commands.printf;

import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PrintfCommand implements Command {
    @Override
    public String name() {
        return "printf";
    }

    @Override
    public CompletableFuture<ExecResult> execute(List<String> args, CommandContext ctx) {
        return CompletableFuture.supplyAsync(() -> {
            if (args.isEmpty()) {
                return new ExecResult("", "printf: usage: printf format [arguments]\n", 1);
            }

            String format = args.get(0);
            List<String> values = new ArrayList<>(args.subList(1, args.size()));

            StringBuilder output = new StringBuilder();
            int valueIndex = 0;

            for (int i = 0; i < format.length(); i++) {
                char c = format.charAt(i);
                if (c == '%' && i + 1 < format.length()) {
                    char specifier = format.charAt(i + 1);
                    switch (specifier) {
                        case 's' -> {
                            output.append(valueIndex < values.size() ? values.get(valueIndex++) : "");
                            i++;
                        }
                        case 'd', 'i' -> {
                            String val = valueIndex < values.size() ? values.get(valueIndex++) : "0";
                            try {
                                output.append(Integer.parseInt(val));
                            } catch (NumberFormatException e) {
                                output.append("0");
                            }
                            i++;
                        }
                        case 'f' -> {
                            String val = valueIndex < values.size() ? values.get(valueIndex++) : "0";
                            try {
                                output.append(Double.parseDouble(val));
                            } catch (NumberFormatException e) {
                                output.append("0.0");
                            }
                            i++;
                        }
                        case 'x' -> {
                            String val = valueIndex < values.size() ? values.get(valueIndex++) : "0";
                            try {
                                output.append(Integer.toHexString(Integer.parseInt(val)));
                            } catch (NumberFormatException e) {
                                output.append("0");
                            }
                            i++;
                        }
                        case 'o' -> {
                            String val = valueIndex < values.size() ? values.get(valueIndex++) : "0";
                            try {
                                output.append(Integer.toOctalString(Integer.parseInt(val)));
                            } catch (NumberFormatException e) {
                                output.append("0");
                            }
                            i++;
                        }
                        case 'c' -> {
                            String val = valueIndex < values.size() ? values.get(valueIndex++) : "";
                            output.append(val.isEmpty() ? "" : val.charAt(0));
                            i++;
                        }
                        case '%' -> {
                            output.append('%');
                            i++;
                        }
                        default -> output.append(c);
                    }
                } else if (c == '\\' && i + 1 < format.length()) {
                    char escape = format.charAt(i + 1);
                    switch (escape) {
                        case 'n' -> {
                            output.append('\n');
                            i++;
                        }
                        case 't' -> {
                            output.append('\t');
                            i++;
                        }
                        case '\\' -> {
                            output.append('\\');
                            i++;
                        }
                        default -> output.append(c);
                    }
                } else {
                    output.append(c);
                }
            }

            return new ExecResult(output.toString(), "", 0);
        });
    }
}
