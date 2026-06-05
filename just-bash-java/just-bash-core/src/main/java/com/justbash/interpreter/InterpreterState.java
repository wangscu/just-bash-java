package com.justbash.interpreter;

import com.justbash.ast.command.FunctionDefNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class InterpreterState {

    public record LocalVarEntry(String value, int scopeIndex) {}

    // Core Environment
    Map<String, String> env;
    String cwd;
    String previousDir;

    // Execution Tracking
    int lastExitCode;
    String lastArg;
    int currentLine;

    // Shell Options
    ShellOptions options;
    ShoptOptions shoptOptions;

    // Variable Attributes
    Set<String> readonlyVars;
    Set<String> associativeArrays;
    Set<String> namerefs;
    Set<String> boundNamerefs;
    Set<String> invalidNamerefs;
    Set<String> integerVars;
    Set<String> lowercaseVars;
    Set<String> uppercaseVars;
    Set<String> exportedVars;
    Set<String> tempExportedVars;
    List<Set<String>> localExportedVars;
    Set<String> declaredVars;

    // Local Scoping
    List<Map<String, String>> localScopes;
    Map<String, Integer> localVarDepth;
    Map<String, List<LocalVarEntry>> localVarStack;
    Map<String, Integer> fullyUnsetLocals;
    List<Map<String, String>> tempEnvBindings;
    Set<String> mutatedTempEnvVars;
    Set<String> accessedTempEnvVars;

    // Call Stack
    Map<String, FunctionDefNode> functions;
    int callDepth;
    int sourceDepth;
    List<Integer> callLineStack;
    List<String> funcNameStack;
    List<String> sourceStack;
    String currentSource;

    // Control Flow
    boolean inCondition;
    int loopDepth;
    boolean parentHasLoopContext;
    boolean errexitSafe;

    // Process State
    int commandCount;
    long startTime;
    int lastBackgroundPid;
    int bashPid;
    int nextVirtualPid;
    int virtualPid;
    int virtualPpid;
    int virtualUid;
    int virtualGid;

    // IO State
    String groupStdin;
    Map<Integer, String> fileDescriptors;
    int nextFd;

    // Expansion State
    Integer expansionExitCode;
    String expansionStderr;

    // Other
    Map<String, CompletionSpec> completionSpecs;
    List<String> directoryStack;
    Map<String, String> hashTable;
    boolean suppressVerbose;
    List<String> extraArgs;

    public InterpreterState() {
        this.env = new LinkedHashMap<>();
        this.cwd = "/home/user";
        this.previousDir = "/home/user";

        this.lastExitCode = 0;
        this.lastArg = "";
        this.currentLine = 0;

        this.options = ShellOptions.defaults();
        this.shoptOptions = ShoptOptions.defaults();

        this.readonlyVars = new HashSet<>();
        this.associativeArrays = new HashSet<>();
        this.namerefs = new HashSet<>();
        this.boundNamerefs = new HashSet<>();
        this.invalidNamerefs = new HashSet<>();
        this.integerVars = new HashSet<>();
        this.lowercaseVars = new HashSet<>();
        this.uppercaseVars = new HashSet<>();
        this.exportedVars = new HashSet<>();
        this.tempExportedVars = new HashSet<>();
        this.localExportedVars = new ArrayList<>();
        this.declaredVars = new HashSet<>();

        this.localScopes = new ArrayList<>();
        this.localVarDepth = new HashMap<>();
        this.localVarStack = new HashMap<>();
        this.fullyUnsetLocals = new HashMap<>();
        this.tempEnvBindings = new ArrayList<>();
        this.mutatedTempEnvVars = new HashSet<>();
        this.accessedTempEnvVars = new HashSet<>();

        this.functions = new HashMap<>();
        this.callDepth = 0;
        this.sourceDepth = 0;
        this.callLineStack = new ArrayList<>();
        this.funcNameStack = new ArrayList<>();
        this.sourceStack = new ArrayList<>();
        this.currentSource = "";

        this.inCondition = false;
        this.loopDepth = 0;
        this.parentHasLoopContext = false;
        this.errexitSafe = false;

        this.commandCount = 0;
        this.startTime = System.currentTimeMillis();
        this.lastBackgroundPid = 0;
        this.bashPid = 0;
        this.nextVirtualPid = 0;
        this.virtualPid = 1;
        this.virtualPpid = 0;
        this.virtualUid = 1000;
        this.virtualGid = 1000;

        this.groupStdin = "";
        this.fileDescriptors = new HashMap<>();
        this.nextFd = 10;

        this.expansionExitCode = null;
        this.expansionStderr = "";

        this.completionSpecs = new HashMap<>();
        this.directoryStack = new ArrayList<>();
        this.hashTable = new HashMap<>();
        this.suppressVerbose = false;
        this.extraArgs = new ArrayList<>();
    }

    public static InterpreterState defaults() {
        return new InterpreterState();
    }

    public InterpreterState copy() {
        InterpreterState copy = new InterpreterState();

        copy.env = new LinkedHashMap<>(this.env);
        copy.cwd = this.cwd;
        copy.previousDir = this.previousDir;

        copy.lastExitCode = this.lastExitCode;
        copy.lastArg = this.lastArg;
        copy.currentLine = this.currentLine;

        copy.options = this.options.copy();
        copy.shoptOptions = this.shoptOptions.copy();

        copy.readonlyVars = new HashSet<>(this.readonlyVars);
        copy.associativeArrays = new HashSet<>(this.associativeArrays);
        copy.namerefs = new HashSet<>(this.namerefs);
        copy.boundNamerefs = new HashSet<>(this.boundNamerefs);
        copy.invalidNamerefs = new HashSet<>(this.invalidNamerefs);
        copy.integerVars = new HashSet<>(this.integerVars);
        copy.lowercaseVars = new HashSet<>(this.lowercaseVars);
        copy.uppercaseVars = new HashSet<>(this.uppercaseVars);
        copy.exportedVars = new HashSet<>(this.exportedVars);
        copy.tempExportedVars = new HashSet<>(this.tempExportedVars);
        copy.localExportedVars = new ArrayList<>();
        for (Set<String> set : this.localExportedVars) {
            copy.localExportedVars.add(new HashSet<>(set));
        }
        copy.declaredVars = new HashSet<>(this.declaredVars);

        copy.localScopes = new ArrayList<>();
        for (Map<String, String> scope : this.localScopes) {
            copy.localScopes.add(new HashMap<>(scope));
        }
        copy.localVarDepth = new HashMap<>(this.localVarDepth);
        copy.localVarStack = new HashMap<>();
        for (Map.Entry<String, List<LocalVarEntry>> entry : this.localVarStack.entrySet()) {
            List<LocalVarEntry> copiedList = new ArrayList<>();
            for (LocalVarEntry e : entry.getValue()) {
                copiedList.add(new LocalVarEntry(e.value(), e.scopeIndex()));
            }
            copy.localVarStack.put(entry.getKey(), copiedList);
        }
        copy.fullyUnsetLocals = new HashMap<>(this.fullyUnsetLocals);
        copy.tempEnvBindings = new ArrayList<>();
        for (Map<String, String> binding : this.tempEnvBindings) {
            copy.tempEnvBindings.add(new HashMap<>(binding));
        }
        copy.mutatedTempEnvVars = new HashSet<>(this.mutatedTempEnvVars);
        copy.accessedTempEnvVars = new HashSet<>(this.accessedTempEnvVars);

        copy.functions = new HashMap<>(this.functions);
        copy.callDepth = this.callDepth;
        copy.sourceDepth = this.sourceDepth;
        copy.callLineStack = new ArrayList<>(this.callLineStack);
        copy.funcNameStack = new ArrayList<>(this.funcNameStack);
        copy.sourceStack = new ArrayList<>(this.sourceStack);
        copy.currentSource = this.currentSource;

        copy.inCondition = this.inCondition;
        copy.loopDepth = this.loopDepth;
        copy.parentHasLoopContext = this.parentHasLoopContext;
        copy.errexitSafe = this.errexitSafe;

        copy.commandCount = this.commandCount;
        copy.startTime = this.startTime;
        copy.lastBackgroundPid = this.lastBackgroundPid;
        copy.bashPid = this.bashPid;
        copy.nextVirtualPid = this.nextVirtualPid;
        copy.virtualPid = this.virtualPid;
        copy.virtualPpid = this.virtualPpid;
        copy.virtualUid = this.virtualUid;
        copy.virtualGid = this.virtualGid;

        copy.groupStdin = this.groupStdin;
        copy.fileDescriptors = new HashMap<>(this.fileDescriptors);
        copy.nextFd = this.nextFd;

        copy.expansionExitCode = this.expansionExitCode;
        copy.expansionStderr = this.expansionStderr;

        copy.completionSpecs = new HashMap<>();
        for (Map.Entry<String, CompletionSpec> entry : this.completionSpecs.entrySet()) {
            copy.completionSpecs.put(entry.getKey(), entry.getValue().copy());
        }
        copy.directoryStack = new ArrayList<>(this.directoryStack);
        copy.hashTable = new HashMap<>(this.hashTable);
        copy.suppressVerbose = this.suppressVerbose;
        copy.extraArgs = new ArrayList<>(this.extraArgs);

        return copy;
    }

    public void prependOutput(String stdout, String stderr) {
        // Placeholder for control flow error output prepending
        // Actual implementation will be added when interpreter is built
    }

    public void incrementCommandCount() {
        this.commandCount++;
    }

    public void pushLocalScope() {
        this.localScopes.add(new HashMap<>());
        this.localExportedVars.add(new HashSet<>());
        this.tempEnvBindings.add(new HashMap<>());
    }

    public void popLocalScope() {
        if (!this.localScopes.isEmpty()) {
            this.localScopes.removeLast();
        }
        if (!this.localExportedVars.isEmpty()) {
            this.localExportedVars.removeLast();
        }
        if (!this.tempEnvBindings.isEmpty()) {
            this.tempEnvBindings.removeLast();
        }
    }

    // Getters and setters for external access
    public Map<String, String> getEnv() {
        return env;
    }

    public void setEnv(Map<String, String> env) {
        this.env = env;
    }

    public String getCwd() {
        return cwd;
    }

    public void setCwd(String cwd) {
        this.cwd = cwd;
    }

    public int getLastExitCode() {
        return lastExitCode;
    }

    public void setLastExitCode(int lastExitCode) {
        this.lastExitCode = lastExitCode;
    }

    public Set<String> getExportedVars() {
        return exportedVars;
    }

    public void setExportedVars(Set<String> exportedVars) {
        this.exportedVars = exportedVars;
    }

    public String getPreviousDir() {
        return previousDir;
    }

    public void setPreviousDir(String previousDir) {
        this.previousDir = previousDir;
    }
}
