package com.justbash.commands.sed;

import java.util.List;
import java.util.Map;

public class SedTypes {

    public record AddressRange(SedAddress start, SedAddress end, boolean negated) {}

    public interface SedAddress {}
    public record LineNumberAddress(int line) implements SedAddress {}
    public record LastLineAddress() implements SedAddress {}
    public record PatternAddress(String pattern, boolean extendedRegex) implements SedAddress {}
    public record StepAddress(int first, int step) implements SedAddress {}

    public interface SedCmd {
        AddressRange address();
    }

    public record SubstituteCmd(AddressRange address, String pattern, String replacement,
                                 boolean global, boolean ignoreCase, boolean printOnMatch,
                                 Integer nthOccurrence, boolean extendedRegex) implements SedCmd {}
    public record DeleteCmd(AddressRange address) implements SedCmd {}
    public record PrintCmd(AddressRange address) implements SedCmd {}
    public record QuitCmd(AddressRange address, Integer exitCode) implements SedCmd {}
    public record NextCmd(AddressRange address) implements SedCmd {}
    public record NextAppendCmd(AddressRange address) implements SedCmd {}
    public record AppendCmd(AddressRange address, String text) implements SedCmd {}
    public record InsertCmd(AddressRange address, String text) implements SedCmd {}
    public record ChangeCmd(AddressRange address, String text) implements SedCmd {}
    public record TransliterateCmd(AddressRange address, String source, String dest) implements SedCmd {}
    public record LineNumberCmd(AddressRange address) implements SedCmd {}
    public record HoldCmd(AddressRange address) implements SedCmd {}
    public record HoldAppendCmd(AddressRange address) implements SedCmd {}
    public record GetCmd(AddressRange address) implements SedCmd {}
    public record GetAppendCmd(AddressRange address) implements SedCmd {}
    public record ExchangeCmd(AddressRange address) implements SedCmd {}
    public record BranchCmd(AddressRange address, String label) implements SedCmd {}
    public record BranchOnSubstCmd(AddressRange address, String label) implements SedCmd {}
    public record BranchOnNoSubstCmd(AddressRange address, String label) implements SedCmd {}
    public record LabelCmd(String name) implements SedCmd {
        public AddressRange address() { return null; }
    }
    public record GroupCmd(AddressRange address, List<SedCmd> commands) implements SedCmd {}

    public static class SedState {
        public String patternSpace = "";
        public String holdSpace = "";
        public int lineNumber = 0;
        public int totalLines = 0;
        public boolean deleted = false;
        public boolean printed = false;
        public boolean quit = false;
        public boolean quitSilent = false;
        public Integer exitCode = null;
        public String errorMessage = null;
        public java.util.List<String> appendBuffer = new java.util.ArrayList<>();
        public boolean substitutionMade = false;
        public java.util.List<String> lineNumberOutput = new java.util.ArrayList<>();
        public java.util.List<String> nCommandOutput = new java.util.ArrayList<>();
        public boolean restartCycle = false;
        public int linesConsumedInCycle = 0;
        public String lastPattern = null;
        public String branchRequest = null;
        public String currentFilename = null;
        public Map<String, RangeState> rangeStates = new java.util.HashMap<>();
    }

    public static class RangeState {
        public boolean active = false;
        public boolean completed = false;
        public int startLine = 0;
    }

    public static class ParseResult {
        public java.util.List<SedCmd> commands = new java.util.ArrayList<>();
        public String error = null;
        public boolean silentMode = false;
    }
}
