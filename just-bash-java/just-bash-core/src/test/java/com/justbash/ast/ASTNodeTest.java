package com.justbash.ast;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ASTNodeTest {
    @Test
    void scriptNodeHasCorrectType() {
        ScriptNode script = new ScriptNode(1, List.of());
        assertThat(script.type()).isEqualTo("Script");
        assertThat(script.line()).isEqualTo(1);
        assertThat(script.statements()).isEmpty();
    }

    @Test
    void statementNodeHasOperators() {
        StatementNode stmt = new StatementNode(
            1, List.of(), List.of(), false);
        assertThat(stmt.type()).isEqualTo("Statement");
        assertThat(stmt.background()).isFalse();
    }

    @Test
    void pipelineNodeHasNegatedFlag() {
        PipelineNode pipe = new PipelineNode(1, List.of(), true);
        assertThat(pipe.negated()).isTrue();
        assertThat(pipe.timed()).isFalse();
    }
}
