package com.justbash.interpreter;

import java.util.Map;
import java.util.Set;

public class CommandResolver {

    private static final Set<String> BUILTINS = Set.of(
        "echo", "true", "false", "cd", "pwd", "export", "unset",
        "exit", "return", "shift", "source", ".", "eval", "exec",
        "set", "shopt", "local", "declare", "typeset", "readonly",
        "printf", "test", "[", "[[", "alias", "unalias", "wait",
        "jobs", "fg", "bg", "kill", "umask", "trap", "times",
        "hash", "help", "history", "logout", "read", "ulimit",
        "caller", "command", "compgen", "complete", "compopt",
        "builtin", "enable", "mapfile", "readarray",
        "type", "let", "bind", "break", "continue", "getopts",
        "popd", "pushd", "dirs", "suspend", "disown"
    );

    /** Resolve command name to full path or return as-is if it's a builtin */
    public String resolve(String name, Map<String, String> env) {
        if (name.contains("/")) return name;
        // For MVP, just return the name (we don't have real filesystem command lookup yet)
        return name;
    }

    public boolean isBuiltin(String name) {
        return BUILTINS.contains(name);
    }
}
