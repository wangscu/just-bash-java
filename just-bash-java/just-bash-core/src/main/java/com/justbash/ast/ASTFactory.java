package com.justbash.ast;

import com.justbash.ast.command.*;
import com.justbash.ast.word.*;
import com.justbash.ast.operations.*;
import com.justbash.ast.expression.*;
import java.util.List;
import java.util.Optional;

public final class ASTFactory {
    private ASTFactory() {}

    public static ScriptNode script(List<StatementNode> statements) {
        return new ScriptNode(0, List.copyOf(statements));
    }

    public static ScriptNode script(int line, List<StatementNode> statements) {
        return new ScriptNode(line, List.copyOf(statements));
    }

    public static StatementNode statement(
            List<PipelineNode> pipelines,
            List<StatementNode.StatementOperator> operators,
            boolean background) {
        return new StatementNode(0, pipelines, operators, background);
    }

    public static PipelineNode pipeline(List<CommandNode> commands, boolean negated) {
        return new PipelineNode(0, commands, negated);
    }

    public static SimpleCommandNode simpleCommand(
            WordNode name, List<WordNode> args,
            List<AssignmentNode> assignments,
            List<RedirectionNode> redirections) {
        return new SimpleCommandNode(0, name, args, assignments, redirections);
    }

    public static WordNode word(WordPart... parts) {
        return new WordNode(0, List.of(parts));
    }

    public static WordNode word(int line, List<WordPart> parts) {
        return new WordNode(line, List.copyOf(parts));
    }

    public static LiteralPart literal(String value) {
        return new LiteralPart(0, value);
    }

    public static SingleQuotedPart singleQuoted(String value) {
        return new SingleQuotedPart(0, value);
    }

    public static DoubleQuotedPart doubleQuoted(WordPart... parts) {
        return new DoubleQuotedPart(0, List.of(parts));
    }

    public static ParameterExpansionPart parameterExpansion(String parameter) {
        return new ParameterExpansionPart(0, parameter, Optional.empty());
    }

    public static CommandSubstitutionPart commandSubstitution(ScriptNode body) {
        return new CommandSubstitutionPart(0, body, false);
    }

    public static AssignmentNode assignment(
            String name, WordNode value, boolean append) {
        return new AssignmentNode(0, name, Optional.ofNullable(value), append, Optional.empty());
    }

    public static RedirectionNode redirection(
            RedirectionNode.RedirectionOperator operator,
            WordNode target) {
        return new RedirectionNode(0, Optional.empty(), Optional.empty(), operator,
            new RedirectionNode.WordTarget(target));
    }

    public static IfNode ifNode(
            List<IfNode.IfClause> clauses,
            List<StatementNode> elseBody,
            List<RedirectionNode> redirections) {
        return new IfNode(0, clauses, elseBody, redirections);
    }

    public static ForNode forNode(
            String variable, List<WordNode> words,
            List<StatementNode> body,
            List<RedirectionNode> redirections) {
        return new ForNode(0, variable, Optional.ofNullable(words),
            body, redirections);
    }

    public static FunctionDefNode functionDef(
            String name, CompoundCommandNode body,
            List<RedirectionNode> redirections) {
        return new FunctionDefNode(0, name, body, redirections, Optional.empty());
    }
}
