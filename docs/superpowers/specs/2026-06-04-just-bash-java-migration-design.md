# just-bash TypeScript → Java 迁移方案

## 概述

将 just-bash（TypeScript 实现的 bash 解释器）迁移到 Java，保持核心公共 API 不变，内部实现采用 Java 现代特性优化。

- **目标 Java 版本**: Java 21 (LTS)
- **迁移范围**: 核心层（Parser + AST + Interpreter + FS）+ 常用外部命令，跳过 WASM 和边缘功能
- **异步模型**: 公共 API 保留 `CompletableFuture`，内部使用 Virtual Threads 同步化

---

## 1. 整体架构与模块划分

### 1.1 多模块项目结构

```
just-bash-java/
├── just-bash-core/                    # 核心引擎
│   └── src/main/java/com/justbash/
│       ├── ast/                       # AST节点（Sealed Interface + Records）
│       ├── parser/                    # 解析器
│       ├── interpreter/               # 解释器
│       │   ├── builtin/               # 内置命令
│       │   └── controlflow/           # 控制流
│       ├── fs/                        # 文件系统抽象
│       ├── security/                  # 安全层（简化版）
│       ├── encoding/                  # 字节/文本编码处理
│       └── Bash.java                  # 主入口类
│
├── just-bash-commands/                # 外部命令实现
│   └── src/main/java/com/justbash/commands/
│       ├── CommandRegistry.java
│       ├── cat/CatCommand.java
│       ├── grep/GrepCommand.java
│       ├── sed/SedCommand.java
│       ├── awk/                       # AWK引擎
│       └── ...
│
└── just-bash-cli/                     # CLI入口（可选）
    └── src/main/java/com/justbash/cli/
        └── JustBashCli.java
```

### 1.2 模块依赖

```
just-bash-cli
    ↓
just-bash-commands
    ↓
just-bash-core
    ↓
JDK 21 (java.base, java.net.http)
```

### 1.3 核心类型映射

| TypeScript 概念 | Java 映射 | 说明 |
|---|---|---|
| `type` / `interface` | `sealed interface` / `record` | AST 节点用 sealed interface |
| Tagged Union (`A \| B \| C`) | `sealed interface` + `record implements` | 编译器安全校验 |
| `Promise<T>` | `CompletableFuture<T>` | 公共 API 保留异步契约 |
| `async/await` (内部) | Virtual Threads + 同步调用 | 内部全同步 |
| `Map<string, string>` | `Map<String, String>` | `LinkedHashMap` 保持顺序 |
| `Record<string, T>` | `Map<String, T>` | Java 无原型链问题 |
| `string` (latin1 bytes) | `ByteString` (自定义类) | 保留字节透明语义 |
| `Uint8Array` | `byte[]` | 直接映射 |
| `AbortSignal` | `CancellationToken` (自定义) | 协作取消 |

---

## 2. AST 设计

### 2.1 基接口

```java
public sealed interface ASTNode permits
    ScriptNode, StatementNode, PipelineNode,
    CommandNode, WordNode, WordPart,
    AssignmentNode, RedirectionNode, HereDocNode,
    IfNode, ForNode, WhileNode, CaseNode, ... {
    String type();
    int line();
}
```

### 2.2 核心节点

**ScriptNode:**
```java
public record ScriptNode(int line, List<StatementNode> statements)
    implements ASTNode {
    @Override public String type() { return "Script"; }
}
```

**StatementNode:**
```java
public record StatementNode(
    int line,
    List<PipelineNode> pipelines,
    List<StatementOperator> operators,
    boolean background,
    Optional<DeferredError> deferredError,
    Optional<String> sourceText
) implements ASTNode {
    @Override public String type() { return "Statement"; }

    public enum StatementOperator { AND, OR, SEMICOLON }
}
```

### 2.3 CommandNode（Tagged Union → Sealed Interface）

