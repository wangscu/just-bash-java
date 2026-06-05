package com.justbash.parser;

import com.justbash.ast.ScriptNode;
import com.justbash.ast.StatementNode;
import com.justbash.ast.PipelineNode;
import com.justbash.ast.command.*;
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

    @Test
    void parseIfStatement() {
        ScriptNode ast = Parser.parse("if true; then echo yes; fi");
        assertThat(ast.statements()).hasSize(1);
        IfNode ifNode = (IfNode) ast.statements().get(0).pipelines().get(0).commands().get(0);
        assertThat(ifNode.clauses()).hasSize(1);
        assertThat(ifNode.elseBody()).isEmpty();
    }

    @Test
    void parseIfElse() {
        ScriptNode ast = Parser.parse("if false; then echo no; else echo yes; fi");
        IfNode ifNode = (IfNode) ast.statements().get(0).pipelines().get(0).commands().get(0);
        assertThat(ifNode.clauses()).hasSize(1);
        assertThat(ifNode.elseBody()).hasSize(1);
    }

    @Test
    void parseIfElif() {
        ScriptNode ast = Parser.parse("if false; then echo a; elif true; then echo b; fi");
        IfNode ifNode = (IfNode) ast.statements().get(0).pipelines().get(0).commands().get(0);
        assertThat(ifNode.clauses()).hasSize(2);
        assertThat(ifNode.elseBody()).isEmpty();
    }

    @Test
    void parseForLoop() {
        ScriptNode ast = Parser.parse("for i in a b c; do echo $i; done");
        ForNode forNode = (ForNode) ast.statements().get(0).pipelines().get(0).commands().get(0);
        assertThat(forNode.variable()).isEqualTo("i");
        assertThat(forNode.words()).isPresent();
        assertThat(forNode.words().get()).hasSize(3);
        assertThat(forNode.body()).hasSize(1);
    }

    @Test
    void parseWhileLoop() {
        ScriptNode ast = Parser.parse("while false; do echo loop; done");
        WhileNode whileNode = (WhileNode) ast.statements().get(0).pipelines().get(0).commands().get(0);
        assertThat(whileNode.isUntil()).isFalse();
        assertThat(whileNode.condition()).hasSize(1);
        assertThat(whileNode.body()).hasSize(1);
    }

    @Test
    void parseUntilLoop() {
        ScriptNode ast = Parser.parse("until true; do echo loop; done");
        WhileNode whileNode = (WhileNode) ast.statements().get(0).pipelines().get(0).commands().get(0);
        assertThat(whileNode.isUntil()).isTrue();
        assertThat(whileNode.condition()).hasSize(1);
        assertThat(whileNode.body()).hasSize(1);
    }

    @Test
    void parseGroupCommand() {
        ScriptNode ast = Parser.parse("{ echo a; echo b; }");
        GroupNode group = (GroupNode) ast.statements().get(0).pipelines().get(0).commands().get(0);
        assertThat(group.body()).hasSize(1);
        assertThat(group.body().get(0).pipelines()).hasSize(2);
    }

    @Test
    void parseCaseStatement() {
        ScriptNode ast = Parser.parse("case x in a) echo one;; b) echo two;; esac");
        CaseNode caseNode = (CaseNode) ast.statements().get(0).pipelines().get(0).commands().get(0);
        assertThat(caseNode.word().parts().get(0)).extracting("value").isEqualTo("x");
        assertThat(caseNode.items()).hasSize(2);
        assertThat(caseNode.items().get(0).patterns()).hasSize(1);
        assertThat(caseNode.items().get(1).patterns()).hasSize(1);
    }

    @Test
    void parseCaseWithPipePatterns() {
        ScriptNode ast = Parser.parse("case x in a|b) echo match;; *) echo default;; esac");
        CaseNode caseNode = (CaseNode) ast.statements().get(0).pipelines().get(0).commands().get(0);
        assertThat(caseNode.items()).hasSize(2);
        assertThat(caseNode.items().get(0).patterns()).hasSize(2);
        assertThat(caseNode.items().get(1).patterns()).hasSize(1);
    }

    @Test
    void parseFunctionDefWithKeyword() {
        ScriptNode ast = Parser.parse("function foo() { echo hi; }");
        FunctionDefNode func = (FunctionDefNode) ast.statements().get(0).pipelines().get(0).commands().get(0);
        assertThat(func.name()).isEqualTo("foo");
        assertThat(func.body()).isInstanceOf(GroupNode.class);
    }

    @Test
    void parseFunctionDefWithoutKeyword() {
        ScriptNode ast = Parser.parse("bar() { echo hello; }");
        FunctionDefNode func = (FunctionDefNode) ast.statements().get(0).pipelines().get(0).commands().get(0);
        assertThat(func.name()).isEqualTo("bar");
        assertThat(func.body()).isInstanceOf(GroupNode.class);
    }
}
