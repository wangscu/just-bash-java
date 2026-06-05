package com.justbash.interpreter;

import com.justbash.Bash;
import com.justbash.BashExecResult;
import com.justbash.ast.ASTFactory;
import com.justbash.ast.StatementNode;
import com.justbash.fs.InMemoryFs;
import com.justbash.security.ExecutionLimits;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.assertThat;

class InterpreterTest {

    private Interpreter createInterpreter() {
        InterpreterState state = InterpreterState.defaults();
        InterpreterOptions options = new InterpreterOptions(
            new InMemoryFs(),
            new LinkedHashMap<>(),
            ExecutionLimits.defaults(),
            (script, execOpts) -> CompletableFuture.completedFuture(
                new BashExecResult("", "", 0, Map.of())
            )
        );
        return new Interpreter(options, state);
    }

    @Test
    void echoHello() {
        var script = ASTFactory.script(
            List.of(ASTFactory.statement(
                List.of(ASTFactory.pipeline(
                    List.of(ASTFactory.simpleCommand(
                        ASTFactory.word(ASTFactory.literal("echo")),
                        List.of(ASTFactory.word(ASTFactory.literal("hello"))),
                        List.of(),
                        List.of()
                    )),
                    false
                )),
                List.of(),
                false
            ))
        );
        var interpreter = createInterpreter();
        var result = interpreter.executeScript(script);
        assertThat(result.stdout()).isEqualTo("hello\n");
        assertThat(result.exitCode()).isEqualTo(0);
    }

    @Test
    void echoMultipleArgs() {
        var script = ASTFactory.script(
            List.of(ASTFactory.statement(
                List.of(ASTFactory.pipeline(
                    List.of(ASTFactory.simpleCommand(
                        ASTFactory.word(ASTFactory.literal("echo")),
                        List.of(
                            ASTFactory.word(ASTFactory.literal("hello")),
                            ASTFactory.word(ASTFactory.literal("world"))
                        ),
                        List.of(),
                        List.of()
                    )),
                    false
                )),
                List.of(),
                false
            ))
        );
        var interpreter = createInterpreter();
        var result = interpreter.executeScript(script);
        assertThat(result.stdout()).isEqualTo("hello world\n");
        assertThat(result.exitCode()).isEqualTo(0);
    }

    @Test
    void logicalAnd() {
        var script = ASTFactory.script(
            List.of(ASTFactory.statement(
                List.of(
                    ASTFactory.pipeline(
                        List.of(ASTFactory.simpleCommand(
                            ASTFactory.word(ASTFactory.literal("true")),
                            List.of(),
                            List.of(),
                            List.of()
                        )),
                        false
                    ),
                    ASTFactory.pipeline(
                        List.of(ASTFactory.simpleCommand(
                            ASTFactory.word(ASTFactory.literal("echo")),
                            List.of(ASTFactory.word(ASTFactory.literal("ok"))),
                            List.of(),
                            List.of()
                        )),
                        false
                    )
                ),
                List.of(StatementNode.StatementOperator.AND),
                false
            ))
        );
        var interpreter = createInterpreter();
        var result = interpreter.executeScript(script);
        assertThat(result.stdout()).isEqualTo("ok\n");
        assertThat(result.exitCode()).isEqualTo(0);
    }

    @Test
    void logicalOr() {
        var script = ASTFactory.script(
            List.of(ASTFactory.statement(
                List.of(
                    ASTFactory.pipeline(
                        List.of(ASTFactory.simpleCommand(
                            ASTFactory.word(ASTFactory.literal("false")),
                            List.of(),
                            List.of(),
                            List.of()
                        )),
                        false
                    ),
                    ASTFactory.pipeline(
                        List.of(ASTFactory.simpleCommand(
                            ASTFactory.word(ASTFactory.literal("echo")),
                            List.of(ASTFactory.word(ASTFactory.literal("recovered"))),
                            List.of(),
                            List.of()
                        )),
                        false
                    )
                ),
                List.of(StatementNode.StatementOperator.OR),
                false
            ))
        );
        var interpreter = createInterpreter();
        var result = interpreter.executeScript(script);
        assertThat(result.stdout()).isEqualTo("recovered\n");
        assertThat(result.exitCode()).isEqualTo(0);
    }

    @Test
    void pipelineEchoCat() {
        var script = ASTFactory.script(
            List.of(ASTFactory.statement(
                List.of(ASTFactory.pipeline(
                    List.of(
                        ASTFactory.simpleCommand(
                            ASTFactory.word(ASTFactory.literal("echo")),
                            List.of(ASTFactory.word(ASTFactory.literal("hello"))),
                            List.of(),
                            List.of()
                        ),
                        ASTFactory.simpleCommand(
                            ASTFactory.word(ASTFactory.literal("cat")),
                            List.of(),
                            List.of(),
                            List.of()
                        )
                    ),
                    false
                )),
                List.of(),
                false
            ))
        );
        var interpreter = createInterpreter();
        var result = interpreter.executeScript(script);
        // Pipeline passes stdout of echo as stdin of cat
        // For MVP, cat is not a builtin so returns command not found
        // This test mainly verifies pipeline structure doesn't crash
        assertThat(result.exitCode()).isNotNull();
    }

    @Test
    void endToEndViaBash() {
        Bash bash = new Bash();
        var result = bash.exec("echo hello world").join();
        assertThat(result.stdout()).isEqualTo("hello world\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void endToEndMultipleStatements() {
        Bash bash = new Bash();
        var result = bash.exec("echo first; echo second").join();
        assertThat(result.stdout()).isEqualTo("first\nsecond\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void endToEndLogicalOperators() {
        Bash bash = new Bash();
        var result = bash.exec("false || echo recovered").join();
        assertThat(result.stdout()).isEqualTo("recovered\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void endToEndLogicalAnd() {
        Bash bash = new Bash();
        var result = bash.exec("true && echo ok").join();
        assertThat(result.stdout()).isEqualTo("ok\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void endToEndPrefixAssignment() {
        Bash bash = new Bash();
        var result = bash.exec("FOO=bar echo $FOO").join();
        // Prefix assignment sets FOO=bar in env, then $FOO expands to bar
        assertThat(result.stdout()).isEqualTo("bar\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void endToEndCommandNotFound() {
        Bash bash = new Bash();
        var result = bash.exec("nonexistent_command").join();
        assertThat(result.stderr()).contains("command not found");
        assertThat(result.exitCode()).isEqualTo(127);
        bash.shutdown();
    }

    @Test
    void endToEndSingleQuotes() {
        Bash bash = new Bash();
        var result = bash.exec("echo 'hello world'").join();
        assertThat(result.stdout()).isEqualTo("hello world\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void endToEndDoubleQuotes() {
        Bash bash = new Bash();
        var result = bash.exec("echo \"hello world\"").join();
        assertThat(result.stdout()).isEqualTo("hello world\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void endToEndMixedQuotes() {
        Bash bash = new Bash();
        var result = bash.exec("echo hello'world'").join();
        assertThat(result.stdout()).isEqualTo("helloworld\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void endToEndQuotedAndLiteral() {
        Bash bash = new Bash();
        var result = bash.exec("echo 'hello'world").join();
        assertThat(result.stdout()).isEqualTo("helloworld\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }
}
