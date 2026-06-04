package com.justbash.interpreter;

import static org.junit.jupiter.api.Assertions.*;

import com.justbash.BashExecResult;
import com.justbash.Command;
import com.justbash.ExecOptions;
import com.justbash.TraceEvent;
import com.justbash.encoding.ByteString;
import com.justbash.fs.*;
import com.justbash.security.ExecutionLimits;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class InterpreterOptionsTest {

    private static final IFileSystem stubFs = new IFileSystem() {
        @Override public CompletableFuture<String> readFile(String path, ReadFileOptions options) { return null; }
        @Override public CompletableFuture<ByteString> readFileBytes(String path) { return null; }
        @Override public CompletableFuture<byte[]> readFileBuffer(String path) { return null; }
        @Override public CompletableFuture<Void> writeFile(String path, FileContent content, WriteFileOptions options) { return null; }
        @Override public CompletableFuture<Void> appendFile(String path, FileContent content, WriteFileOptions options) { return null; }
        @Override public CompletableFuture<Boolean> exists(String path) { return null; }
        @Override public CompletableFuture<FsStat> stat(String path) { return null; }
        @Override public CompletableFuture<Void> mkdir(String path, MkdirOptions options) { return null; }
        @Override public CompletableFuture<List<String>> readdir(String path) { return null; }
        @Override public CompletableFuture<List<DirentEntry>> readdirWithFileTypes(String path) { return null; }
        @Override public CompletableFuture<Void> rm(String path, RmOptions options) { return null; }
        @Override public CompletableFuture<Void> cp(String src, String dest, CpOptions options) { return null; }
        @Override public CompletableFuture<Void> mv(String src, String dest) { return null; }
        @Override public String resolvePath(String base, String path) { return null; }
        @Override public List<String> getAllPaths() { return null; }
        @Override public CompletableFuture<Void> chmod(String path, int mode) { return null; }
        @Override public CompletableFuture<Void> symlink(String target, String linkPath) { return null; }
        @Override public CompletableFuture<Void> link(String existingPath, String newPath) { return null; }
        @Override public CompletableFuture<String> readlink(String path) { return null; }
        @Override public CompletableFuture<FsStat> lstat(String path) { return null; }
        @Override public CompletableFuture<String> realpath(String path) { return null; }
        @Override public CompletableFuture<Void> utimes(String path, Instant atime, Instant mtime) { return null; }
    };

    private final Map<String, Command> mockCommands = Map.of();
    private final ExecutionLimits limits = new ExecutionLimits(10, 100, 1000, 1024, 512);
    private final BiFunction<String, ExecOptions, CompletableFuture<BashExecResult>> mockExec =
            (script, opts) -> CompletableFuture.completedFuture(
                    new BashExecResult("", "", 0, Map.of()));

    @Test
    void constructionWithRequiredFields() {
        InterpreterOptions opts = new InterpreterOptions(stubFs, mockCommands, limits, mockExec);

        assertSame(stubFs, opts.fs());
        assertSame(mockCommands, opts.commands());
        assertSame(limits, opts.limits());
        assertSame(mockExec, opts.exec());
    }

    @Test
    void defaultsAreCorrect() {
        InterpreterOptions opts = new InterpreterOptions(stubFs, mockCommands, limits, mockExec);

        assertEquals(Optional.empty(), opts.trace());
        assertEquals(Optional.empty(), opts.sleep());
        assertFalse(opts.requireDefenseContext());
        assertEquals(Optional.empty(), opts.jsBootstrapCode());
        assertEquals(Optional.empty(), opts.invokeTool());
    }

    @Test
    void builderStyleSetters() {
        Consumer<TraceEvent> traceConsumer = event -> {};
        Function<Long, CompletableFuture<Void>> sleepFn = ms -> CompletableFuture.completedFuture(null);
        BiFunction<String, String, CompletableFuture<String>> invokeToolFn = (path, args) -> CompletableFuture.completedFuture("");

        InterpreterOptions opts = new InterpreterOptions(stubFs, mockCommands, limits, mockExec)
                .withTrace(traceConsumer)
                .withSleep(sleepFn)
                .withRequireDefenseContext(true)
                .withJsBootstrapCode("console.log('hello')")
                .withInvokeTool(invokeToolFn);

        assertTrue(opts.trace().isPresent());
        assertSame(traceConsumer, opts.trace().get());

        assertTrue(opts.sleep().isPresent());
        assertSame(sleepFn, opts.sleep().get());

        assertTrue(opts.requireDefenseContext());

        assertTrue(opts.jsBootstrapCode().isPresent());
        assertEquals("console.log('hello')", opts.jsBootstrapCode().get());

        assertTrue(opts.invokeTool().isPresent());
        assertSame(invokeToolFn, opts.invokeTool().get());
    }

    @Test
    void builderStyleSettersReturnSameInstance() {
        InterpreterOptions base = new InterpreterOptions(stubFs, mockCommands, limits, mockExec);

        assertSame(base, base.withTrace(event -> {}));
        assertSame(base, base.withSleep(ms -> CompletableFuture.completedFuture(null)));
        assertSame(base, base.withRequireDefenseContext(true));
        assertSame(base, base.withJsBootstrapCode("code"));
        assertSame(base, base.withInvokeTool((p, a) -> CompletableFuture.completedFuture("")));
    }

    @Test
    void nullValuesAreHandled() {
        InterpreterOptions opts = new InterpreterOptions(stubFs, mockCommands, limits, mockExec)
                .withTrace(null)
                .withSleep(null)
                .withJsBootstrapCode(null)
                .withInvokeTool(null);

        assertEquals(Optional.empty(), opts.trace());
        assertEquals(Optional.empty(), opts.sleep());
        assertEquals(Optional.empty(), opts.jsBootstrapCode());
        assertEquals(Optional.empty(), opts.invokeTool());
    }
}
