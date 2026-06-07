package com.justbash.commands.queryengine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ParserTest {

    @Test
    public void testIdentity() {
        AstNode node = Parser.parse(".");
        assertInstanceOf(AstNode.IdentityNode.class, node);
    }

    @Test
    public void testFieldAccess() {
        AstNode node = Parser.parse(".foo");
        assertInstanceOf(AstNode.FieldNode.class, node);
        AstNode.FieldNode field = (AstNode.FieldNode) node;
        assertEquals("foo", field.name());
        assertNull(field.base());
    }

    @Test
    public void testPipe() {
        AstNode node = Parser.parse(".foo | .bar");
        assertInstanceOf(AstNode.PipeNode.class, node);
    }

    @Test
    public void testComma() {
        AstNode node = Parser.parse(".foo, .bar");
        assertInstanceOf(AstNode.CommaNode.class, node);
    }

    @Test
    public void testArray() {
        AstNode node = Parser.parse("[]");
        assertInstanceOf(AstNode.ArrayNode.class, node);
        AstNode.ArrayNode arr = (AstNode.ArrayNode) node;
        assertNull(arr.elements());
    }

    @Test
    public void testArrayWithElements() {
        AstNode node = Parser.parse("[.foo]");
        assertInstanceOf(AstNode.ArrayNode.class, node);
    }

    @Test
    public void testObject() {
        AstNode node = Parser.parse("{a: 1, b: 2}");
        assertInstanceOf(AstNode.ObjectNode.class, node);
        AstNode.ObjectNode obj = (AstNode.ObjectNode) node;
        assertEquals(2, obj.entries().size());
    }

    @Test
    public void testLiteral() {
        AstNode node = Parser.parse("true");
        assertInstanceOf(AstNode.LiteralNode.class, node);
        assertEquals(true, ((AstNode.LiteralNode) node).value());
    }

    @Test
    public void testVarRef() {
        AstNode node = Parser.parse("$foo");
        assertInstanceOf(AstNode.VarRefNode.class, node);
        assertEquals("$foo", ((AstNode.VarRefNode) node).name());
    }

    @Test
    public void testVarBind() {
        AstNode node = Parser.parse(".foo as $x | .bar");
        assertInstanceOf(AstNode.VarBindNode.class, node);
        AstNode.VarBindNode bind = (AstNode.VarBindNode) node;
        assertEquals("$x", bind.name());
    }

    @Test
    public void testIfThenElse() {
        AstNode node = Parser.parse("if .foo then 1 else 2 end");
        assertInstanceOf(AstNode.CondNode.class, node);
    }

    @Test
    public void testTryCatch() {
        AstNode node = Parser.parse("try .foo catch .bar");
        assertInstanceOf(AstNode.TryNode.class, node);
    }

    @Test
    public void testReduce() {
        AstNode node = Parser.parse("reduce .[] as $x (0; . + $x)");
        assertInstanceOf(AstNode.ReduceNode.class, node);
    }

    @Test
    public void testForeach() {
        AstNode node = Parser.parse("foreach .[] as $x (0; . + $x)");
        assertInstanceOf(AstNode.ForeachNode.class, node);
    }

    @Test
    public void testRecurse() {
        AstNode node = Parser.parse("..");
        assertInstanceOf(AstNode.RecurseNode.class, node);
    }

    @Test
    public void testOptional() {
        AstNode node = Parser.parse(".foo?");
        assertInstanceOf(AstNode.OptionalNode.class, node);
    }

    @Test
    public void testIndex() {
        AstNode node = Parser.parse(".foo[0]");
        assertInstanceOf(AstNode.IndexNode.class, node);
    }

    @Test
    public void testSlice() {
        AstNode node = Parser.parse(".foo[1:3]");
        assertInstanceOf(AstNode.SliceNode.class, node);
    }

    @Test
    public void testIterate() {
        AstNode node = Parser.parse(".foo[]");
        assertInstanceOf(AstNode.IterateNode.class, node);
    }

    @Test
    public void testBinaryOp() {
        AstNode node = Parser.parse(".foo + .bar");
        assertInstanceOf(AstNode.BinaryOpNode.class, node);
        assertEquals("+", ((AstNode.BinaryOpNode) node).op());
    }

    @Test
    public void testUnaryOp() {
        AstNode node = Parser.parse("-.foo");
        assertInstanceOf(AstNode.UnaryOpNode.class, node);
        assertEquals("-", ((AstNode.UnaryOpNode) node).op());
    }

    @Test
    public void testCall() {
        AstNode node = Parser.parse("length");
        assertInstanceOf(AstNode.CallNode.class, node);
        assertEquals("length", ((AstNode.CallNode) node).name());
    }

    @Test
    public void testCallWithArgs() {
        AstNode node = Parser.parse("map(.foo)");
        assertInstanceOf(AstNode.CallNode.class, node);
        AstNode.CallNode call = (AstNode.CallNode) node;
        assertEquals("map", call.name());
        assertEquals(1, call.args().size());
    }

    @Test
    public void testStringInterpolation() {
        AstNode node = Parser.parse("\"hello \\(.name)\"");
        assertInstanceOf(AstNode.StringInterpNode.class, node);
    }

    @Test
    public void testLabelBreak() {
        AstNode node = Parser.parse("label $out | break $out");
        assertInstanceOf(AstNode.LabelNode.class, node);
    }

    @Test
    public void testDef() {
        AstNode node = Parser.parse("def foo: .bar; foo");
        assertInstanceOf(AstNode.DefNode.class, node);
    }

    @Test
    public void testUpdateOp() {
        AstNode node = Parser.parse(".foo = 1");
        assertInstanceOf(AstNode.UpdateOpNode.class, node);
        assertEquals("=", ((AstNode.UpdateOpNode) node).op());
    }

    @Test
    public void testAlt() {
        AstNode node = Parser.parse(".foo // .bar");
        assertInstanceOf(AstNode.BinaryOpNode.class, node);
        assertEquals("//", ((AstNode.BinaryOpNode) node).op());
    }

    @Test
    public void testComparison() {
        AstNode node = Parser.parse(".foo == .bar");
        assertInstanceOf(AstNode.BinaryOpNode.class, node);
        assertEquals("==", ((AstNode.BinaryOpNode) node).op());
    }

    @Test
    public void testObjectShorthand() {
        AstNode node = Parser.parse("{foo}");
        assertInstanceOf(AstNode.ObjectNode.class, node);
        AstNode.ObjectNode obj = (AstNode.ObjectNode) node;
        assertEquals(1, obj.entries().size());
    }

    @Test
    public void testArrayPatternBinding() {
        AstNode node = Parser.parse(". as [$a, $b] | .");
        assertInstanceOf(AstNode.VarBindNode.class, node);
        AstNode.VarBindNode bind = (AstNode.VarBindNode) node;
        assertNotNull(bind.pattern());
    }

    @Test
    public void testObjectPatternBinding() {
        AstNode node = Parser.parse(". as {a: $x} | .");
        assertInstanceOf(AstNode.VarBindNode.class, node);
        AstNode.VarBindNode bind = (AstNode.VarBindNode) node;
        assertNotNull(bind.pattern());
    }

    @Test
    public void testNotAsCall() {
        AstNode node = Parser.parse("not");
        assertInstanceOf(AstNode.CallNode.class, node);
        assertEquals("not", ((AstNode.CallNode) node).name());
    }

    @Test
    public void testParen() {
        AstNode node = Parser.parse("(.foo)");
        assertInstanceOf(AstNode.ParenNode.class, node);
    }

    @Test
    public void testUnexpectedToken() {
        ParseException ex = assertThrows(ParseException.class, () -> Parser.parse(".foo]"));
        assertTrue(ex.getMessage().contains("Unexpected token"));
    }
}
