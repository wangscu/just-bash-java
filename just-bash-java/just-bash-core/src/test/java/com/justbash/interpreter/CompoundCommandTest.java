package com.justbash.interpreter;

import com.justbash.Bash;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CompoundCommandTest {

    @Test
    void ifTrueThenEcho() {
        Bash bash = new Bash();
        var result = bash.exec("if true; then echo yes; fi").join();
        assertThat(result.stdout()).isEqualTo("yes\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void ifFalseThenNoEcho() {
        Bash bash = new Bash();
        var result = bash.exec("if false; then echo yes; fi").join();
        assertThat(result.stdout()).isEmpty();
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void ifElse() {
        Bash bash = new Bash();
        var result = bash.exec("if false; then echo yes; else echo no; fi").join();
        assertThat(result.stdout()).isEqualTo("no\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void ifElif() {
        Bash bash = new Bash();
        var result = bash.exec("if false; then echo a; elif true; then echo b; fi").join();
        assertThat(result.stdout()).isEqualTo("b\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void ifElifElse() {
        Bash bash = new Bash();
        var result = bash.exec("if false; then echo a; elif false; then echo b; else echo c; fi").join();
        assertThat(result.stdout()).isEqualTo("c\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void nestedIf() {
        Bash bash = new Bash();
        var result = bash.exec("if true; then if true; then echo nested; fi; fi").join();
        assertThat(result.stdout()).isEqualTo("nested\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void ifWithNewlines() {
        Bash bash = new Bash();
        var result = bash.exec("if true\nthen\necho yes\nfi").join();
        assertThat(result.stdout()).isEqualTo("yes\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void forLoop() {
        Bash bash = new Bash();
        var result = bash.exec("for i in a b c; do echo $i; done").join();
        assertThat(result.stdout()).isEqualTo("a\nb\nc\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void forLoopWithVariableExpansion() {
        Bash bash = new Bash();
        var result = bash.exec("for i in hello world; do echo $i; done").join();
        assertThat(result.stdout()).isEqualTo("hello\nworld\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void forLoopDefaultNoWords() {
        Bash bash = new Bash();
        var result = bash.exec("for i; do echo $i; done").join();
        // No positional params set, so empty iteration
        assertThat(result.stdout()).isEmpty();
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void whileLoopWithFalse() {
        Bash bash = new Bash();
        var result = bash.exec("while false; do echo loop; done").join();
        assertThat(result.stdout()).isEmpty();
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void untilLoop() {
        Bash bash = new Bash();
        var result = bash.exec("until true; do echo loop; done").join();
        assertThat(result.stdout()).isEmpty();
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void groupCommand() {
        Bash bash = new Bash();
        var result = bash.exec("{ echo a; echo b; }").join();
        assertThat(result.stdout()).isEqualTo("a\nb\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void forLoopBreak() {
        Bash bash = new Bash();
        var result = bash.exec("for i in a b c; do echo $i; break; done").join();
        assertThat(result.stdout()).isEqualTo("a\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void forLoopContinue() {
        Bash bash = new Bash();
        var result = bash.exec("for i in 1 2 3; do echo before; continue; echo after; done").join();
        assertThat(result.stdout()).isEqualTo("before\nbefore\nbefore\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void breakOutsideLoopError() {
        Bash bash = new Bash();
        var result = bash.exec("break").join();
        assertThat(result.stderr()).contains("only meaningful in a loop");
        bash.shutdown();
    }

    @Test
    void continueOutsideLoopError() {
        Bash bash = new Bash();
        var result = bash.exec("continue").join();
        assertThat(result.stderr()).contains("only meaningful in a loop");
        bash.shutdown();
    }

    @Test
    void ifWithLogicalOperatorAfter() {
        Bash bash = new Bash();
        var result = bash.exec("if true; then echo a; fi && echo b").join();
        assertThat(result.stdout()).isEqualTo("a\nb\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void ifWithMultipleConditionStatements() {
        Bash bash = new Bash();
        var result = bash.exec("if false; true; then echo ok; fi").join();
        // Last exit code (true = 0) determines truthiness
        assertThat(result.stdout()).isEqualTo("ok\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void caseMatchFirst() {
        Bash bash = new Bash();
        var result = bash.exec("case a in a) echo one;; b) echo two;; esac").join();
        assertThat(result.stdout()).isEqualTo("one\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void caseMatchSecond() {
        Bash bash = new Bash();
        var result = bash.exec("case b in a) echo one;; b) echo two;; esac").join();
        assertThat(result.stdout()).isEqualTo("two\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void caseDefault() {
        Bash bash = new Bash();
        var result = bash.exec("case z in a) echo one;; *) echo default;; esac").join();
        assertThat(result.stdout()).isEqualTo("default\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void caseMultiplePatterns() {
        Bash bash = new Bash();
        var result = bash.exec("case b in a|b) echo match;; *) echo no;; esac").join();
        assertThat(result.stdout()).isEqualTo("match\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void caseWithVariable() {
        Bash bash = new Bash();
        var result = bash.exec("x=hello; case $x in hello) echo yes;; *) echo no;; esac").join();
        assertThat(result.stdout()).isEqualTo("yes\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void caseGlobStar() {
        Bash bash = new Bash();
        var result = bash.exec("case abc in *) echo match;; esac").join();
        assertThat(result.stdout()).isEqualTo("match\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void caseGlobQuestion() {
        Bash bash = new Bash();
        var result = bash.exec("case a in ?) echo single;; *) echo multi;; esac").join();
        assertThat(result.stdout()).isEqualTo("single\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void caseNoMatch() {
        Bash bash = new Bash();
        var result = bash.exec("case x in a) echo one;; esac").join();
        assertThat(result.stdout()).isEmpty();
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void caseEmptyBody() {
        Bash bash = new Bash();
        var result = bash.exec("case a in a);; esac").join();
        assertThat(result.stdout()).isEmpty();
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void functionDefAndCall() {
        Bash bash = new Bash();
        var result = bash.exec("function greet() { echo hello; }; greet").join();
        assertThat(result.stdout()).isEqualTo("hello\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void functionDefWithoutKeyword() {
        Bash bash = new Bash();
        var result = bash.exec("foo() { echo bar; }; foo").join();
        assertThat(result.stdout()).isEqualTo("bar\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void functionWithArgs() {
        Bash bash = new Bash();
        var result = bash.exec("f() { echo $1 $2; }; f hello world").join();
        assertThat(result.stdout()).isEqualTo("hello world\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void functionReturn() {
        Bash bash = new Bash();
        var result = bash.exec("f() { return 42; }; f").join();
        assertThat(result.stdout()).isEmpty();
        assertThat(result.exitCode()).isEqualTo(42);
        bash.shutdown();
    }

    @Test
    void functionReturnWithOutput() {
        Bash bash = new Bash();
        var result = bash.exec("f() { echo hi; return 1; }; f").join();
        assertThat(result.stdout()).isEqualTo("hi\n");
        assertThat(result.exitCode()).isEqualTo(1);
        bash.shutdown();
    }

    @Test
    void functionLocalVariable() {
        Bash bash = new Bash();
        var result = bash.exec("x=global; f() { local x=local; echo $x; }; f; echo $x").join();
        assertThat(result.stdout()).isEqualTo("local\nglobal\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void functionPositionalParamsRestored() {
        Bash bash = new Bash();
        var result = bash.exec("f() { echo $1; }; f x; echo $1").join();
        assertThat(result.stdout()).isEqualTo("x\n\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void functionReturnsLastExitCodeByDefault() {
        Bash bash = new Bash();
        var result = bash.exec("f() { false; return; }; f").join();
        assertThat(result.exitCode()).isEqualTo(1);
        bash.shutdown();
    }

    @Test
    void returnOutsideFunctionError() {
        Bash bash = new Bash();
        var result = bash.exec("return").join();
        assertThat(result.stderr()).contains("can only `return' from a function");
        bash.shutdown();
    }

    @Test
    void functionCallsBuiltin() {
        Bash bash = new Bash();
        var result = bash.exec("f() { echo $1; }; f test").join();
        assertThat(result.stdout()).isEqualTo("test\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void shiftDefault() {
        Bash bash = new Bash();
        var result = bash.exec("f() { shift; echo $1; }; f a b c").join();
        assertThat(result.stdout()).isEqualTo("b\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void shiftByTwo() {
        Bash bash = new Bash();
        var result = bash.exec("f() { shift 2; echo $1; }; f x y z").join();
        assertThat(result.stdout()).isEqualTo("z\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void shiftUpdatesCount() {
        Bash bash = new Bash();
        var result = bash.exec("f() { shift 2; echo $#; }; f a b c d").join();
        assertThat(result.stdout()).isEqualTo("2\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void shiftOutOfRange() {
        Bash bash = new Bash();
        var result = bash.exec("f() { shift 5; }; f a").join();
        assertThat(result.stderr()).contains("shift count out of range");
        assertThat(result.exitCode()).isEqualTo(1);
        bash.shutdown();
    }

    @Test
    void shiftZero() {
        Bash bash = new Bash();
        var result = bash.exec("f() { shift 0; echo $1; }; f a b").join();
        assertThat(result.stdout()).isEqualTo("a\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // test / [ builtin tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void testTrue() {
        Bash bash = new Bash();
        var result = bash.exec("test true").join();
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void testFalseEmptyString() {
        Bash bash = new Bash();
        var result = bash.exec("test ''").join();
        assertThat(result.exitCode()).isEqualTo(1);
        bash.shutdown();
    }

    @Test
    void testNot() {
        Bash bash = new Bash();
        var result = bash.exec("test ! ''").join();
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void testStringEqual() {
        Bash bash = new Bash();
        var result = bash.exec("test 'hello' = 'hello'").join();
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void testStringNotEqual() {
        Bash bash = new Bash();
        var result = bash.exec("test 'hello' != 'world'").join();
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void testStringEmpty() {
        Bash bash = new Bash();
        var result = bash.exec("test -z ''").join();
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void testStringNonEmpty() {
        Bash bash = new Bash();
        var result = bash.exec("test -n 'hello'").join();
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void testNumericEqual() {
        Bash bash = new Bash();
        var result = bash.exec("test 5 -eq 5").join();
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void testNumericNotEqual() {
        Bash bash = new Bash();
        var result = bash.exec("test 5 -ne 3").join();
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void testNumericLessThan() {
        Bash bash = new Bash();
        var result = bash.exec("test 3 -lt 5").join();
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void testNumericGreaterThan() {
        Bash bash = new Bash();
        var result = bash.exec("test 5 -gt 3").join();
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void testNumericLessEqual() {
        Bash bash = new Bash();
        var result = bash.exec("test 5 -le 5").join();
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void testNumericGreaterEqual() {
        Bash bash = new Bash();
        var result = bash.exec("test 5 -ge 3").join();
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void testAndOperator() {
        Bash bash = new Bash();
        var result = bash.exec("test 'a' = 'a' -a 'b' = 'b'").join();
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void testAndOperatorFalse() {
        Bash bash = new Bash();
        var result = bash.exec("test 'a' = 'a' -a 'b' = 'c'").join();
        assertThat(result.exitCode()).isEqualTo(1);
        bash.shutdown();
    }

    @Test
    void testOrOperator() {
        Bash bash = new Bash();
        var result = bash.exec("test 'a' = 'b' -o 'c' = 'c'").join();
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void testBracketForm() {
        Bash bash = new Bash();
        var result = bash.exec("[ 'hello' = 'hello' ]").join();
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void testBracketMissingClose() {
        Bash bash = new Bash();
        var result = bash.exec("[ 'hello' = 'hello'").join();
        assertThat(result.stderr()).contains("missing `]'");
        assertThat(result.exitCode()).isEqualTo(2);
        bash.shutdown();
    }

    @Test
    void testFileNotExists() {
        Bash bash = new Bash();
        var result = bash.exec("test -e /tmp/nonexistent").join();
        assertThat(result.exitCode()).isEqualTo(1);
        bash.shutdown();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Arithmetic expansion and command tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void arithmeticExpansionBasic() {
        Bash bash = new Bash();
        var result = bash.exec("echo $((1 + 2))").join();
        assertThat(result.stdout()).isEqualTo("3\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void arithmeticExpansionVariable() {
        Bash bash = new Bash();
        var result = bash.exec("x=5; echo $((x + 3))").join();
        assertThat(result.stdout()).isEqualTo("8\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void arithmeticExpansionMultiplication() {
        Bash bash = new Bash();
        var result = bash.exec("echo $((3 * 4))").join();
        assertThat(result.stdout()).isEqualTo("12\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void arithmeticExpansionComparison() {
        Bash bash = new Bash();
        var result = bash.exec("echo $((5 > 3))").join();
        assertThat(result.stdout()).isEqualTo("1\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void arithmeticExpansionTernary() {
        Bash bash = new Bash();
        var result = bash.exec("echo $((1 ? 10 : 20))").join();
        assertThat(result.stdout()).isEqualTo("10\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void arithmeticExpansionAssignment() {
        Bash bash = new Bash();
        var result = bash.exec("echo $((x = 7))").join();
        assertThat(result.stdout()).isEqualTo("7\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void arithmeticExpansionNoSpaces() {
        Bash bash = new Bash();
        var result = bash.exec("echo $((1+2*3))").join();
        assertThat(result.stdout()).isEqualTo("7\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void arithmeticCommandTrue() {
        Bash bash = new Bash();
        var result = bash.exec("(( 1 + 1 ))").join();
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void arithmeticCommandFalse() {
        Bash bash = new Bash();
        var result = bash.exec("(( 0 ))").join();
        assertThat(result.exitCode()).isEqualTo(1);
        bash.shutdown();
    }

    @Test
    void arithmeticCommandSetsVariable() {
        Bash bash = new Bash();
        var result = bash.exec("(( x = 42 )); echo $x").join();
        assertThat(result.stdout()).isEqualTo("42\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void arithmeticCommandInIf() {
        Bash bash = new Bash();
        var result = bash.exec("if (( 5 > 3 )); then echo yes; fi").join();
        assertThat(result.stdout()).isEqualTo("yes\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void arithmeticCommandWithParens() {
        Bash bash = new Bash();
        var result = bash.exec("(( (1 + 2) * 3 )); echo $?").join();
        assertThat(result.stdout()).isEqualTo("0\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void arithmeticExpansionWithNestedParens() {
        Bash bash = new Bash();
        var result = bash.exec("echo $(( (2 + 3) * 4 ))").join();
        assertThat(result.stdout()).isEqualTo("20\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Command substitution tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void commandSubstitutionBasic() {
        Bash bash = new Bash();
        var result = bash.exec("echo $(echo hello)").join();
        assertThat(result.stdout()).isEqualTo("hello\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void commandSubstitutionMultipleWords() {
        Bash bash = new Bash();
        var result = bash.exec("echo $(echo hello world)").join();
        assertThat(result.stdout()).isEqualTo("hello world\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void commandSubstitutionStripsTrailingNewlines() {
        Bash bash = new Bash();
        var result = bash.exec("echo $(echo hello)world").join();
        assertThat(result.stdout()).isEqualTo("helloworld\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void commandSubstitutionInVariable() {
        Bash bash = new Bash();
        var result = bash.exec("x=$(echo 42); echo $x").join();
        assertThat(result.stdout()).isEqualTo("42\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void commandSubstitutionNested() {
        Bash bash = new Bash();
        var result = bash.exec("echo $(echo $(echo hello))").join();
        assertThat(result.stdout()).isEqualTo("hello\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void commandSubstitutionWithArithmetic() {
        Bash bash = new Bash();
        var result = bash.exec("echo $(echo $((1 + 2)))").join();
        assertThat(result.stdout()).isEqualTo("3\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // I/O redirection tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void outputRedirectTruncate() {
        Bash bash = new Bash();
        bash.exec("echo hello > /tmp/out.txt").join();
        assertThat(bash.readFile("/tmp/out.txt").join()).isEqualTo("hello\n");
        bash.shutdown();
    }

    @Test
    void outputRedirectOverwrite() {
        Bash bash = new Bash();
        bash.exec("echo first > /tmp/over.txt; echo second > /tmp/over.txt").join();
        assertThat(bash.readFile("/tmp/over.txt").join()).isEqualTo("second\n");
        bash.shutdown();
    }

    @Test
    void outputRedirectAppend() {
        Bash bash = new Bash();
        bash.exec("echo first >> /tmp/app.txt; echo second >> /tmp/app.txt").join();
        assertThat(bash.readFile("/tmp/app.txt").join()).isEqualTo("first\nsecond\n");
        bash.shutdown();
    }

    @Test
    void inputRedirect() {
        Bash bash = new Bash();
        bash.exec("echo hello world > /tmp/in.txt").join();
        var result = bash.exec("read line < /tmp/in.txt; echo $line").join();
        assertThat(result.stdout()).isEqualTo("hello world\n");
        bash.shutdown();
    }

    @Test
    void stderrRedirect() {
        Bash bash = new Bash();
        bash.exec("nonexistent_command 2> /tmp/err.txt").join();
        assertThat(bash.readFile("/tmp/err.txt").join()).contains("command not found");
        bash.shutdown();
    }

    @Test
    void redirectToDevNull() {
        Bash bash = new Bash();
        var result = bash.exec("echo hello > /dev/null").join();
        assertThat(result.stdout()).isEmpty();
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Pipeline tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void pipelineStdoutIsLastCommandOnly() {
        Bash bash = new Bash();
        var result = bash.exec("echo a | echo b").join();
        assertThat(result.stdout()).isEqualTo("b\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void pipelineWithCat() {
        Bash bash = new Bash();
        bash.exec("echo hello > /tmp/pipe.txt").join();
        var result = bash.exec("cat /tmp/pipe.txt | wc -l").join();
        // wc is not implemented, so this returns 127
        assertThat(result.exitCode()).isEqualTo(127);
        bash.shutdown();
    }

    @Test
    void pipelinePipeStatus() {
        Bash bash = new Bash();
        var result = bash.exec("true | false | true; echo $PIPESTATUS").join();
        assertThat(result.stdout()).isEqualTo("(0 1 0)\n");
        bash.shutdown();
    }

    @Test
    void pipelinePipefailOff() {
        Bash bash = new Bash();
        // Without pipefail: exit code is last command's (true = 0)
        var result = bash.exec("false | true; echo $?").join();
        assertThat(result.stdout()).isEqualTo("0\n");
        bash.shutdown();
    }

    @Test
    void pipelinePipefailOn() {
        Bash bash = new Bash();
        bash.exec("set -o pipefail").join();
        // With pipefail: exit code is rightmost non-zero (false = 1)
        var result = bash.exec("false | true; echo $?").join();
        assertThat(result.stdout()).isEqualTo("1\n");
        bash.shutdown();
    }

    @Test
    void pipelinePipefailRightmostNonZero() {
        Bash bash = new Bash();
        bash.exec("set -o pipefail").join();
        var result = bash.exec("false | true | false | true; echo $?").join();
        // rightmost non-zero is 1 (from the third command)
        assertThat(result.stdout()).isEqualTo("1\n");
        bash.shutdown();
    }

    @Test
    void pipelineNegated() {
        Bash bash = new Bash();
        var result = bash.exec("! true | true; echo $?").join();
        assertThat(result.stdout()).isEqualTo("1\n");
        bash.shutdown();
    }

    @Test
    void pipelineStderrAccumulated() {
        Bash bash = new Bash();
        var result = bash.exec("nonexistent1 | nonexistent2").join();
        assertThat(result.stderr()).contains("nonexistent1");
        assertThat(result.stderr()).contains("nonexistent2");
        assertThat(result.exitCode()).isEqualTo(127);
        bash.shutdown();
    }

    @Test
    void pipelineWithInputRedirection() {
        Bash bash = new Bash();
        bash.exec("echo hello world > /tmp/pipein.txt").join();
        var result = bash.exec("read line < /tmp/pipein.txt; echo $line").join();
        assertThat(result.stdout()).isEqualTo("hello world\n");
        bash.shutdown();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // set builtin tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void setPositionalParams() {
        Bash bash = new Bash();
        var result = bash.exec("set -- a b c; echo $1 $2 $3").join();
        assertThat(result.stdout()).isEqualTo("a b c\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void setPositionalParamsWithDash() {
        Bash bash = new Bash();
        var result = bash.exec("set - a b c; echo $#").join();
        assertThat(result.stdout()).isEqualTo("3\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void setClearsOldPositionalParams() {
        Bash bash = new Bash();
        var result = bash.exec("set -- x y; set -- a; echo $1 $2").join();
        assertThat(result.stdout()).isEqualTo("a \n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void setErrexitFlag() {
        Bash bash = new Bash();
        bash.exec("set -e").join();
        assertThat(bash.getEnv().get("SHELLOPTS")).contains("errexit");
        bash.shutdown();
    }

    @Test
    void setNoUnsetFlag() {
        Bash bash = new Bash();
        bash.exec("set -u").join();
        assertThat(bash.getEnv().get("SHELLOPTS")).contains("nounset");
        bash.shutdown();
    }

    @Test
    void setCombinedFlags() {
        Bash bash = new Bash();
        bash.exec("set -eu").join();
        assertThat(bash.getEnv().get("SHELLOPTS")).contains("errexit");
        assertThat(bash.getEnv().get("SHELLOPTS")).contains("nounset");
        bash.shutdown();
    }

    @Test
    void setTurnOffFlag() {
        Bash bash = new Bash();
        bash.exec("set -e; set +e").join();
        assertThat(bash.getEnv().get("SHELLOPTS")).doesNotContain("errexit");
        bash.shutdown();
    }

    @Test
    void setOptionByName() {
        Bash bash = new Bash();
        bash.exec("set -o xtrace").join();
        assertThat(bash.getEnv().get("SHELLOPTS")).contains("xtrace");
        bash.shutdown();
    }

    @Test
    void setOptionOffByName() {
        Bash bash = new Bash();
        bash.exec("set -o xtrace; set +o xtrace").join();
        assertThat(bash.getEnv().get("SHELLOPTS")).doesNotContain("xtrace");
        bash.shutdown();
    }

    @Test
    void setWithFlagsAndPositionalParams() {
        Bash bash = new Bash();
        var result = bash.exec("set -e -- a b; echo $1 $2").join();
        assertThat(result.stdout()).isEqualTo("a b\n");
        assertThat(bash.getEnv().get("SHELLOPTS")).contains("errexit");
        bash.shutdown();
    }

    @Test
    void setPrintsVariables() {
        Bash bash = new Bash();
        bash.exec("x=hello; y=world").join();
        var result = bash.exec("set").join();
        assertThat(result.stdout()).contains("x=hello");
        assertThat(result.stdout()).contains("y=world");
        bash.shutdown();
    }

    @Test
    void setNoArgsWithPositionalParams() {
        Bash bash = new Bash();
        bash.exec("set -- a b c").join();
        var result = bash.exec("set").join();
        assertThat(result.stdout()).contains("1=a");
        assertThat(result.stdout()).contains("2=b");
        assertThat(result.stdout()).contains("3=c");
        bash.shutdown();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Arrays
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void arrayLiteral() {
        Bash bash = new Bash();
        var result = bash.exec("arr=(a b c); echo ${arr[0]} ${arr[1]} ${arr[2]}").join();
        assertThat(result.stdout()).isEqualTo("a b c\n");
        bash.shutdown();
    }

    @Test
    void arrayIndexedAssignment() {
        Bash bash = new Bash();
        var result = bash.exec("arr[0]=x; arr[1]=y; echo ${arr[0]} ${arr[1]}").join();
        assertThat(result.stdout()).isEqualTo("x y\n");
        bash.shutdown();
    }

    @Test
    void arrayAllElementsAt() {
        Bash bash = new Bash();
        var result = bash.exec("arr=(a b c); for x in ${arr[@]}; do echo $x; done").join();
        assertThat(result.stdout()).isEqualTo("a\nb\nc\n");
        bash.shutdown();
    }

    @Test
    void arrayAllElementsStar() {
        Bash bash = new Bash();
        var result = bash.exec("arr=(a b c); echo ${arr[*]}").join();
        assertThat(result.stdout()).isEqualTo("a b c\n");
        bash.shutdown();
    }

    @Test
    void arrayLength() {
        Bash bash = new Bash();
        var result = bash.exec("arr=(a b c); echo ${#arr[@]}").join();
        assertThat(result.stdout()).isEqualTo("3\n");
        bash.shutdown();
    }

    @Test
    void arrayDollarAccessReturnsFirstElement() {
        Bash bash = new Bash();
        var result = bash.exec("arr=(x y z); echo $arr").join();
        assertThat(result.stdout()).isEqualTo("x\n");
        bash.shutdown();
    }

    @Test
    void arrayEmpty() {
        Bash bash = new Bash();
        var result = bash.exec("arr=(); echo ${#arr[@]}").join();
        assertThat(result.stdout()).isEqualTo("0\n");
        bash.shutdown();
    }

    @Test
    void arrayElementLength() {
        Bash bash = new Bash();
        var result = bash.exec("arr=(hello world); echo ${#arr[0]}").join();
        assertThat(result.stdout()).isEqualTo("5\n");
        bash.shutdown();
    }

    @Test
    void arrayOutOfBounds() {
        Bash bash = new Bash();
        var result = bash.exec("arr=(a b); echo ${arr[5]}").join();
        assertThat(result.stdout()).isEqualTo("\n");
        bash.shutdown();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Brace Expansion
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void braceSimple() {
        Bash bash = new Bash();
        var result = bash.exec("echo {a,b,c}").join();
        assertThat(result.stdout()).isEqualTo("a b c\n");
        bash.shutdown();
    }

    @Test
    void braceNumericRange() {
        Bash bash = new Bash();
        var result = bash.exec("echo {1..3}").join();
        assertThat(result.stdout()).isEqualTo("1 2 3\n");
        bash.shutdown();
    }

    @Test
    void braceAlphaRange() {
        Bash bash = new Bash();
        var result = bash.exec("echo {a..c}").join();
        assertThat(result.stdout()).isEqualTo("a b c\n");
        bash.shutdown();
    }

    @Test
    void braceWithPrefixSuffix() {
        Bash bash = new Bash();
        var result = bash.exec("echo file{1,2}.txt").join();
        assertThat(result.stdout()).isEqualTo("file1.txt file2.txt\n");
        bash.shutdown();
    }

    @Test
    void braceNested() {
        Bash bash = new Bash();
        var result = bash.exec("echo {a,{b,c}}").join();
        assertThat(result.stdout()).isEqualTo("a b c\n");
        bash.shutdown();
    }

    @Test
    void braceInForLoop() {
        Bash bash = new Bash();
        var result = bash.exec("for x in {a,b,c}; do echo $x; done").join();
        assertThat(result.stdout()).isEqualTo("a\nb\nc\n");
        bash.shutdown();
    }

    @Test
    void braceNumericRangeWithStep() {
        Bash bash = new Bash();
        var result = bash.exec("echo {1..6..2}").join();
        assertThat(result.stdout()).isEqualTo("1 3 5\n");
        bash.shutdown();
    }

    @Test
    void braceNoExpansion() {
        Bash bash = new Bash();
        var result = bash.exec("echo hello").join();
        assertThat(result.stdout()).isEqualTo("hello\n");
        bash.shutdown();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Glob Expansion
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void globStar() {
        Bash bash = new Bash();
        bash.exec(": > a.txt; : > b.txt; : > c.txt").join();
        var result = bash.exec("echo *.txt").join();
        assertThat(result.stdout()).isEqualTo("a.txt b.txt c.txt\n");
        bash.shutdown();
    }

    @Test
    void globQuestion() {
        Bash bash = new Bash();
        bash.exec(": > file1.txt; : > file2.txt; : > other.txt").join();
        var result = bash.exec("echo file?.txt").join();
        assertThat(result.stdout()).isEqualTo("file1.txt file2.txt\n");
        bash.shutdown();
    }

    @Test
    void globBracket() {
        Bash bash = new Bash();
        bash.exec(": > a.log; : > b.log; : > c.log").join();
        var result = bash.exec("echo [ab].log").join();
        assertThat(result.stdout()).isEqualTo("a.log b.log\n");
        bash.shutdown();
    }

    @Test
    void globNoMatchReturnsLiteral() {
        Bash bash = new Bash();
        var result = bash.exec("echo *.nonexistent").join();
        assertThat(result.stdout()).isEqualTo("*.nonexistent\n");
        bash.shutdown();
    }

    @Test
    void globNullglob() {
        Bash bash = new Bash();
        bash.exec("shopt -s nullglob").join();
        var result = bash.exec("echo *.nonexistent").join();
        assertThat(result.stdout()).isEqualTo("\n");
        bash.shutdown();
    }

    @Test
    void globDotglob() {
        Bash bash = new Bash();
        bash.exec(": > .hidden; : > visible").join();
        bash.exec("shopt -s dotglob").join();
        var result = bash.exec("echo *").join();
        assertThat(result.stdout()).contains(".hidden");
        assertThat(result.stdout()).contains("visible");
        bash.shutdown();
    }

    @Test
    void globHiddenExcludedByDefault() {
        Bash bash = new Bash();
        bash.exec(": > .hidden; : > visible").join();
        var result = bash.exec("echo *").join();
        assertThat(result.stdout()).isEqualTo("visible\n");
        bash.shutdown();
    }

    @Test
    void globMixedWithLiterals() {
        Bash bash = new Bash();
        bash.exec(": > a.txt; : > b.txt").join();
        var result = bash.exec("echo pre-*.txt").join();
        assertThat(result.stdout()).isEqualTo("pre-*.txt\n");
        bash.shutdown();
    }
}