```java
public sealed interface CommandNode extends ASTNode
    permits SimpleCommandNode, CompoundCommandNode, FunctionDefNode {}

public sealed interface CompoundCommandNode extends CommandNode
    permits IfNode, ForNode, WhileNode, UntilNode,
            CaseNode, SubshellNode, GroupNode,
            ArithmeticCommandNode, ConditionalCommandNode {}
```

### 2.4 WordPart（最复杂的 Tagged Union）

```java
public sealed interface WordPart extends ASTNode
    permits LiteralPart, SingleQuotedPart, DoubleQuotedPart,
            EscapedPart, ParameterExpansionPart,
            CommandSubstitutionPart, ArithmeticExpansionPart,
            ProcessSubstitutionPart, BraceExpansionPart,
            TildeExpansionPart, GlobPart {}

public record LiteralPart(int line, String value) implements WordPart {
    @Override public String type() { return "Literal"; }
}

public record ParameterExpansionPart(
    int line, String parameter,
    Optional<ParameterOperation> operation
) implements WordPart {
    @Override public String type() { return "ParameterExpansion"; }
}
```

### 2.5 工厂类

```java
public final class AST {
    private AST() {}

    public static ScriptNode script(List<StatementNode> statements) {
        return new ScriptNode(0, List.copyOf(statements));
    }

    public static WordNode word(WordPart... parts) {
        return new WordNode(0, List.of(parts));
    }

    public static LiteralPart literal(String value) {
        return new LiteralPart(0, value);
    }
    // ... 其他工厂方法
}
```

### 2.6 模式匹配

```java
switch (commandNode) {
    case SimpleCommandNode sc -> {
        WordNode name = sc.name();
        List<WordNode> args = sc.args();
    }
    case IfNode ifNode -> {
        List<IfClause> clauses = ifNode.clauses();
    }
    case FunctionDefNode fn -> { /* ... */ }
    default -> throw new IllegalStateException();
}
```

---

## 3. 核心公共 API 层

### 3.1 ExecResult / BashExecResult

```java
public record ExecResult(
    String stdout, String stderr, int exitCode,
    Optional<StdoutKind> stdoutKind
) {
    public enum StdoutKind { TEXT, BYTES }
    public ExecResult(String stdout, String stderr, int exitCode) {
        this(stdout, stderr, exitCode, Optional.empty());
    }
}

public record BashExecResult(
    String stdout, String stderr, int exitCode,
    Optional<StdoutKind> stdoutKind,
    Map<String, String> env,
    Optional<Map<String, Object>> metadata
) {
    public BashExecResult(String stdout, String stderr, int exitCode,
                          Map<String, String> env) {
        this(stdout, stderr, exitCode, Optional.empty(),
             Map.copyOf(env), Optional.empty());
    }
}
```

### 3.2 ByteString

```java
public final class ByteString {
    private final String internal; // latin1-encoded

    private ByteString(String internal) { this.internal = internal; }

    public static ByteString fromLatin1(String latin1) {
        return new ByteString(latin1);
    }

    public static ByteString fromUtf8Bytes(byte[] bytes) {
        char[] latin1 = new char[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            latin1[i] = (char) (bytes[i] & 0xFF);
        }
        return new ByteString(new String(latin1));
    }

    public byte[] toBytes() {
        byte[] bytes = new byte[internal.length()];
        for (int i = 0; i < internal.length(); i++) {
            bytes[i] = (byte) internal.charAt(i);
        }
        return bytes;
    }

    public String decodeUtf8() {
        return new String(toBytes(), StandardCharsets.UTF_8);
    }

    @Override public String toString() {
        throw new UnsupportedOperationException(
            "Use decodeUtf8() or asLatin1() explicitly");
    }
}
```

### 3.3 IFileSystem 接口

