package com.justbash.ast;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ASTFactoryTest {
    @Test
    void canBuildSimpleEchoCommand() {
        ScriptNode ast = ASTFactory.script(List.of(
            ASTFactory.statement(List.of(
                ASTFactory.pipeline(List.of(
                    ASTFactory.simpleCommand(
                        ASTFactory.word(ASTFactory.literal("echo")),
                        List.of(ASTFactory.word(ASTFactory.literal("hello"))),
                        List.of(),
                        List.of()
                    )
                ), false)
            ), List.of(), false)
        ));

        assertThat(ast.type()).isEqualTo("Script");
        assertThat(ast.statements()).hasSize(1);
    }

    @Test
    void parameterExpansionWithoutOperation() {
        var pe = ASTFactory.parameterExpansion("HOME");
        assertThat(pe.parameter()).isEqualTo("HOME");
        assertThat(pe.operation()).isEmpty();
    }
}
