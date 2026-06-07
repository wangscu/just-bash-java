package com.justbash.commands.queryengine;

public interface StringInterpPart {}

record StringPart(String value) implements StringInterpPart {}

record ExprPart(AstNode expr) implements StringInterpPart {}
