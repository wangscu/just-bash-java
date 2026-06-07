package com.justbash.commands.queryengine;

import java.util.List;

public sealed interface AstNode permits
    AstNode.IdentityNode, AstNode.FieldNode, AstNode.IndexNode, AstNode.SliceNode,
    AstNode.IterateNode, AstNode.PipeNode, AstNode.CommaNode, AstNode.LiteralNode,
    AstNode.ArrayNode, AstNode.ObjectNode, AstNode.ParenNode, AstNode.BinaryOpNode,
    AstNode.UnaryOpNode, AstNode.CondNode, AstNode.TryNode, AstNode.CallNode,
    AstNode.VarBindNode, AstNode.VarRefNode, AstNode.RecurseNode, AstNode.OptionalNode,
    AstNode.StringInterpNode, AstNode.UpdateOpNode, AstNode.ReduceNode,
    AstNode.ForeachNode, AstNode.LabelNode, AstNode.BreakNode, AstNode.DefNode {

    record IdentityNode() implements AstNode {}

    record FieldNode(String name, AstNode base) implements AstNode {}

    record IndexNode(AstNode index, AstNode base) implements AstNode {}

    record SliceNode(AstNode start, AstNode end, AstNode base) implements AstNode {}

    record IterateNode(AstNode base) implements AstNode {}

    record PipeNode(AstNode left, AstNode right) implements AstNode {}

    record CommaNode(AstNode left, AstNode right) implements AstNode {}

    record LiteralNode(Object value) implements AstNode {}

    record ArrayNode(AstNode elements) implements AstNode {}

    record ObjectNode(List<ObjectEntry> entries) implements AstNode {
        public record ObjectEntry(AstNode key, AstNode value) {}
    }

    record ParenNode(AstNode expr) implements AstNode {}

    record BinaryOpNode(String op, AstNode left, AstNode right) implements AstNode {}

    record UnaryOpNode(String op, AstNode operand) implements AstNode {}

    record CondNode(AstNode cond, AstNode thenBranch, List<ElifBranch> elifs, AstNode elseBranch) implements AstNode {
        public record ElifBranch(AstNode cond, AstNode thenBranch) {}
    }

    record TryNode(AstNode body, AstNode catchBranch) implements AstNode {}

    record CallNode(String name, List<AstNode> args) implements AstNode {}

    record VarBindNode(String name, AstNode value, AstNode body, DestructurePattern pattern, List<DestructurePattern> alternatives) implements AstNode {}

    record VarRefNode(String name) implements AstNode {}

    record RecurseNode() implements AstNode {}

    record OptionalNode(AstNode expr) implements AstNode {}

    record StringInterpNode(List<StringInterpPart> parts) implements AstNode {}

    record UpdateOpNode(String op, AstNode path, AstNode value) implements AstNode {}

    record ReduceNode(AstNode expr, String varName, DestructurePattern pattern, AstNode init, AstNode update) implements AstNode {}

    record ForeachNode(AstNode expr, String varName, DestructurePattern pattern, AstNode init, AstNode update, AstNode extract) implements AstNode {}

    record LabelNode(String name, AstNode body) implements AstNode {}

    record BreakNode(String name) implements AstNode {}

    record DefNode(String name, List<String> params, AstNode funcBody, AstNode body) implements AstNode {}
}
