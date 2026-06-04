package com.justbash.parser;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class LexerTest {
    @Test
    void tokenizeEchoHello() {
        Lexer lexer = new Lexer("echo hello");
        List<Token> tokens = lexer.tokenize();
        assertThat(tokens).hasSize(3);
        assertThat(tokens.get(0)).isEqualTo(new Token(TokenType.WORD, "echo", 1, 1));
        assertThat(tokens.get(1)).isEqualTo(new Token(TokenType.WORD, "hello", 1, 6));
        assertThat(tokens.get(2).type()).isEqualTo(TokenType.EOF);
    }

    @Test
    void tokenizePipeline() {
        Lexer lexer = new Lexer("echo hello | wc -l");
        List<Token> tokens = lexer.tokenize();
        assertThat(tokens.stream().map(Token::type).toList())
            .containsExactly(TokenType.WORD, TokenType.WORD, TokenType.PIPE,
                TokenType.WORD, TokenType.WORD, TokenType.EOF);
    }

    @Test
    void tokenizeReservedWords() {
        Lexer lexer = new Lexer("if true; then echo hi; fi");
        List<Token> tokens = lexer.tokenize();
        assertThat(tokens.get(0).type()).isEqualTo(TokenType.IF);
        assertThat(tokens.get(2).type()).isEqualTo(TokenType.SEMI);
        assertThat(tokens.get(3).type()).isEqualTo(TokenType.THEN);
        assertThat(tokens.get(6).type()).isEqualTo(TokenType.SEMI);
        assertThat(tokens.get(7).type()).isEqualTo(TokenType.FI);
    }

    @Test
    void skipsComments() {
        Lexer lexer = new Lexer("echo hi # this is a comment");
        List<Token> tokens = lexer.tokenize();
        assertThat(tokens).hasSize(3);
    }
}
