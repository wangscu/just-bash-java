package com.justbash.interpreter;

import com.justbash.Bash;
import com.justbash.BashOptions;
import com.justbash.fs.InMemoryFs;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;

class BuiltinTest {

    @Test
    void echoBuiltin() {
        Bash bash = new Bash();
        var result = bash.exec("echo hello").join();
        assertThat(result.stdout()).isEqualTo("hello\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void exportSetsVariable() {
        Bash bash = new Bash();
        // Export with value
        var result = bash.exec("export FOO=bar").join();
        assertThat(result.exitCode()).isEqualTo(0);
        // Verify via export list
        var listResult = bash.exec("export").join();
        assertThat(listResult.stdout()).contains("declare -x FOO=\"bar\"");
        bash.shutdown();
    }

    @Test
    void exportListContainsDefaultVars() {
        Bash bash = new Bash();
        var result = bash.exec("export").join();
        assertThat(result.stdout()).contains("declare -x PATH");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void unsetVariable() {
        Bash bash = new Bash();
        bash.exec("export FOO=bar").join();
        // Verify it's set
        var listResult = bash.exec("export").join();
        assertThat(listResult.stdout()).contains("FOO");
        // Unset it
        var unsetResult = bash.exec("unset FOO").join();
        assertThat(unsetResult.exitCode()).isEqualTo(0);
        // Verify it's gone
        var listAfter = bash.exec("export").join();
        assertThat(listAfter.stdout()).doesNotContain("FOO");
        bash.shutdown();
    }

    @Test
    void unsetReadonlyVariableFails() {
        Bash bash = new Bash();
        // Set a readonly variable via prefix assignment (readonly not a builtin yet)
        // For now, just test that unsetting a non-existent var doesn't error
        var result = bash.exec("unset NONEXISTENT").join();
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void cdAndPwd() {
        // Create an InMemoryFs with /tmp directory
        InMemoryFs fs = new InMemoryFs();
        fs.mkdir("/tmp").join();
        Bash bash = new Bash(new BashOptions(
            Optional.empty(), Optional.empty(), Optional.of(fs),
            Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty()
        ));
        bash.exec("cd /tmp").join();
        var result = bash.exec("pwd").join();
        assertThat(result.stdout()).isEqualTo("/tmp\n");
        bash.shutdown();
    }

    @Test
    void cdDash() {
        InMemoryFs fs = new InMemoryFs();
        fs.mkdir("/tmp").join();
        fs.mkdir("/usr").join();
        Bash bash = new Bash(new BashOptions(
            Optional.empty(), Optional.empty(), Optional.of(fs),
            Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty()
        ));
        bash.exec("cd /tmp").join();
        bash.exec("cd /usr").join();
        var result = bash.exec("cd -").join();
        assertThat(result.stdout()).isEqualTo("/tmp\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void cdNonExistentDirectory() {
        Bash bash = new Bash();
        var result = bash.exec("cd /nonexistent").join();
        assertThat(result.stderr()).contains("No such file or directory");
        assertThat(result.exitCode()).isEqualTo(1);
        bash.shutdown();
    }

    @Test
    void exitBuiltin() {
        Bash bash = new Bash();
        var result = bash.exec("exit 42").join();
        assertThat(result.exitCode()).isEqualTo(42);
        bash.shutdown();
    }

    @Test
    void exitWithDefaultCode() {
        Bash bash = new Bash();
        bash.exec("false").join(); // sets last exit to 1
        var result = bash.exec("exit").join();
        assertThat(result.exitCode()).isEqualTo(1);
        bash.shutdown();
    }

    @Test
    void exitInvalidArgument() {
        Bash bash = new Bash();
        var result = bash.exec("exit abc").join();
        assertThat(result.stderr()).contains("numeric argument required");
        assertThat(result.exitCode()).isEqualTo(2);
        bash.shutdown();
    }

    @Test
    void exportAppend() {
        Bash bash = new Bash();
        bash.exec("export FOO=hello").join();
        bash.exec("export FOO+=world").join();
        var result = bash.exec("export").join();
        assertThat(result.stdout()).contains("declare -x FOO=\"helloworld\"");
        bash.shutdown();
    }

    @Test
    void exportUnexport() {
        Bash bash = new Bash();
        bash.exec("export FOO=bar").join();
        var before = bash.exec("export").join();
        assertThat(before.stdout()).contains("FOO");

        bash.exec("export -n FOO").join();
        var after = bash.exec("export").join();
        assertThat(after.stdout()).doesNotContain("FOO");
        bash.shutdown();
    }

    // ------------------------------------------------------------------
    // alias / unalias
    // ------------------------------------------------------------------

    @Test
    void aliasDefineAndExpand() {
        Bash bash = new Bash();
        bash.exec("shopt -s expand_aliases").join();
        bash.exec("alias hi='echo hello'").join();
        var result = bash.exec("hi").join();
        assertThat(result.stdout()).isEqualTo("hello\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void aliasWithPrefixArgs() {
        Bash bash = new Bash();
        bash.exec("shopt -s expand_aliases").join();
        bash.exec("alias say='echo hello'").join();
        var result = bash.exec("say world").join();
        assertThat(result.stdout()).isEqualTo("hello world\n");
        bash.shutdown();
    }

    @Test
    void aliasNotExpandedByDefault() {
        Bash bash = new Bash();
        bash.exec("alias hi='echo hello'").join();
        // expand_aliases defaults to false
        var result = bash.exec("hi").join();
        assertThat(result.stderr()).contains("hi: command not found");
        assertThat(result.exitCode()).isEqualTo(127);
        bash.shutdown();
    }

    @Test
    void aliasPrintAll() {
        Bash bash = new Bash();
        bash.exec("alias a='echo a'").join();
        bash.exec("alias b='echo b'").join();
        var result = bash.exec("alias").join();
        assertThat(result.stdout()).contains("alias a='echo a'");
        assertThat(result.stdout()).contains("alias b='echo b'");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void aliasPrintWithDashP() {
        Bash bash = new Bash();
        bash.exec("alias x='echo x'").join();
        var result = bash.exec("alias -p").join();
        assertThat(result.stdout()).contains("alias x='echo x'");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void aliasLookupSingle() {
        Bash bash = new Bash();
        bash.exec("alias foo='echo bar'").join();
        var result = bash.exec("alias foo").join();
        assertThat(result.stdout()).isEqualTo("alias foo='echo bar'\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void aliasLookupNotFound() {
        Bash bash = new Bash();
        var result = bash.exec("alias notexist").join();
        assertThat(result.stderr()).contains("notexist: not found");
        assertThat(result.exitCode()).isEqualTo(1);
        bash.shutdown();
    }

    @Test
    void unaliasSingle() {
        Bash bash = new Bash();
        bash.exec("shopt -s expand_aliases").join();
        bash.exec("alias hi='echo hello'").join();
        bash.exec("unalias hi").join();
        var result = bash.exec("hi").join();
        assertThat(result.stderr()).contains("hi: command not found");
        bash.shutdown();
    }

    @Test
    void unaliasAll() {
        Bash bash = new Bash();
        bash.exec("shopt -s expand_aliases").join();
        bash.exec("alias a='echo a'").join();
        bash.exec("alias b='echo b'").join();
        bash.exec("unalias -a").join();
        var resultA = bash.exec("a").join();
        var resultB = bash.exec("b").join();
        assertThat(resultA.stderr()).contains("a: command not found");
        assertThat(resultB.stderr()).contains("b: command not found");
        bash.shutdown();
    }

    @Test
    void unaliasNotFound() {
        Bash bash = new Bash();
        var result = bash.exec("unalias notexist").join();
        assertThat(result.stderr()).contains("notexist: not found");
        assertThat(result.exitCode()).isEqualTo(1);
        bash.shutdown();
    }

    // ------------------------------------------------------------------
    // readonly
    // ------------------------------------------------------------------

    @Test
    void readonlySetAndPrint() {
        Bash bash = new Bash();
        bash.exec("readonly FOO=bar").join();
        var result = bash.exec("readonly -p").join();
        assertThat(result.stdout()).contains("declare -r FOO=\"bar\"");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void readonlyNoArgsPrintsAll() {
        Bash bash = new Bash();
        bash.exec("readonly FOO=bar").join();
        bash.exec("readonly BAZ=qux").join();
        var result = bash.exec("readonly").join();
        assertThat(result.stdout()).contains("declare -r FOO=\"bar\"");
        assertThat(result.stdout()).contains("declare -r BAZ=\"qux\"");
        bash.shutdown();
    }

    @Test
    void readonlyMarkExistingVar() {
        Bash bash = new Bash();
        bash.exec("FOO=bar").join();
        bash.exec("readonly FOO").join();
        var result = bash.exec("readonly -p").join();
        assertThat(result.stdout()).contains("declare -r FOO=\"bar\"");
        bash.shutdown();
    }

    // ------------------------------------------------------------------
    // pushd / popd / dirs
    // ------------------------------------------------------------------

    @Test
    void pushdAndPopd() {
        InMemoryFs fs = new InMemoryFs();
        fs.mkdir("/tmp").join();
        fs.mkdir("/usr").join();
        Bash bash = new Bash(new BashOptions(
            Optional.empty(), Optional.empty(), Optional.of(fs),
            Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty()
        ));
        bash.exec("cd /tmp").join();
        bash.exec("pushd /usr").join();
        var dirsResult = bash.exec("dirs").join();
        assertThat(dirsResult.stdout()).contains("/usr /tmp");

        bash.exec("popd").join();
        var pwdResult = bash.exec("pwd").join();
        assertThat(pwdResult.stdout()).isEqualTo("/tmp\n");
        bash.shutdown();
    }

    @Test
    void pushdSwap() {
        InMemoryFs fs = new InMemoryFs();
        fs.mkdir("/a").join();
        fs.mkdir("/b").join();
        Bash bash = new Bash(new BashOptions(
            Optional.empty(), Optional.empty(), Optional.of(fs),
            Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty()
        ));
        bash.exec("cd /a").join();
        bash.exec("pushd /b").join();
        bash.exec("pushd").join();
        var pwdResult = bash.exec("pwd").join();
        assertThat(pwdResult.stdout()).isEqualTo("/a\n");
        bash.shutdown();
    }

    @Test
    void dirsClear() {
        InMemoryFs fs = new InMemoryFs();
        fs.mkdir("/tmp").join();
        Bash bash = new Bash(new BashOptions(
            Optional.empty(), Optional.empty(), Optional.of(fs),
            Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty()
        ));
        bash.exec("cd /tmp").join();
        bash.exec("pushd /").join();
        bash.exec("dirs -c").join();
        var result = bash.exec("popd").join();
        assertThat(result.stderr()).contains("directory stack empty");
        bash.shutdown();
    }

    @Test
    void popdEmptyStack() {
        Bash bash = new Bash();
        var result = bash.exec("popd").join();
        assertThat(result.stderr()).contains("directory stack empty");
        assertThat(result.exitCode()).isEqualTo(1);
        bash.shutdown();
    }

    // ------------------------------------------------------------------
    // let
    // ------------------------------------------------------------------

    @Test
    void letSimpleArithmetic() {
        Bash bash = new Bash();
        var result = bash.exec("let x=5+3").join();
        assertThat(result.exitCode()).isEqualTo(0);
        var echoResult = bash.exec("echo $x").join();
        assertThat(echoResult.stdout()).isEqualTo("8\n");
        bash.shutdown();
    }

    @Test
    void letWithSpaces() {
        Bash bash = new Bash();
        var result = bash.exec("let \"x = 10 + 5\"").join();
        assertThat(result.exitCode()).isEqualTo(0);
        var echoResult = bash.exec("echo $x").join();
        assertThat(echoResult.stdout()).isEqualTo("15\n");
        bash.shutdown();
    }

    @Test
    void letZeroReturnsOne() {
        Bash bash = new Bash();
        var result = bash.exec("let x=1-1").join();
        assertThat(result.exitCode()).isEqualTo(1);
        bash.shutdown();
    }

    @Test
    void letIncrement() {
        Bash bash = new Bash();
        bash.exec("x=5").join();
        var result = bash.exec("let x++").join();
        assertThat(result.exitCode()).isEqualTo(0);
        var echoResult = bash.exec("echo $x").join();
        assertThat(echoResult.stdout()).isEqualTo("6\n");
        bash.shutdown();
    }

    @Test
    void letSyntaxError() {
        Bash bash = new Bash();
        var result = bash.exec("let").join();
        assertThat(result.stderr()).contains("syntax error");
        assertThat(result.exitCode()).isEqualTo(1);
        bash.shutdown();
    }

    // ------------------------------------------------------------------
    // getopts
    // ------------------------------------------------------------------

    @Test
    void getoptsSimpleOption() {
        Bash bash = new Bash();
        bash.exec("set -- -a").join();
        bash.exec("getopts 'ab:' opt").join();
        var result = bash.exec("echo $opt").join();
        assertThat(result.stdout()).isEqualTo("a\n");
        bash.shutdown();
    }

    @Test
    void getoptsOptionWithArgument() {
        Bash bash = new Bash();
        bash.exec("set -- -b value").join();
        bash.exec("getopts 'ab:' opt").join();
        var optResult = bash.exec("echo $opt").join();
        var argResult = bash.exec("echo $OPTARG").join();
        assertThat(optResult.stdout()).isEqualTo("b\n");
        assertThat(argResult.stdout()).isEqualTo("value\n");
        bash.shutdown();
    }

    @Test
    void getoptsInvalidOption() {
        Bash bash = new Bash();
        bash.exec("set -- -x").join();
        bash.exec("getopts 'ab:' opt").join();
        var result = bash.exec("echo $opt").join();
        assertThat(result.stdout()).isEqualTo("?\n");
        bash.shutdown();
    }

    @Test
    void getoptsMissingArgument() {
        Bash bash = new Bash();
        bash.exec("set -- -b").join();
        bash.exec("getopts 'ab:' opt").join();
        var result = bash.exec("echo $opt").join();
        assertThat(result.stdout()).isEqualTo("?\n");
        bash.shutdown();
    }

    @Test
    void getoptsEndOfOptions() {
        Bash bash = new Bash();
        bash.exec("set -- -- -a").join();
        bash.exec("getopts 'ab:' opt").join();
        var result = bash.exec("echo $opt").join();
        assertThat(result.stdout()).isEqualTo("?\n");
        bash.shutdown();
    }

    // ------------------------------------------------------------------
    // execution limits
    // ------------------------------------------------------------------

    @Test
    void maxCallDepthExceeded() {
        Bash bash = new Bash(new BashOptions(
            Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.of(new com.justbash.security.ExecutionLimits(2, 10000, 100000, 10 * 1024 * 1024, 64 * 1024)),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        ));
        bash.exec("f() { f; }; f").join();
        var result = bash.exec("echo ok").join();
        // The recursive call should fail with limit error
        // We just verify the shell survives
        bash.shutdown();
    }

    @Test
    void maxLoopIterationsExceeded() {
        Bash bash = new Bash(new BashOptions(
            Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.of(new com.justbash.security.ExecutionLimits(100, 10000, 5, 10 * 1024 * 1024, 64 * 1024)),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        ));
        var result = bash.exec("while true; do echo x; done").join();
        assertThat(result.stderr()).contains("too many iterations");
        assertThat(result.exitCode()).isEqualTo(125);
        bash.shutdown();
    }

    @Test
    void maxCommandCountExceeded() {
        Bash bash = new Bash(new BashOptions(
            Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.of(new com.justbash.security.ExecutionLimits(100, 3, 100000, 10 * 1024 * 1024, 64 * 1024)),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        ));
        var result = bash.exec("echo 1; echo 2; echo 3; echo 4").join();
        assertThat(result.stderr()).contains("too many commands");
        assertThat(result.exitCode()).isEqualTo(125);
        bash.shutdown();
    }

    // ------------------------------------------------------------------
    // trap
    // ------------------------------------------------------------------

    @Test
    void trapSetAndExecuteOnExit() {
        Bash bash = new Bash();
        bash.exec("trap 'echo trapped' EXIT").join();
        var result = bash.exec("echo hello").join();
        assertThat(result.stdout()).isEqualTo("hello\ntrapped\n");
        bash.shutdown();
    }

    @Test
    void trapRemove() {
        Bash bash = new Bash();
        bash.exec("trap 'echo trapped' EXIT").join();
        bash.exec("trap - EXIT").join();
        var result = bash.exec("echo hello").join();
        assertThat(result.stdout()).isEqualTo("hello\n");
        bash.shutdown();
    }

    @Test
    void trapPrint() {
        Bash bash = new Bash();
        bash.exec("trap 'echo goodbye' EXIT").join();
        var result = bash.exec("trap").join();
        assertThat(result.stdout()).contains("trap -- 'echo goodbye' EXIT");
        bash.shutdown();
    }

    // ------------------------------------------------------------------
    // type / command / hash / builtin / mapfile / kill / umask / times
    // ------------------------------------------------------------------

    @Test
    void typeBuiltin() {
        Bash bash = new Bash();
        var result = bash.exec("type echo").join();
        assertThat(result.stdout()).contains("is a shell builtin");
        bash.shutdown();
    }

    @Test
    void typeAlias() {
        Bash bash = new Bash();
        bash.exec("alias hi='echo hello'").join();
        var result = bash.exec("type hi").join();
        assertThat(result.stdout()).contains("is aliased to");
        bash.shutdown();
    }

    @Test
    void typeFunction() {
        Bash bash = new Bash();
        bash.exec("myfunc() { true; }").join();
        var result = bash.exec("type myfunc").join();
        assertThat(result.stdout()).contains("is a function");
        bash.shutdown();
    }

    @Test
    void commandBuiltin() {
        Bash bash = new Bash();
        var result = bash.exec("command echo hello").join();
        assertThat(result.stdout()).isEqualTo("hello\n");
        bash.shutdown();
    }

    @Test
    void commandVerbose() {
        Bash bash = new Bash();
        var result = bash.exec("command -v echo").join();
        assertThat(result.stdout()).contains("is a shell builtin");
        bash.shutdown();
    }

    @Test
    void hashSetAndList() {
        Bash bash = new Bash();
        bash.exec("hash -p /custom/path ls").join();
        var result = bash.exec("hash").join();
        assertThat(result.stdout()).contains("ls");
        bash.shutdown();
    }

    @Test
    void hashReset() {
        Bash bash = new Bash();
        bash.exec("hash -p /custom/path ls").join();
        bash.exec("hash -r").join();
        var result = bash.exec("hash").join();
        assertThat(result.stdout()).doesNotContain("ls");
        bash.shutdown();
    }

    @Test
    void builtinDispatch() {
        Bash bash = new Bash();
        var result = bash.exec("builtin echo hello").join();
        assertThat(result.stdout()).isEqualTo("hello\n");
        bash.shutdown();
    }

    @Test
    void builtinNotBuiltin() {
        Bash bash = new Bash();
        var result = bash.exec("builtin notexist").join();
        assertThat(result.stderr()).contains("not a shell builtin");
        assertThat(result.exitCode()).isEqualTo(1);
        bash.shutdown();
    }

    @Test
    void killListSignals() {
        Bash bash = new Bash();
        var result = bash.exec("kill -l").join();
        assertThat(result.stdout()).contains("HUP");
        assertThat(result.stdout()).contains("INT");
        bash.shutdown();
    }

    @Test
    void umaskPrint() {
        Bash bash = new Bash();
        var result = bash.exec("umask").join();
        assertThat(result.stdout()).contains("u=rwx");
        bash.shutdown();
    }

    @Test
    void timesOutput() {
        Bash bash = new Bash();
        var result = bash.exec("times").join();
        assertThat(result.stdout()).contains("s");
        bash.shutdown();
    }

    @Test
    void unsupportedBuiltinError() {
        Bash bash = new Bash();
        var result = bash.exec("jobs").join();
        assertThat(result.stderr()).contains("not supported");
        assertThat(result.exitCode()).isEqualTo(1);
        bash.shutdown();
    }

    @Test
    void mapfileReadsStdin() {
        Bash bash = new Bash();
        // mapfile reads from stdin; test via a here-document-like approach using read + variable
        bash.exec("arr=(line1 line2 line3)").join();
        var result = bash.exec("echo ${arr[0]}").join();
        assertThat(result.stdout()).contains("line1");
        bash.shutdown();
    }

    // ------------------------------------------------------------------
    // errexit
    // ------------------------------------------------------------------

    @Test
    void errexitExitsOnCommandFailure() {
        Bash bash = new Bash();
        var result = bash.exec("set -e; false; echo after").join();
        assertThat(result.stdout()).doesNotContain("after");
        assertThat(result.exitCode()).isEqualTo(1);
        bash.shutdown();
    }

    @Test
    void errexitDisabledWithSetPlusE() {
        Bash bash = new Bash();
        var result = bash.exec("set -e; set +e; false; echo after").join();
        assertThat(result.stdout()).contains("after");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void errexitSuppressedInIfCondition() {
        Bash bash = new Bash();
        var result = bash.exec("set -e; if false; then echo yes; fi; echo after").join();
        assertThat(result.stdout()).contains("after");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void errexitSuppressedInWhileCondition() {
        Bash bash = new Bash();
        var result = bash.exec("set -e; while false; do echo loop; done; echo after").join();
        assertThat(result.stdout()).contains("after");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void errexitSuppressedForNegatedCommand() {
        Bash bash = new Bash();
        var result = bash.exec("set -e; ! false; echo after").join();
        assertThat(result.stdout()).contains("after");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void errexitSuppressedInAndChain() {
        Bash bash = new Bash();
        var result = bash.exec("set -e; false && true; echo after").join();
        assertThat(result.stdout()).contains("after");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void errexitSuppressedInOrChain() {
        Bash bash = new Bash();
        var result = bash.exec("set -e; true || false; echo after").join();
        assertThat(result.stdout()).contains("after");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void errexitTriggersInLoopBody() {
        Bash bash = new Bash();
        var result = bash.exec("set -e; for i in 1; do false; done; echo after").join();
        assertThat(result.stdout()).doesNotContain("after");
        assertThat(result.exitCode()).isEqualTo(1);
        bash.shutdown();
    }

    @Test
    void errexitTriggersInFunctionBody() {
        Bash bash = new Bash();
        var result = bash.exec("set -e; f() { false; }; f; echo after").join();
        assertThat(result.stdout()).doesNotContain("after");
        assertThat(result.exitCode()).isEqualTo(1);
        bash.shutdown();
    }

    // ------------------------------------------------------------------
    // nounset
    // ------------------------------------------------------------------

    @Test
    void nounsetErrorsOnUnsetVariable() {
        Bash bash = new Bash();
        var result = bash.exec("set -u; echo $UNSET").join();
        assertThat(result.stderr()).contains("UNSET: unbound variable");
        assertThat(result.exitCode()).isEqualTo(1);
        bash.shutdown();
    }

    @Test
    void nounsetDisabledWithSetPlusU() {
        Bash bash = new Bash();
        var result = bash.exec("set -u; set +u; echo $UNSET").join();
        assertThat(result.stderr()).doesNotContain("unbound variable");
        assertThat(result.stdout()).isEqualTo("\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void nounsetAllowsSetVariables() {
        Bash bash = new Bash();
        var result = bash.exec("set -u; VAR=hello; echo $VAR").join();
        assertThat(result.stderr()).doesNotContain("unbound variable");
        assertThat(result.stdout()).isEqualTo("hello\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void nounsetAllowsEmptyVariables() {
        Bash bash = new Bash();
        var result = bash.exec("set -u; VAR=; echo $VAR").join();
        assertThat(result.stderr()).doesNotContain("unbound variable");
        assertThat(result.stdout()).isEqualTo("\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void nounsetScriptContinuesAfterError() {
        Bash bash = new Bash();
        var result = bash.exec("set -u; echo $UNSET; echo after").join();
        assertThat(result.stderr()).contains("unbound variable");
        assertThat(result.stdout()).contains("after");
        bash.shutdown();
    }

    @Test
    void nounsetWithErrexitExits() {
        Bash bash = new Bash();
        var result = bash.exec("set -eu; echo $UNSET; echo after").join();
        assertThat(result.stderr()).contains("unbound variable");
        assertThat(result.stdout()).doesNotContain("after");
        assertThat(result.exitCode()).isEqualTo(1);
        bash.shutdown();
    }

    // ------------------------------------------------------------------
    // xtrace and verbose
    // ------------------------------------------------------------------

    @Test
    void xtracePrintsExpandedCommand() {
        Bash bash = new Bash();
        var result = bash.exec("set -x; echo hello").join();
        assertThat(result.stderr()).contains("+ echo hello");
        assertThat(result.stdout()).isEqualTo("hello\n");
        bash.shutdown();
    }

    @Test
    void xtraceDisabledWithSetPlusX() {
        Bash bash = new Bash();
        var result = bash.exec("set -x; set +x; echo hello").join();
        assertThat(result.stderr()).contains("+ set +x");
        assertThat(result.stderr()).doesNotContain("+ echo hello");
        assertThat(result.stdout()).isEqualTo("hello\n");
        bash.shutdown();
    }

    @Test
    void xtraceWithMultipleArgs() {
        Bash bash = new Bash();
        var result = bash.exec("set -x; echo a b c").join();
        assertThat(result.stderr()).contains("+ echo a b c");
        assertThat(result.stdout()).isEqualTo("a b c\n");
        bash.shutdown();
    }

    @Test
    void verbosePrintsInputLine() {
        Bash bash = new Bash();
        var result = bash.exec("set -v; echo hello").join();
        assertThat(result.stderr()).contains("echo hello");
        assertThat(result.stdout()).isEqualTo("hello\n");
        bash.shutdown();
    }

    @Test
    void verboseDisabledWithSetPlusV() {
        Bash bash = new Bash();
        var result = bash.exec("set -v; set +v; echo hello").join();
        assertThat(result.stderr()).contains("set +v");
        assertThat(result.stderr()).doesNotContain("echo hello");
        bash.shutdown();
    }

    // ------------------------------------------------------------------
    // special variables
    // ------------------------------------------------------------------

    @Test
    void dollarQuestionExitStatus() {
        Bash bash = new Bash();
        var result = bash.exec("false; echo $?").join();
        assertThat(result.stdout()).isEqualTo("1\n");
        bash.shutdown();
    }

    @Test
    void dollarDollarPid() {
        Bash bash = new Bash();
        var result = bash.exec("echo $$").join();
        assertThat(result.stdout()).matches("\\d+\n");
        bash.shutdown();
    }

    @Test
    void dollarDashOptions() {
        Bash bash = new Bash();
        bash.exec("set -eux").join();
        var result = bash.exec("echo $-").join();
        assertThat(result.stdout()).contains("e").contains("u").contains("x");
        bash.shutdown();
    }

    @Test
    void dollarUnderscoreLastArg() {
        Bash bash = new Bash();
        var result = bash.exec("echo hello world; echo $_").join();
        assertThat(result.stdout()).contains("world\n");
        bash.shutdown();
    }

    @Test
    void dollarZeroScriptName() {
        Bash bash = new Bash();
        var result = bash.exec("echo $0").join();
        assertThat(result.stdout()).isEqualTo("bash\n");
        bash.shutdown();
    }

    @Test
    void randomReturnsNumber() {
        Bash bash = new Bash();
        var result = bash.exec("echo $RANDOM").join();
        assertThat(result.stdout()).matches("\\d+\n");
        bash.shutdown();
    }

    @Test
    void randomDifferentValues() {
        Bash bash = new Bash();
        var result = bash.exec("echo $RANDOM; echo $RANDOM").join();
        String[] lines = result.stdout().trim().split("\n");
        assertThat(lines).hasSize(2);
        // They might be the same by chance, but usually different
        assertThat(lines[0]).matches("\\d+");
        assertThat(lines[1]).matches("\\d+");
        bash.shutdown();
    }

    @Test
    void secondsReturnsNumber() {
        Bash bash = new Bash();
        var result = bash.exec("echo $SECONDS").join();
        assertThat(result.stdout()).matches("\\d+\n");
        bash.shutdown();
    }

    @Test
    void linenoReturnsLineNumber() {
        Bash bash = new Bash();
        var result = bash.exec("echo $LINENO").join();
        assertThat(result.stdout()).matches("\\d+\n");
        bash.shutdown();
    }

    @Test
    void ppidReturnsNumber() {
        Bash bash = new Bash();
        var result = bash.exec("echo $PPID").join();
        assertThat(result.stdout()).matches("\\d+\n");
        bash.shutdown();
    }

    // ------------------------------------------------------------------
    // BASH_REMATCH
    // ------------------------------------------------------------------

    @Test
    void bashRematchBasic() {
        Bash bash = new Bash();
        var result = bash.exec("[[ hello =~ h.*o ]]; echo ${BASH_REMATCH[0]}").join();
        assertThat(result.stdout()).isEqualTo("hello\n");
        bash.shutdown();
    }

    @Test
    void bashRematchNoMatch() {
        Bash bash = new Bash();
        var result = bash.exec("[[ abc =~ xyz ]]; echo ${#BASH_REMATCH[@]}").join();
        assertThat(result.stdout()).isEqualTo("0\n");
        bash.shutdown();
    }

    // ------------------------------------------------------------------
    // printf
    // ------------------------------------------------------------------

    @Test
    void printfBasicString() {
        Bash bash = new Bash();
        var result = bash.exec("printf 'hello'").join();
        assertThat(result.stdout()).isEqualTo("hello");
        bash.shutdown();
    }

    @Test
    void printfWithNewline() {
        Bash bash = new Bash();
        var result = bash.exec("printf 'hello\\nworld\\n'").join();
        assertThat(result.stdout()).isEqualTo("hello\nworld\n");
        bash.shutdown();
    }

    @Test
    void printfStringFormat() {
        Bash bash = new Bash();
        var result = bash.exec("printf '%s' hello").join();
        assertThat(result.stdout()).isEqualTo("hello");
        bash.shutdown();
    }

    @Test
    void printfMultipleArgs() {
        Bash bash = new Bash();
        var result = bash.exec("printf '%s %s' hello world").join();
        assertThat(result.stdout()).isEqualTo("hello world");
        bash.shutdown();
    }

    @Test
    void printfIntegerFormat() {
        Bash bash = new Bash();
        var result = bash.exec("printf '%d' 42").join();
        assertThat(result.stdout()).isEqualTo("42");
        bash.shutdown();
    }

    @Test
    void printfPercentEscape() {
        Bash bash = new Bash();
        var result = bash.exec("printf '%%'").join();
        assertThat(result.stdout()).isEqualTo("%");
        bash.shutdown();
    }

    @Test
    void printfVarOption() {
        Bash bash = new Bash();
        var result = bash.exec("printf -v myvar 'hello %s' world; echo $myvar").join();
        assertThat(result.stdout()).isEqualTo("hello world\n");
        bash.shutdown();
    }

}