```java
public interface IFileSystem {
    CompletableFuture<String> readFile(String path, ReadFileOptions options);
    CompletableFuture<String> readFile(String path);
    CompletableFuture<ByteString> readFileBytes(String path);
    CompletableFuture<byte[]> readFileBuffer(String path);
    CompletableFuture<Void> writeFile(String path, FileContent content,
                                       WriteFileOptions options);
    CompletableFuture<Void> appendFile(String path, FileContent content,
                                        WriteFileOptions options);
    CompletableFuture<Boolean> exists(String path);
    CompletableFuture<FsStat> stat(String path);
    CompletableFuture<Void> mkdir(String path, MkdirOptions options);
    CompletableFuture<List<String>> readdir(String path);
    CompletableFuture<List<DirentEntry>> readdirWithFileTypes(String path);
    CompletableFuture<Void> rm(String path, RmOptions options);
    CompletableFuture<Void> cp(String src, String dest, CpOptions options);
    CompletableFuture<Void> mv(String src, String dest);
    String resolvePath(String base, String path);
    List<String> getAllPaths();
    CompletableFuture<Void> chmod(String path, int mode);
    CompletableFuture<Void> symlink(String target, String linkPath);
    CompletableFuture<Void> link(String existingPath, String newPath);
    CompletableFuture<String> readlink(String path);
    CompletableFuture<FsStat> lstat(String path);
    CompletableFuture<String> realpath(String path);
    CompletableFuture<Void> utimes(String path, Instant atime, Instant mtime);

    sealed interface FileContent permits StringContent, ByteArrayContent {}
    record StringContent(String value) implements FileContent {}
    record ByteArrayContent(byte[] value) implements FileContent {}
}
```

### 3.4 Bash 主类

```java
public final class Bash {
    private final IFileSystem fs;
    private final Map<String, Command> commands = new LinkedHashMap<>();
    private final InterpreterState state;
    private final ExecutionLimits limits;
    private final ExecutorService virtualThreadExecutor;
    private final Optional<SecureFetch> secureFetch;
    private final Optional<BashLogger> logger;

    public Bash() { this(BashOptions.defaults()); }

    public Bash(BashOptions options) {
        this.fs = options.fs().orElseGet(() -> new InMemoryFs(options.files()));
        this.limits = options.executionLimits().orElse(ExecutionLimits.defaults());
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.secureFetch = options.networkConfig()
            .map(NetworkConfig::createSecureFetch);
        this.logger = options.logger();
        this.state = initializeState(options);
        registerBuiltInCommands();
    }

    public CompletableFuture<BashExecResult> exec(String commandLine) {
        return exec(commandLine, ExecOptions.defaults());
    }

    public CompletableFuture<BashExecResult> exec(String commandLine,
                                                   ExecOptions options) {
        return CompletableFuture.supplyAsync(() -> {
            try { return executeSync(commandLine, options); }
            catch (Exception e) { throw new RuntimeException(e); }
        }, virtualThreadExecutor);
    }

    private BashExecResult executeSync(String commandLine, ExecOptions options) {
        ScriptNode ast = Parser.parse(commandLine,
            ParserOptions.defaults()
                .withMaxHeredocSize(limits.maxHeredocSize()));

        Interpreter interpreter = new Interpreter(
            new InterpreterOptions(fs, commands, limits, this::exec, secureFetch),
            createExecState(options)
        );

        BashExecResult result = interpreter.executeScript(ast);
        return logResult(result);
    }

    public CompletableFuture<String> readFile(String path) {
        return fs.readFile(fs.resolvePath(state.cwd(), path));
    }

    public String getCwd() { return state.cwd(); }
    public Map<String, String> getEnv() { return Map.copyOf(state.env()); }

    public void registerCommand(Command command) {
        commands.put(command.name(), command);
        createCommandStub(command.name());
    }
}
```

---

## 4. 解释器执行模型与 Virtual Threads

### 4.1 核心设计

TypeScript 中所有执行路径都是 `async/await`，Java 中在 Virtual Thread 内全同步化：

```java
// TypeScript
async executeScript(node: ScriptNode): Promise<ExecResult> {
    for (const statement of node.statements) {
        const result = await this.executeStatement(statement);
        stdout += result.stdout;
    }
}

// Java
public BashExecResult executeScript(ScriptNode node) {
    StringBuilder stdout = new StringBuilder();
    StringBuilder stderr = new StringBuilder();
    for (StatementNode statement : node.statements()) {
        ExecResult result = executeStatement(statement);
        stdout.append(result.stdout());
        stderr.append(result.stderr());
    }
    return new BashExecResult(stdout.toString(), stderr.toString(),
                              exitCode, state.getEnvMap());
}
```

