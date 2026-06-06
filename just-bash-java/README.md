# just-bash-java

A Java implementation of a secure, sandboxed bash interpreter with an in-memory virtual filesystem. Designed for AI agents needing a safe environment to execute bash scripts without access to the host system.

## Overview

just-bash-java provides a complete bash interpreter implemented in Java 21, featuring:

- **Secure by default**: All execution happens in an in-memory virtual filesystem
- **No external dependencies**: Pure Java implementation with no native code
- **Bash-compatible**: Supports pipelines, redirections, variables, arithmetic, conditionals, loops, functions, and more
- **Extensible command registry**: Register custom external commands
- **Programmatic API**: Embed as a library or use as a standalone CLI

## Project Structure

```
just-bash-java/
├── pom.xml                          # Parent POM
├── just-bash-core/                  # Core interpreter engine
│   └── src/main/java/com/justbash/
│       ├── Bash.java                # Main API entry point
│       ├── BashOptions.java         # Configuration options
│       ├── BashExecResult.java      # Execution result
│       ├── ast/                     # AST node definitions
│       ├── parser/                  # Lexer and parser
│       ├── interpreter/             # Execution engine
│       ├── fs/                      # Virtual filesystem (InMemoryFs, OverlayFs)
│       └── security/                # Execution limits
├── just-bash-commands/              # External command implementations
│   └── src/main/java/com/justbash/commands/
│       ├── CommandRegistry.java     # Registers all commands
│       ├── cat/CatCommand.java
│       ├── cp/CpCommand.java
│       ├── grep/GrepCommand.java
│       ├── head/HeadCommand.java
│       ├── ls/LsCommand.java
│       ├── mkdir/MkdirCommand.java
│       ├── mv/MvCommand.java
│       ├── printf/PrintfCommand.java
│       ├── rm/RmCommand.java
│       ├── tail/TailCommand.java
│       ├── touch/TouchCommand.java
│       └── wc/WcCommand.java
└── just-bash-cli/                   # Standalone CLI entry point
    └── src/main/java/com/justbash/cli/
        └── JustBashCli.java         # Command-line interface
```

## Requirements

- Java 21 or later
- Maven 3.9+

## Building

```bash
# Compile all modules
mvn compile

# Run all tests
mvn test

# Build fat JAR (includes all dependencies)
mvn package
```

## CLI Usage

### Basic execution

```bash
# Execute inline script
java -jar just-bash-cli/target/just-bash-cli-0.1.0-SNAPSHOT.jar -c 'echo hello'

# Execute with JSON output
java -jar just-bash-cli/target/just-bash-cli-0.1.0-SNAPSHOT.jar --json -c 'echo hello'

# Pipe script from stdin
echo 'echo hello world' | java -jar just-bash-cli/target/just-bash-cli-0.1.0-SNAPSHOT.jar

# Exit on first error
java -jar just-bash-cli/target/just-bash-cli-0.1.0-SNAPSHOT.jar -e -c 'false; echo not reached'

# Execute with real directory overlay (read-only by default)
java -jar just-bash-cli/target/just-bash-cli-0.1.0-SNAPSHOT.jar --root . -c 'ls -la'

# Execute with real directory overlay (allow writes to memory)
java -jar just-bash-cli/target/just-bash-cli-0.1.0-SNAPSHOT.jar --root . --allow-write -c 'echo test > /tmp/file.txt && cat /tmp/file.txt'
```

### CLI Options

```
just-bash - A secure bash environment for AI agents

Usage:
  just-bash [options] [script]
  just-bash -c 'script' [options]
  echo 'script' | just-bash [options]

Options:
  -c <script>       Execute the script from command line argument
  -e, --errexit     Exit immediately if a command exits with non-zero status
  --json            Output results as JSON (stdout, stderr, exitCode)
  --root <path>     Root directory for overlay filesystem
  --allow-write     Allow write operations (writes stay in memory)
  -h, --help        Show this help message
  -v, --version     Show version
```

## Programmatic API

### Basic usage

```java
import com.justbash.Bash;
import com.justbash.BashExecResult;
import com.justbash.commands.CommandRegistry;

public class Example {
    public static void main(String[] args) {
        Bash bash = new Bash();

        // Register external commands (cat, ls, grep, etc.)
        CommandRegistry.registerAll(bash);

        // Execute a script
        BashExecResult result = bash.exec("echo hello world").join();

        System.out.println("stdout: " + result.stdout());
        System.out.println("stderr: " + result.stderr());
        System.out.println("exit code: " + result.exitCode());

        bash.shutdown();
    }
}
```

