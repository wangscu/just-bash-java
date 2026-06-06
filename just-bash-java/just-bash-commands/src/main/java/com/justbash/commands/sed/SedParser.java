package com.justbash.commands.sed;

import java.util.ArrayList;
import java.util.List;

public class SedParser {

    private final String script;
    private int pos = 0;
    private final boolean extendedRegex;

    public SedParser(String script, boolean extendedRegex) {
        this.script = script;
        this.extendedRegex = extendedRegex;
    }

    public SedTypes.ParseResult parse() {
        SedTypes.ParseResult result = new SedTypes.ParseResult();
        pos = 0;

        // Check for #n or #r comment at start
        if (script.startsWith("#n") || script.startsWith("#N")) {
            result.silentMode = true;
        }

        while (pos < script.length()) {
            skipWhitespaceAndSeparators();
            if (pos >= script.length()) break;

            var cmdResult = parseCommand();
            if (cmdResult.error != null) {
                result.error = cmdResult.error;
                return result;
            }
            if (cmdResult.cmd != null) {
                result.commands.add(cmdResult.cmd);
            }
        }

        // Validate labels
        String labelError = validateLabels(result.commands);
        if (labelError != null) {
            result.error = labelError;
        }

        return result;
    }

    private void skipWhitespaceAndSeparators() {
        while (pos < script.length()) {
            char c = script.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == ';') {
                pos++;
            } else {
                break;
            }
        }
    }

    private static class CmdParseResult {
        SedTypes.SedCmd cmd;
        String error;
        CmdParseResult(SedTypes.SedCmd cmd) { this.cmd = cmd; }
        CmdParseResult(String error) { this.error = error; }
    }

    private CmdParseResult parseCommand() {
        // Parse optional address range
        SedTypes.AddressRange address = parseAddressRange();

        // Check for negation
        boolean negated = false;
        if (pos < script.length() && script.charAt(pos) == '!') {
            negated = true;
            pos++;
        }
        if (address != null && negated) {
            address = new SedTypes.AddressRange(address.start(), address.end(), true);
        }

        skipWhitespaceAndSeparators();
        if (pos >= script.length()) {
            if (address != null && (address.start() != null || address.end() != null)) {
                return new CmdParseResult("command expected");
            }
            return new CmdParseResult((SedTypes.SedCmd) null);
        }

        char cmdChar = script.charAt(pos++);

        switch (cmdChar) {
            case 's':
                return parseSubstitute(address);
            case 'y':
                return parseTransliterate(address);
            case 'd':
                return new CmdParseResult(new SedTypes.DeleteCmd(address));
            case 'D':
                return new CmdParseResult(new SedTypes.DeleteCmd(address));
            case 'p':
                return new CmdParseResult(new SedTypes.PrintCmd(address));
            case 'P':
                return new CmdParseResult(new SedTypes.PrintCmd(address));
            case 'q':
                return new CmdParseResult(new SedTypes.QuitCmd(address, null));
            case 'Q':
                return new CmdParseResult(new SedTypes.QuitCmd(address, null));
            case 'n':
                return new CmdParseResult(new SedTypes.NextCmd(address));
            case 'N':
                return new CmdParseResult(new SedTypes.NextAppendCmd(address));
            case 'h':
                return new CmdParseResult(new SedTypes.HoldCmd(address));
            case 'H':
                return new CmdParseResult(new SedTypes.HoldAppendCmd(address));
            case 'g':
                return new CmdParseResult(new SedTypes.GetCmd(address));
            case 'G':
                return new CmdParseResult(new SedTypes.GetAppendCmd(address));
            case 'x':
                return new CmdParseResult(new SedTypes.ExchangeCmd(address));
            case '=':
                return new CmdParseResult(new SedTypes.LineNumberCmd(address));
            case 'a':
            case 'i':
            case 'c':
                return parseTextCommand(cmdChar, address);
            case 'b':
                return new CmdParseResult(new SedTypes.BranchCmd(address, readLabel()));
            case 't':
                return new CmdParseResult(new SedTypes.BranchOnSubstCmd(address, readLabel()));
            case 'T':
                return new CmdParseResult(new SedTypes.BranchOnNoSubstCmd(address, readLabel()));
            case ':':
                return new CmdParseResult(new SedTypes.LabelCmd(readLabel()));
            case '{':
                return parseGroup(address);
            case '}':
                return new CmdParseResult((SedTypes.SedCmd) null);
            case '#':
                skipToEndOfLine();
                return new CmdParseResult((SedTypes.SedCmd) null);
            default:
                if (address != null && (address.start() != null || address.end() != null)) {
                    return new CmdParseResult("command expected");
                }
                return new CmdParseResult((SedTypes.SedCmd) null);
        }
    }

    private SedTypes.AddressRange parseAddressRange() {
        SedTypes.SedAddress start = parseAddress();
        if (start == null) return null;

        if (pos < script.length() && script.charAt(pos) == ',') {
            pos++;
            SedTypes.SedAddress end = parseAddress();
            if (end == null) {
                // Handle incomplete range - just treat start as single address
                return new SedTypes.AddressRange(start, null, false);
            }
            return new SedTypes.AddressRange(start, end, false);
        }

        return new SedTypes.AddressRange(start, null, false);
    }

    private SedTypes.SedAddress parseAddress() {
        skipWhitespaceAndSeparators();
        if (pos >= script.length()) return null;

        char c = script.charAt(pos);

        if (c == '$') {
            pos++;
            return new SedTypes.LastLineAddress();
        }

        if (c == '/') {
            pos++;
            StringBuilder pattern = new StringBuilder();
            while (pos < script.length() && script.charAt(pos) != '/') {
                if (script.charAt(pos) == '\\' && pos + 1 < script.length()) {
                    pattern.append(script.charAt(pos++));
                }
                pattern.append(script.charAt(pos++));
            }
            if (pos < script.length() && script.charAt(pos) == '/') {
                pos++;
            }
            return new SedTypes.PatternAddress(pattern.toString(), extendedRegex);
        }

        if (Character.isDigit(c)) {
            int num = 0;
            while (pos < script.length() && Character.isDigit(script.charAt(pos))) {
                num = num * 10 + (script.charAt(pos++) - '0');
            }
            return new SedTypes.LineNumberAddress(num);
        }

        return null;
    }

    private CmdParseResult parseSubstitute(SedTypes.AddressRange address) {
        char delimiter = script.charAt(pos++);
        String pattern = readUntilDelimiter(delimiter);
        String replacement = readUntilDelimiter(delimiter);

        boolean global = false;
        boolean ignoreCase = false;
        boolean printOnMatch = false;
        Integer nth = null;

        while (pos < script.length()) {
            char f = script.charAt(pos);
            if (f == 'g') { global = true; pos++; }
            else if (f == 'i' || f == 'I') { ignoreCase = true; pos++; }
            else if (f == 'p') { printOnMatch = true; pos++; }
            else if (f >= '1' && f <= '9') {
                int num = 0;
                while (pos < script.length() && Character.isDigit(script.charAt(pos))) {
                    num = num * 10 + (script.charAt(pos++) - '0');
                }
                nth = num;
            } else {
                break;
            }
        }

        return new CmdParseResult(new SedTypes.SubstituteCmd(
            address, pattern, replacement, global, ignoreCase, printOnMatch, nth, extendedRegex));
    }

    private CmdParseResult parseTransliterate(SedTypes.AddressRange address) {
        if (pos >= script.length()) return new CmdParseResult("unterminated `y' command");
        char delimiter = script.charAt(pos++);
        String source = readUntilDelimiter(delimiter);
        String dest = readUntilDelimiter(delimiter);

        if (source.length() != dest.length()) {
            return new CmdParseResult("transliteration sets must have same length");
        }

        return new CmdParseResult(new SedTypes.TransliterateCmd(address, source, dest));
    }

    private CmdParseResult parseTextCommand(char cmd, SedTypes.AddressRange address) {
        String text = readTextAfterBackslash();
        return switch (cmd) {
            case 'a' -> new CmdParseResult(new SedTypes.AppendCmd(address, text));
            case 'i' -> new CmdParseResult(new SedTypes.InsertCmd(address, text));
            case 'c' -> new CmdParseResult(new SedTypes.ChangeCmd(address, text));
            default -> new CmdParseResult("unknown text command");
        };
    }

    private String readTextAfterBackslash() {
        skipWhitespaceAndSeparators();
        if (pos < script.length() && script.charAt(pos) == '\\') {
            pos++;
            skipWhitespaceAndSeparators();
        }
        StringBuilder text = new StringBuilder();
        while (pos < script.length()) {
            char c = script.charAt(pos);
            if (c == '\n' || c == ';') break;
            if (c == '\\' && pos + 1 < script.length()) {
                pos++;
                char next = script.charAt(pos);
                text.append(switch (next) {
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    case 'r' -> '\r';
                    default -> next;
                });
                pos++;
            } else {
                text.append(c);
                pos++;
            }
        }
        return text.toString();
    }

    private CmdParseResult parseGroup(SedTypes.AddressRange address) {
        List<SedTypes.SedCmd> commands = new ArrayList<>();
        while (pos < script.length()) {
            skipWhitespaceAndSeparators();
            if (pos >= script.length()) break;
            if (script.charAt(pos) == '}') {
                pos++;
                return new CmdParseResult(new SedTypes.GroupCmd(address, commands));
            }
            var result = parseCommand();
            if (result.error != null) {
                return result;
            }
            if (result.cmd != null) {
                commands.add(result.cmd);
            }
        }
        return new CmdParseResult("unmatched `{'");
    }

    private String readUntilDelimiter(char delimiter) {
        StringBuilder sb = new StringBuilder();
        while (pos < script.length()) {
            char c = script.charAt(pos);
            if (c == delimiter) {
                pos++;
                break;
            }
            if (c == '\\' && pos + 1 < script.length()) {
                sb.append(c);
                sb.append(script.charAt(++pos));
                pos++;
            } else {
                sb.append(c);
                pos++;
            }
        }
        return sb.toString();
    }

    private String readLabel() {
        skipWhitespaceAndSeparators();
        StringBuilder label = new StringBuilder();
        while (pos < script.length()) {
            char c = script.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == ';') break;
            label.append(c);
            pos++;
        }
        return label.toString();
    }

    private void skipToEndOfLine() {
        while (pos < script.length() && script.charAt(pos) != '\n') {
            pos++;
        }
    }

    private String validateLabels(List<SedTypes.SedCmd> commands) {
        List<String> definedLabels = new ArrayList<>();
        collectLabels(commands, definedLabels);

        for (SedTypes.SedCmd cmd : commands) {
            String undefined = findUndefinedLabel(cmd, definedLabels);
            if (undefined != null) {
                return "undefined label '" + undefined + "'";
            }
        }
        return null;
    }

    private void collectLabels(List<SedTypes.SedCmd> commands, List<String> labels) {
        for (SedTypes.SedCmd cmd : commands) {
            if (cmd instanceof SedTypes.LabelCmd lc) {
                labels.add(lc.name());
            } else if (cmd instanceof SedTypes.GroupCmd gc) {
                collectLabels(gc.commands(), labels);
            }
        }
    }

    private String findUndefinedLabel(SedTypes.SedCmd cmd, List<String> labels) {
        String label = null;
        if (cmd instanceof SedTypes.BranchCmd bc) label = bc.label();
        else if (cmd instanceof SedTypes.BranchOnSubstCmd bc) label = bc.label();
        else if (cmd instanceof SedTypes.BranchOnNoSubstCmd bc) label = bc.label();

        if (label != null && !label.isEmpty() && !labels.contains(label)) {
            return label;
        }

        if (cmd instanceof SedTypes.GroupCmd gc) {
            for (SedTypes.SedCmd child : gc.commands()) {
                String result = findUndefinedLabel(child, labels);
                if (result != null) return result;
            }
        }
        return null;
    }
}