### 4.2 PipelineExecutor（并发管道）

```java
public class PipelineExecutor {
    private final ExecutorService executor =
        Executors.newVirtualThreadPerTaskExecutor();

    public ExecResult executePipeline(
            PipelineNode node,
            BiFunction<CommandNode, String, ExecResult> executeCommand) {

        if (node.commands().size() == 1) {
            return executeCommand.apply(node.commands().get(0), "");
        }

        List<BlockingQueue<String>> pipes = new ArrayList<>();
        for (int i = 0; i < node.commands().size() - 1; i++) {
            pipes.add(new LinkedBlockingQueue<>());
        }

        List<Future<ExecResult>> futures = new ArrayList<>();
        for (int i = 0; i < node.commands().size(); i++) {
            final int idx = i;
            final boolean isLast = (i == node.commands().size() - 1);

            Future<ExecResult> future = executor.submit(() -> {
                String stdin = (idx == 0) ? "" : drainPipe(pipes.get(idx - 1));
                ExecResult result = executeCommand.apply(
                    node.commands().get(idx), stdin);
                if (!isLast) feedPipe(pipes.get(idx), result.stdout());
                return result;
            });
            futures.add(future);
        }

        List<ExecResult> results = futures.stream()
            .map(f -> { try { return f.get(); }
                        catch (Exception e) { throw new RuntimeException(e); }})
            .toList();

        return computePipelineResult(node, results);
    }
}
```

### 4.3 子命令递归

```java
private BashExecResult execSubcommand(String script, ExecOptions options) {
    try {
        return execFn.apply(script, options).join();
    } catch (CompletionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof RuntimeException re) throw re;
        throw new RuntimeException(cause);
    }
}
```

---

## 5. 文件系统与异常体系

### 5.1 InMemoryFs

```java
public class InMemoryFs implements IFileSystem {
    private final Map<String, FsEntry> data = new HashMap<>();

    public InMemoryFs() { this(null); }

    public InMemoryFs(Map<String, ? extends InitialFile> initialFiles) {
        data.put("/", new DirectoryEntry(0o755, Instant.now()));
        if (initialFiles != null) {
            for (Map.Entry<String, ? extends InitialFile> e : initialFiles.entrySet()) {
                writeFileSync(e.getKey(), e.getValue());
            }
        }
    }

    @Override
    public CompletableFuture<String> readFile(String path, ReadFileOptions options) {
        return CompletableFuture.supplyAsync(() -> readFileSync(path, options));
    }

    // 同步内部方法（Virtual Thread 中直接调用）
    public String readFileSync(String path, ReadFileOptions options) {
        String normalized = normalizePath(path);
        FsEntry entry = data.get(normalized);
        if (entry == null) {
            throw new FsException("ENOENT: no such file or directory: " + path);
        }
        if (entry instanceof DirectoryEntry) {
            throw new FsException("EISDIR: illegal operation on a directory");
        }
        return ((FileEntry) entry).content();
    }

    public void writeFileSync(String path, FileContent content,
                               WriteFileOptions options) {
        String normalized = normalizePath(path);
        ensureParentDirs(normalized);
        String textContent = switch (content) {
            case IFileSystem.StringContent sc -> sc.value();
            case IFileSystem.ByteArrayContent bc -> {
                char[] chars = new char[bc.value().length];
                for (int i = 0; i < bc.value().length; i++) {
                    chars[i] = (char) (bc.value()[i] & 0xFF);
                }
                yield new String(chars);
            }
        };
        data.put(normalized, new FileEntry(textContent, 0o644, Instant.now()));
    }

    public sealed interface FsEntry permits FileEntry, DirectoryEntry, SymlinkEntry {
        int mode();
        Instant mtime();
    }
    public record FileEntry(String content, int mode, Instant mtime) implements FsEntry {}
    public record DirectoryEntry(int mode, Instant mtime) implements FsEntry {}
    public record SymlinkEntry(String target, int mode, Instant mtime) implements FsEntry {}
}
```

