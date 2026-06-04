package com.justbash.parser;

import com.justbash.ast.ScriptNode;
import com.justbash.ast.StatementNode;
import com.justbash.ast.PipelineNode;
import com.justbash.ast.command.SimpleCommandNode;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ParserTest {
    @Test
    void parseEchoHello() {
        ScriptNode ast = Parser.parse("echo hello");
        assertThat(ast.statements()).hasSize(1);
        StatementNode stmt = ast.statements().get(0);
        assertThat(stmt.pipelines()).hasSize(1);
        PipelineNode pipe = stmt.pipelines().get(0);
        assertThat(pipe.commands()).hasSize(1);
        SimpleCommandNode cmd = (SimpleCommandNode) pipe.commands().get(0);
        assertThat(cmd.name().parts().get(0)).extracting("value").isEqualTo("echo");
        assertThat(cmd.args()).hasSize(1);
    }

    @Test
    void parsePipeline() {
        ScriptNode ast = Parser.parse("echo hello | wc -l");
        PipelineNode pipe = ast.statements().get(0).pipelines().get(0);
        assertThat(pipe.commands()).hasSize(2);
    }

    @Test
    void parseAssignment() {
        ScriptNode ast = Parser.parse("FOO=bar echo test");
        SimpleCommandNode cmd = (SimpleCommandNode)
            ast.statements().get(0).pipelines().get(0).commands().get(0);
        assertThat(cmd.assignments()).hasSize(1);
        assertThat(cmd.assignments().get(0).name()).isEqualTo("FOO");
    }
}