### With OverlayFs (real directory backed)

```java
import com.justbash.Bash;
import com.justbash.BashOptions;
import com.justbash.fs.OverlayFs;
import java.util.Map;
import java.util.Optional;

public class OverlayExample {
    public static void main(String[] args) {
        OverlayFs fs = new OverlayFs(
            new OverlayFs.OverlayFsOptions("/path/to/project")
        );

        BashOptions options = new BashOptions(
            Optional.of(Map.of("MY_VAR", "hello")),
            Optional.empty(),
            Optional.of(fs),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );

        Bash bash = new Bash(options);
        // Scripts can read real files but writes stay in memory
        bash.shutdown();
    }
}
```

### With custom options

```java
import com.justbash.Bash;
import com.justbash.BashOptions;
import com.justbash.fs.InMemoryFs;
import com.justbash.security.ExecutionLimits;
import java.util.Map;
import java.util.Optional;

public class CustomExample {
    public static void main(String[] args) {
        BashOptions options = new BashOptions(
            Optional.of(Map.of("MY_VAR", "hello")),  // environment variables
            Optional.of("/home/user"),                // working directory
            Optional.of(new InMemoryFs()),            // custom filesystem
            Optional.of(ExecutionLimits.defaults()),  // execution limits
            Optional.empty(),                         // network config
            Optional.empty(),                         // python support
            Optional.empty(),                         // javascript support
            Optional.empty()                          // logger
        );

        Bash bash = new Bash(options);
        // ... use bash
        bash.shutdown();
    }
}
```

### Registering custom commands

```java
import com.justbash.Bash;
import com.justbash.Command;
import com.justbash.CommandContext;
import com.justbash.ExecResult;

public class CustomCommand implements Command {
    @Override
    public String name() {
        return "mycmd";
    }

    @Override
    public ExecResult execute(CommandContext ctx) {
        return new ExecResult("Hello from mycmd!\n", "", 0);
    }
}

// Register it
Bash bash = new Bash();
bash.registerCommand(new CustomCommand());
```

## Supported Features

### Language constructs
- Variable assignment and expansion (`foo=bar`, `$foo`, `${foo}`)
- Command substitution (`$(cmd)` and backticks)
- Arithmetic expansion (`$((1 + 2))`)
- Tilde expansion (`~`, `~/dir`)
- Brace expansion (`{a,b,c}`)
- Glob patterns (`*`, `?`, `[...]`)
- Single and double quotes
- Here-documents and here-strings
- Pipelines (`cmd1 | cmd2 | cmd3`)
- Redirections (`>`, `>>`, `<`, `2>`, `&>`, heredocs)
- Background jobs (`cmd &`)
- Subshells (`(cmd1; cmd2)`)
- Command groups (`{ cmd1; cmd2; }`)

### Control flow
- `if` / `then` / `elif` / `else` / `fi`
- `for` loops (`for i in 1 2 3` and C-style `for ((i=0; i<3; i++))`)
- `while` / `until` loops
- `case` statements
- `select` menus
- Function definitions
- `break` / `continue` / `return` / `exit`

### Builtins
- `echo`, `printf`
- `cd`, `pwd`
- `export`, `local`, `declare`, `readonly`, `unset`
- `source` / `.`
- `eval`
- `shift`
- `test` / `[` / `[[`
- `true`, `false`
- `read`
- `set` (partial)
- `trap` (partial)

### External commands
- `cat`, `cp`, `grep`, `head`, `ls`, `mkdir`, `mv`, `printf`, `rm`, `tail`, `touch`, `wc`

## Testing

```bash
# Run all tests
mvn test

# Run tests for a specific module
mvn test -pl just-bash-core
mvn test -pl just-bash-commands
mvn test -pl just-bash-cli

# Run a specific test class
mvn test -pl just-bash-core -Dtest=InterpreterTest
```

## Architecture

The interpreter follows a classic compiler pipeline:

```
Input Script → Lexer → Parser → AST → Interpreter → ExecResult
```

1. **Lexer** (`parser/Lexer.java`): Tokenizes bash source code, handling quotes, heredocs, and expansions
2. **Parser** (`parser/Parser.java`): Recursive descent parser producing an AST
3. **AST** (`ast/`): Immutable node hierarchy representing bash constructs
4. **Interpreter** (`interpreter/Interpreter.java`): Executes AST nodes, manages state, handles builtins
5. **Filesystem** (`fs/InMemoryFs.java`): In-memory virtual filesystem for sandboxed I/O
6. **Commands** (`commands/`): External command implementations registered via `CommandRegistry`

## License

MIT
