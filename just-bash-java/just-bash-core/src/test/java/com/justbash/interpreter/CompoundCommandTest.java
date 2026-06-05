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
}
