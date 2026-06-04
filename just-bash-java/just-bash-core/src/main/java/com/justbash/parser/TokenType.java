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

    SEMI,      // ;
    PIPE,      // |
    LPAREN,    // (
    RPAREN,    // )
    LESS,      // <
    GREAT,     // >
    LBRACE,    // {
    RBRACE,    // }
    BANG,      // !

    IF, THEN, ELSE, ELIF, FI,
    FOR, WHILE, UNTIL, DO, DONE,
    CASE, ESAC, IN,
    FUNCTION,
    TIME
}
