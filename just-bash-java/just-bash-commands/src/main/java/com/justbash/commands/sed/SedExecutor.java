package com.justbash.commands.sed;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SedExecutor {

    private static final int DEFAULT_MAX_ITERATIONS = 10000;

    public static SedTypes.SedState createInitialState(int totalLines) {
        SedTypes.SedState state = new SedTypes.SedState();
        state.totalLines = totalLines;
        return state;
    }

    private static boolean matchesAddress(SedTypes.SedAddress addr, int lineNum, int totalLines, String line, SedTypes.SedState state) {
        if (addr instanceof SedTypes.LastLineAddress) {
            return lineNum == totalLines;
        }
        if (addr instanceof SedTypes.LineNumberAddress la) {
            return lineNum == la.line();
        }
        if (addr instanceof SedTypes.PatternAddress pa) {
            String rawPattern = pa.pattern();
            if (rawPattern.isEmpty() && state.lastPattern != null) {
                rawPattern = state.lastPattern;
            } else if (!rawPattern.isEmpty()) {
                state.lastPattern = rawPattern;
            }
            try {
                String pattern = pa.extendedRegex() ? rawPattern : breToEre(rawPattern);
                Pattern p = Pattern.compile(pattern);
                return p.matcher(line).find();
            } catch (Exception e) {
                return false;
            }
        }
        if (addr instanceof SedTypes.StepAddress sa) {
            if (sa.step() == 0) return lineNum == sa.first();
            return (lineNum - sa.first()) % sa.step() == 0 && lineNum >= sa.first();
        }
        return false;
    }

    private static String serializeRange(SedTypes.AddressRange range) {
        String start = serializeAddr(range.start());
        String end = serializeAddr(range.end());
        return start + "," + end;
    }

    private static String serializeAddr(SedTypes.SedAddress addr) {
        if (addr == null) return "null";
        if (addr instanceof SedTypes.LastLineAddress) return "$";
        if (addr instanceof SedTypes.LineNumberAddress la) return String.valueOf(la.line());
        if (addr instanceof SedTypes.PatternAddress pa) return "/" + pa.pattern() + "/";
        return "unknown";
    }

    private static boolean isInRange(SedTypes.AddressRange range, int lineNum, int totalLines, String line, SedTypes.SedState state) {
        if (range == null || (range.start() == null && range.end() == null)) {
            return true;
        }

        boolean result;
        if (range.start() != null && range.end() == null) {
            result = matchesAddress(range.start(), lineNum, totalLines, line, state);
        } else if (range.start() != null) {
            String key = serializeRange(range);
            SedTypes.RangeState rs = state.rangeStates.get(key);
            if (rs == null) {
                rs = new SedTypes.RangeState();
                state.rangeStates.put(key, rs);
            }

            if (!rs.active) {
                if (rs.completed) {
                    result = false;
                } else {
                    boolean startMatches = matchesAddress(range.start(), lineNum, totalLines, line, state);
                    if (startMatches) {
                        rs.active = true;
                        rs.startLine = lineNum;
                        if (matchesAddress(range.end(), lineNum, totalLines, line, state)) {
                            rs.active = false;
                            rs.completed = true;
                        }
                        result = true;
                    } else {
                        result = false;
                    }
                }
            } else {
                result = true;
                if (matchesAddress(range.end(), lineNum, totalLines, line, state)) {
                    rs.active = false;
                    rs.completed = true;
                }
            }
        } else {
            result = true;
        }

        if (range.negated()) {
            return !result;
        }
        return result;
    }

    public static void executeCommands(List<SedTypes.SedCmd> commands, SedTypes.SedState state, String[] lines, int currentLineIndex) {
        Map<String, Integer> labelIndex = new HashMap<>();
        for (int i = 0; i < commands.size(); i++) {
            SedTypes.SedCmd cmd = commands.get(i);
            if (cmd instanceof SedTypes.LabelCmd lc) {
                labelIndex.put(lc.name(), i);
            }
        }

        int maxIterations = DEFAULT_MAX_ITERATIONS;
        int iterations = 0;
        int i = 0;

        while (i < commands.size()) {
            iterations++;
            if (iterations > maxIterations) break;
            if (state.deleted || state.quit || state.quitSilent || state.restartCycle) break;

            SedTypes.SedCmd cmd = commands.get(i);

            if (cmd instanceof SedTypes.LabelCmd) {
                i++;
                continue;
            }

            if (cmd instanceof SedTypes.NextCmd nc) {
                if (isInRange(nc.address(), state.lineNumber, state.totalLines, state.patternSpace, state)) {
                    state.nCommandOutput.add(state.patternSpace);
                    if (currentLineIndex + state.linesConsumedInCycle + 1 < lines.length) {
                        state.linesConsumedInCycle++;
                        state.patternSpace = lines[currentLineIndex + state.linesConsumedInCycle];
                        state.lineNumber = currentLineIndex + state.linesConsumedInCycle + 1;
                        state.substitutionMade = false;
                    } else {
                        state.quit = true;
                        state.deleted = true;
                        break;
                    }
                }
                i++;
                continue;
            }

            if (cmd instanceof SedTypes.NextAppendCmd nc) {
                if (isInRange(nc.address(), state.lineNumber, state.totalLines, state.patternSpace, state)) {
                    if (currentLineIndex + state.linesConsumedInCycle + 1 < lines.length) {
                        state.linesConsumedInCycle++;
                        state.patternSpace += "\n" + lines[currentLineIndex + state.linesConsumedInCycle];
                        state.lineNumber = currentLineIndex + state.linesConsumedInCycle + 1;
                    } else {
                        state.quit = true;
                        break;
                    }
                }
                i++;
                continue;
            }

            if (cmd instanceof SedTypes.BranchCmd bc) {
                if (isInRange(bc.address(), state.lineNumber, state.totalLines, state.patternSpace, state)) {
                    if (bc.label() != null && !bc.label().isEmpty()) {
                        Integer target = labelIndex.get(bc.label());
                        if (target != null) {
                            i = target;
                            continue;
                        }
                    }
                    break;
                }
                i++;
                continue;
            }

            if (cmd instanceof SedTypes.BranchOnSubstCmd bc) {
                if (isInRange(bc.address(), state.lineNumber, state.totalLines, state.patternSpace, state)) {
                    if (state.substitutionMade) {
                        state.substitutionMade = false;
                        if (bc.label() != null && !bc.label().isEmpty()) {
                            Integer target = labelIndex.get(bc.label());
                            if (target != null) {
                                i = target;
                                continue;
                            }
                        }
                        break;
                    }
                }
                i++;
                continue;
            }

            if (cmd instanceof SedTypes.BranchOnNoSubstCmd bc) {
                if (isInRange(bc.address(), state.lineNumber, state.totalLines, state.patternSpace, state)) {
                    if (!state.substitutionMade) {
                        if (bc.label() != null && !bc.label().isEmpty()) {
                            Integer target = labelIndex.get(bc.label());
                            if (target != null) {
                                i = target;
                                continue;
                            }
                        }
                        break;
                    }
                }
                i++;
                continue;
            }

            if (cmd instanceof SedTypes.GroupCmd gc) {
                if (isInRange(gc.address(), state.lineNumber, state.totalLines, state.patternSpace, state)) {
                    executeCommands(gc.commands(), state, lines, currentLineIndex);
                }
                i++;
                continue;
            }

            executeSingleCommand(cmd, state);
            i++;
        }
    }

    private static void executeSingleCommand(SedTypes.SedCmd cmd, SedTypes.SedState state) {
        if (!isInRange(cmd.address(), state.lineNumber, state.totalLines, state.patternSpace, state)) {
            return;
        }

        switch (cmd) {
            case SedTypes.SubstituteCmd sc -> executeSubstitute(sc, state);
            case SedTypes.DeleteCmd dc -> state.deleted = true;
            case SedTypes.PrintCmd pc -> state.lineNumberOutput.add(state.patternSpace);
            case SedTypes.QuitCmd qc -> {
                state.quit = true;
                if (qc.exitCode() != null) state.exitCode = qc.exitCode();
            }
            case SedTypes.AppendCmd ac -> state.appendBuffer.add(ac.text());
            case SedTypes.InsertCmd ic -> state.appendBuffer.add(0, "__INSERT__" + ic.text());
            case SedTypes.ChangeCmd cc -> {
                state.deleted = true;
                state.appendBuffer.add(cc.text());
            }
            case SedTypes.TransliterateCmd tc -> {
                StringBuilder sb = new StringBuilder();
                for (char c : state.patternSpace.toCharArray()) {
                    int idx = tc.source().indexOf(c);
                    sb.append(idx >= 0 ? tc.dest().charAt(idx) : c);
                }
                state.patternSpace = sb.toString();
            }
            case SedTypes.LineNumberCmd lc -> state.lineNumberOutput.add(String.valueOf(state.lineNumber));
            case SedTypes.HoldCmd hc -> state.holdSpace = state.patternSpace;
            case SedTypes.HoldAppendCmd hc -> {
                if (state.holdSpace.isEmpty()) state.holdSpace = state.patternSpace;
                else state.holdSpace += "\n" + state.patternSpace;
            }
            case SedTypes.GetCmd gc -> state.patternSpace = state.holdSpace;
            case SedTypes.GetAppendCmd gc -> state.patternSpace += "\n" + state.holdSpace;
            case SedTypes.ExchangeCmd xc -> {
                String tmp = state.patternSpace;
                state.patternSpace = state.holdSpace;
                state.holdSpace = tmp;
            }
            default -> {}
        }
    }

    private static void executeSubstitute(SedTypes.SubstituteCmd cmd, SedTypes.SedState state) {
        String rawPattern = cmd.pattern();
        if (rawPattern.isEmpty() && state.lastPattern != null) {
            rawPattern = state.lastPattern;
        } else if (!rawPattern.isEmpty()) {
            state.lastPattern = rawPattern;
        }

        String pattern = cmd.extendedRegex() ? rawPattern : breToEre(rawPattern);
        int flags = cmd.ignoreCase() ? Pattern.CASE_INSENSITIVE : 0;

        try {
            Pattern p = Pattern.compile(pattern, flags);
            Matcher m = p.matcher(state.patternSpace);

            if (!m.find()) return;
            m.reset();

            state.substitutionMade = true;

            if (cmd.nthOccurrence() != null && cmd.nthOccurrence() > 0 && !cmd.global()) {
                int count = 0;
                StringBuilder result = new StringBuilder();
                int lastEnd = 0;
                while (m.find()) {
                    count++;
                    if (count == cmd.nthOccurrence()) {
                        result.append(state.patternSpace, lastEnd, m.start());
                        result.append(processReplacement(cmd.replacement(), m.group(), getGroups(m)));
                        lastEnd = m.end();
                        break;
                    }
                }
                result.append(state.patternSpace.substring(lastEnd));
                state.patternSpace = result.toString();
            } else if (cmd.global()) {
                state.patternSpace = globalReplace(state.patternSpace, p, cmd.replacement());
            } else {
                if (m.find()) {
                    String replacement = processReplacement(cmd.replacement(), m.group(), getGroups(m));
                    state.patternSpace = state.patternSpace.substring(0, m.start()) + replacement + state.patternSpace.substring(m.end());
                }
            }

            if (cmd.printOnMatch()) {
                state.lineNumberOutput.add(state.patternSpace);
            }
        } catch (Exception e) {
            // Invalid regex, skip
        }
    }

    private static String[] getGroups(Matcher m) {
        List<String> groups = new ArrayList<>();
        for (int i = 1; i <= m.groupCount(); i++) {
            groups.add(m.group(i));
        }
        return groups.toArray(new String[0]);
    }

    private static String globalReplace(String input, Pattern pattern, String replacement) {
        StringBuilder result = new StringBuilder();
        Matcher m = pattern.matcher(input);
        int pos = 0;
        boolean skipZeroLength = false;

        while (pos <= input.length()) {
            m.region(pos, input.length());
            if (!m.find()) {
                result.append(input.substring(pos));
                break;
            }
            if (m.start() != pos) {
                result.append(input, pos, m.start());
                pos = m.start();
                skipZeroLength = false;
                continue;
            }

            String matched = m.group();
            if (skipZeroLength && matched.isEmpty()) {
                if (pos < input.length()) {
                    result.append(input.charAt(pos));
                    pos++;
                } else {
                    break;
                }
                skipZeroLength = false;
                continue;
            }

            result.append(processReplacement(replacement, matched, getGroups(m)));
            skipZeroLength = false;

            if (matched.isEmpty()) {
                if (pos < input.length()) {
                    result.append(input.charAt(pos));
                    pos++;
                } else {
                    break;
                }
            } else {
                pos += matched.length();
                skipZeroLength = true;
            }
        }
        return result.toString();
    }

    private static String processReplacement(String replacement, String match, String[] groups) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < replacement.length(); i++) {
            char c = replacement.charAt(i);
            if (c == '\\' && i + 1 < replacement.length()) {
                char next = replacement.charAt(i + 1);
                if (next == '&') { result.append('&'); i++; }
                else if (next == 'n') { result.append('\n'); i++; }
                else if (next == 't') { result.append('\t'); i++; }
                else if (next == 'r') { result.append('\r'); i++; }
                else if (next >= '0' && next <= '9') {
                    int digit = next - '0';
                    if (digit == 0) result.append(match);
                    else if (digit <= groups.length) result.append(groups[digit - 1] != null ? groups[digit - 1] : "");
                    i++;
                } else {
                    result.append(next);
                    i++;
                }
            } else if (c == '&') {
                result.append(match);
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    static String breToEre(String pattern) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '\\' && i + 1 < pattern.length()) {
                char next = pattern.charAt(i + 1);
                switch (next) {
                    case '+', '?', '|', '(', ')', '{', '}', '<', '>' -> {
                        result.append(next);
                        i++;
                        continue;
                    }
                    case '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                        result.append("$").append(next);
                        i++;
                        continue;
                    }
                    case 'n' -> { result.append('\n'); i++; continue; }
                    case 't' -> { result.append('\t'); i++; continue; }
                    case 'r' -> { result.append('\r'); i++; continue; }
                    default -> {
                        result.append(c);
                        result.append(next);
                        i++;
                        continue;
                    }
                }
            }
            if (c == '.') { result.append("[\\s\\S]"); continue; }
            if ("[](){}^$|*+?".indexOf(c) >= 0) {
                result.append('\\').append(c);
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