### 5.2 OverlayFs 概要

OverlayFs 较复杂，核心思想是**内存层 + 真实文件系统层**：

```java
public class OverlayFs implements IFileSystem {
    private final InMemoryFs memoryLayer;
    private final java.nio.file.Path realRoot;
    private final boolean allowSymlinks;
    // read: 先查 memoryLayer，不存在则查 realRoot
    // write: 只写入 memoryLayer
    // 路径验证：比较 realPath 和 canonicalPath 防 symlink 攻击
}
```

### 5.3 异常体系

```java
public sealed class BashException extends RuntimeException
    permits ParseException, LexerException, ExecutionException,
            SecurityException, ArithmeticException {}

public final class ParseException extends BashException {
    private final int line;
    private final int column;
    public ParseException(String message, int line, int column) {
        super(message);
        this.line = line;
        this.column = column;
    }
}

public sealed class ExecutionException extends BashException
    permits ExitException, ErrexitException, ReturnException,
            BreakException, ContinueException, ExecutionLimitException,
            ExecutionAbortedException, BadSubstitutionException {}

public final class ExitException extends ExecutionException {
    private final int exitCode;
    private final String stdout;
    private final String stderr;
}

public final class ReturnException extends ExecutionException {
    private final int exitCode;
}

public final class ExecutionLimitException extends ExecutionException {
    public static final int EXIT_CODE = 125;
}
```

### 5.4 异常处理（Pattern Matching for switch）

```java
private BashExecResult handleExecutionError(BashException error,
                                             Map<String, String> env) {
    return switch (error) {
        case ExitException e -> new BashExecResult(
            e.stdout(), e.stderr(), e.exitCode(), env);
        case ExecutionLimitException e -> new BashExecResult(
            "", "bash: " + e.getMessage() + "\n",
            ExecutionLimitException.EXIT_CODE, env);
        case ParseException e -> new BashExecResult(
            "", "bash: syntax error: " + e.getMessage() + "\n", 2, env);
        case ExecutionAbortedException e -> new BashExecResult(
            e.stdout(), e.stderr(), 124, env);
        default -> throw error;
    };
}
```

---

## 6. 迁移计划

### 6.1 阶段划分

| 阶段 | 内容 | 预估时间 |
|---|---|---|
| Phase 1 | 基础设施 + AST 类型 | 2-3 周 |
| Phase 2 | 解析器 Parser | 2-3 周 |
| Phase 3 | 解释器核心 | 3-4 周 |
| Phase 4 | 文件系统 + 内置命令 | 2 周 |
| Phase 5 | 外部命令（逐个迁移） | 4-6 周 |
| Phase 6 | CLI + 集成测试 + 性能调优 | 2 周 |

### 6.2 Phase 1: 基础设施 + AST

- Maven/Gradle 多模块项目初始化
- 所有 AST 节点（Sealed Interface + Records）
- `ByteString` 类
- `AST` 工厂类
- AST 节点单元测试

**风险**: AST 节点约 50 个类型，optional 字段映射需仔细核对。建议用脚本自动生成骨架。

### 6.3 Phase 2: 解析器

- `Lexer`: Tokenizer（约 800 行 TS）
- `Parser`: 主解析器（约 600 行）
- `CommandParser`, `CompoundParser`, `ExpansionParser`
- `ArithmeticParser`, `ConditionalParser`
- 解析器对比测试

### 6.4 Phase 3: 解释器核心

- `Interpreter`: 主执行循环
- `ExpansionEngine`: 单词展开
- `ArithmeticEvaluator`: 算术求值
- `ConditionalEvaluator`: 条件求值
- `PipelineExecutor`: 管道执行（Virtual Threads 并发）
- `RedirectionEngine`: I/O 重定向
- `ControlFlow`: 控制流执行
- `BuiltinDispatcher`: 内置命令分发

