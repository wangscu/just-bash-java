package com.justbash.interpreter;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class InterpreterStateTest {

    @Test
    void defaultsCreatesValidState() {
        InterpreterState state = InterpreterState.defaults();

        assertThat(state).isNotNull();
        assertThat(state.env).isNotNull().isEmpty();
        assertThat(state.cwd).isEqualTo("/home/user");
        assertThat(state.previousDir).isEqualTo("/home/user");
        assertThat(state.lastExitCode).isEqualTo(0);
        assertThat(state.lastArg).isEqualTo("");
        assertThat(state.currentLine).isEqualTo(0);

        assertThat(state.options).isNotNull();
        assertThat(state.options.errexit).isFalse();
        assertThat(state.options.pipefail).isFalse();

        assertThat(state.shoptOptions).isNotNull();
        assertThat(state.shoptOptions.globskipdots).isTrue();
        assertThat(state.shoptOptions.extglob).isFalse();

        assertThat(state.readonlyVars).isNotNull().isEmpty();
        assertThat(state.associativeArrays).isNotNull().isEmpty();
        assertThat(state.namerefs).isNotNull().isEmpty();
        assertThat(state.boundNamerefs).isNotNull().isEmpty();
        assertThat(state.invalidNamerefs).isNotNull().isEmpty();
        assertThat(state.integerVars).isNotNull().isEmpty();
        assertThat(state.lowercaseVars).isNotNull().isEmpty();
        assertThat(state.uppercaseVars).isNotNull().isEmpty();
        assertThat(state.exportedVars).isNotNull().isEmpty();
        assertThat(state.tempExportedVars).isNotNull().isEmpty();
        assertThat(state.localExportedVars).isNotNull().isEmpty();
        assertThat(state.declaredVars).isNotNull().isEmpty();

        assertThat(state.localScopes).isNotNull().isEmpty();
        assertThat(state.localVarDepth).isNotNull().isEmpty();
        assertThat(state.localVarStack).isNotNull().isEmpty();
        assertThat(state.fullyUnsetLocals).isNotNull().isEmpty();
        assertThat(state.tempEnvBindings).isNotNull().isEmpty();
        assertThat(state.mutatedTempEnvVars).isNotNull().isEmpty();
        assertThat(state.accessedTempEnvVars).isNotNull().isEmpty();

        assertThat(state.functions).isNotNull().isEmpty();
        assertThat(state.callDepth).isEqualTo(0);
        assertThat(state.sourceDepth).isEqualTo(0);
        assertThat(state.callLineStack).isNotNull().isEmpty();
        assertThat(state.funcNameStack).isNotNull().isEmpty();
        assertThat(state.sourceStack).isNotNull().isEmpty();
        assertThat(state.currentSource).isEqualTo("");

        assertThat(state.inCondition).isFalse();
        assertThat(state.loopDepth).isEqualTo(0);
        assertThat(state.parentHasLoopContext).isFalse();
        assertThat(state.errexitSafe).isFalse();

        assertThat(state.commandCount).isEqualTo(0);
        assertThat(state.startTime).isGreaterThan(0);
        assertThat(state.lastBackgroundPid).isEqualTo(0);
        assertThat(state.bashPid).isEqualTo(0);
        assertThat(state.nextVirtualPid).isEqualTo(0);
        assertThat(state.virtualPid).isEqualTo(1);
        assertThat(state.virtualPpid).isEqualTo(0);
        assertThat(state.virtualUid).isEqualTo(1000);
        assertThat(state.virtualGid).isEqualTo(1000);

        assertThat(state.groupStdin).isEqualTo("");
        assertThat(state.fileDescriptors).isNotNull().isEmpty();
        assertThat(state.nextFd).isEqualTo(10);

        assertThat(state.expansionExitCode).isNull();
        assertThat(state.expansionStderr).isEqualTo("");

        assertThat(state.completionSpecs).isNotNull().isEmpty();
        assertThat(state.directoryStack).isNotNull().isEmpty();
        assertThat(state.hashTable).isNotNull().isEmpty();
        assertThat(state.suppressVerbose).isFalse();
        assertThat(state.extraArgs).isNotNull().isEmpty();
    }

    @Test
    void copyProducesIndependentCopy() {
        InterpreterState original = InterpreterState.defaults();
        original.env.put("FOO", "bar");
        original.lastExitCode = 42;
        original.options.errexit = true;
        original.readonlyVars.add("x");
        original.localScopes.add(new java.util.HashMap<>());
        original.localScopes.getFirst().put("localVar", "value");
        original.functions.put("myFunc", null);
        original.directoryStack.add("/tmp");

        InterpreterState copy = original.copy();

        // Verify copy has same values
        assertThat(copy.env).containsEntry("FOO", "bar");
        assertThat(copy.lastExitCode).isEqualTo(42);
        assertThat(copy.options.errexit).isTrue();
        assertThat(copy.readonlyVars).contains("x");
        assertThat(copy.localScopes).hasSize(1);
        assertThat(copy.localScopes.getFirst()).containsEntry("localVar", "value");
        assertThat(copy.directoryStack).containsExactly("/tmp");

        // Verify independence - modify original, copy should not change
        original.env.put("BAZ", "qux");
        original.lastExitCode = 99;
        original.options.errexit = false;
        original.readonlyVars.add("y");
        original.localScopes.getFirst().put("newVar", "newValue");
        original.directoryStack.add("/home");

        assertThat(copy.env).doesNotContainKey("BAZ");
        assertThat(copy.lastExitCode).isEqualTo(42);
        assertThat(copy.options.errexit).isTrue();
        assertThat(copy.readonlyVars).doesNotContain("y");
        assertThat(copy.localScopes.getFirst()).doesNotContainKey("newVar");
        assertThat(copy.directoryStack).containsExactly("/tmp");
    }

    @Test
    void pushAndPopLocalScope() {
        InterpreterState state = InterpreterState.defaults();

        assertThat(state.localScopes).isEmpty();
        assertThat(state.localExportedVars).isEmpty();
        assertThat(state.tempEnvBindings).isEmpty();

        state.pushLocalScope();
        assertThat(state.localScopes).hasSize(1);
        assertThat(state.localExportedVars).hasSize(1);
        assertThat(state.tempEnvBindings).hasSize(1);

        state.localScopes.getFirst().put("a", "1");
        state.localExportedVars.getFirst().add("a");
        state.tempEnvBindings.getFirst().put("b", "2");

        state.pushLocalScope();
        assertThat(state.localScopes).hasSize(2);
        assertThat(state.localExportedVars).hasSize(2);
        assertThat(state.tempEnvBindings).hasSize(2);

        state.popLocalScope();
        assertThat(state.localScopes).hasSize(1);
        assertThat(state.localExportedVars).hasSize(1);
        assertThat(state.tempEnvBindings).hasSize(1);
        assertThat(state.localScopes.getFirst()).containsEntry("a", "1");
        assertThat(state.localExportedVars.getFirst()).contains("a");
        assertThat(state.tempEnvBindings.getFirst()).containsEntry("b", "2");

        state.popLocalScope();
        assertThat(state.localScopes).isEmpty();
        assertThat(state.localExportedVars).isEmpty();
        assertThat(state.tempEnvBindings).isEmpty();

        // Pop on empty should not throw
        state.popLocalScope();
        assertThat(state.localScopes).isEmpty();
    }

    @Test
    void incrementCommandCount() {
        InterpreterState state = InterpreterState.defaults();
        assertThat(state.commandCount).isEqualTo(0);

        state.incrementCommandCount();
        assertThat(state.commandCount).isEqualTo(1);

        state.incrementCommandCount();
        assertThat(state.commandCount).isEqualTo(2);
    }

    @Test
    void copyDeepCopiesLocalVarStack() {
        InterpreterState original = InterpreterState.defaults();
        original.localVarStack.put("var1", new java.util.ArrayList<>());
        original.localVarStack.get("var1").add(new InterpreterState.LocalVarEntry("val1", 0));
        original.localVarStack.get("var1").add(new InterpreterState.LocalVarEntry("val2", 1));

        InterpreterState copy = original.copy();

        assertThat(copy.localVarStack).hasSize(1);
        assertThat(copy.localVarStack.get("var1")).hasSize(2);
        assertThat(copy.localVarStack.get("var1").get(0).value()).isEqualTo("val1");
        assertThat(copy.localVarStack.get("var1").get(0).scopeIndex()).isEqualTo(0);
        assertThat(copy.localVarStack.get("var1").get(1).value()).isEqualTo("val2");
        assertThat(copy.localVarStack.get("var1").get(1).scopeIndex()).isEqualTo(1);

        // Modify original, copy should be independent
        original.localVarStack.get("var1").add(new InterpreterState.LocalVarEntry("val3", 2));
        assertThat(copy.localVarStack.get("var1")).hasSize(2);
    }

    @Test
    void copyDeepCopiesCompletionSpecs() {
        InterpreterState original = InterpreterState.defaults();
        CompletionSpec spec = new CompletionSpec();
        spec.wordlist = "foo bar";
        spec.options.add("-a");
        spec.actions.add("files");
        original.completionSpecs.put("cmd", spec);

        InterpreterState copy = original.copy();

        assertThat(copy.completionSpecs).hasSize(1);
        assertThat(copy.completionSpecs.get("cmd").wordlist).isEqualTo("foo bar");
        assertThat(copy.completionSpecs.get("cmd").options).containsExactly("-a");
        assertThat(copy.completionSpecs.get("cmd").actions).containsExactly("files");

        // Modify original
        spec.wordlist = "baz";
        spec.options.add("-b");

        assertThat(copy.completionSpecs.get("cmd").wordlist).isEqualTo("foo bar");
        assertThat(copy.completionSpecs.get("cmd").options).containsExactly("-a");
    }
}
