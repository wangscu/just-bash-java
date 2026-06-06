package com.justbash.parser;

public enum TokenType {
    EOF,
    WORD,
    ASSIGNMENT_WORD,
    NAME,
    NEWLINE,
    IO_NUMBER,

    AND_IF,    // &&
    OR_IF,     // ||
    DSEMI,     // ;;
    DLESS,     // <<
    DGREAT,    // >>
    LESSAND,   // <&
    GREATAND,  // >&
    LESSGREAT, // <>
    DLESSDASH, // <<-
    CLOBBER,   // >|
    TRIPLE_LESS, // <<<

    SEMI,      // ;
    PIPE,      // |
    PIPE_AMPERSAND, // |&
    LPAREN,    // (
    RPAREN,    // )
    LESS,      // <
    GREAT,     // >
    LBRACE,    // {
    RBRACE,    // }
    DLBRACKET, // [[
    DRBRACKET, // ]]
    BANG,      // !

    IF, THEN, ELSE, ELIF, FI,
    FOR, WHILE, UNTIL, DO, DONE,
    CASE, ESAC, IN,
    FUNCTION,
    TIME
}