**InterpreterState 设计**:
```java
public record InterpreterState(
    AtomicReference<Map<String, String>> env,
    AtomicReference<String> cwd,
    AtomicReference<Map<String, FunctionDefNode>> functions,
    AtomicInteger callDepth,
    AtomicInteger commandCount,
    AtomicInteger lastExitCode,
    // ...
) {
    public InterpreterState copy() {
        return new InterpreterState(
            new AtomicReference<>(Map.copyOf(env.get())),
            new AtomicReference<>(cwd.get()),
            // ...
        );
    }
}
```

### 6.5 Phase 4: 文件系统 + 内置命令

文件系统:
- `IFileSystem` 接口
- `InMemoryFs` 实现
- `OverlayFs` 实现
- 路径工具类

内置命令（约 30 个）:
| 命令 | 复杂度 |
|---|---|
| `cd`, `pwd`, `exit`, `return` | 低 |
| `export`, `unset`, `local`, `declare` | 中 |
| `read`, `eval`, `source` | 中 |
| `set`, `shopt` | 高 |

### 6.6 Phase 5: 外部命令（按优先级）

**Tier 1（核心，先实现）**:
- `echo`, `cat`, `printf`
- `ls`, `mkdir`, `rm`, `cp`, `mv`, `touch`
- `head`, `tail`, `wc`
- `grep`, `sed`, `awk`
- `find`

**Tier 2（常用）**:
- `sort`, `uniq`, `cut`, `paste`, `tr`
- `diff`, `date`, `sleep`, `seq`
- `env`, `which`, `basename`, `dirname`
- `xargs`

**Tier 3（可选/替换）**:
- `jq` → Java JSON 库
- `yq` → SnakeYAML
- `tar`, `gzip` → Apache Commons Compress
- `sqlite3` → JDBC/SQLite
- `curl` → Java HttpClient
- `python3`, `js-exec` → **建议移除或替换为 Java-native 方案**

**AWK/SED/GREP 策略**: 核心引擎直译（行为兼容性要求最高），正则引擎用 `java.util.regex`。

### 6.7 Phase 6: CLI + 测试 + 调优

- CLI 入口类
- JUnit 5 测试框架
- 对比测试框架（TS 输出 vs Java 输出）
- Spec 测试迁移
- Virtual Threads 性能调优

---

## 7. 风险与应对

| 风险 | 可能性 | 影响 | 应对 |
|---|---|---|---|
| AST 类型映射遗漏/错误 | 中 | 高 | 脚本自动生成骨架，人工 review |
| async/await → Virtual Threads 行为差异 | 中 | 高 | 大量对比测试 |
| 正则表达式行为差异 | 高 | 中 | grep/sed/awk 单独验证 |
| 性能退化 | 低 | 中 | JMH 基准测试 |

---

## 8. 测试策略

1. **单元测试**: 每个 Java 类对应一个 `*Test.java`
2. **对比测试**: 与 TypeScript 版本运行相同输入，比较输出
3. **Spec 测试**: 迁移 bash conformance test suite

---

## 9. 设计决策汇总

| 设计层面 | TypeScript | Java |
|---|---|---|
| AST | Tagged Union | Sealed Interface + Record |
| 工厂 | 对象字面量 | 静态工厂方法 |
| 模式匹配 | `if (node.type === "X")` | `switch` pattern matching |
| 公共 API | `Promise<BashExecResult>` | `CompletableFuture<BashExecResult>` |
| 内部执行 | async/await | Virtual Threads + 同步 |
| Pipeline | 串行 await | BlockingQueue + Future 并发 |
| 文件系统 | async | CompletableFuture |
| 环境变量 | `Map<string, string>` | `LinkedHashMap<String, String>` |
| 字节缓冲 | latin1 `string` | `ByteString` |
| 错误处理 | 自定义 Error | Sealed Exception + Pattern Matching |
| 取消 | `AbortSignal` | `CancellationToken` |
