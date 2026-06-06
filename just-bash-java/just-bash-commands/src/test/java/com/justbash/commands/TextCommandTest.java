package com.justbash.commands;

import com.justbash.Bash;
import com.justbash.BashExecResult;
import com.justbash.BashOptions;
import com.justbash.ExecResult;
import com.justbash.commands.cat.CatCommand;
import com.justbash.commands.column.ColumnCommand;
import com.justbash.commands.comm.CommCommand;
import com.justbash.commands.cut.CutCommand;
import com.justbash.commands.diff.DiffCommand;
import com.justbash.commands.expr.ExprCommand;
import com.justbash.commands.file.FileCommand;
import com.justbash.commands.fold.FoldCommand;
import com.justbash.commands.head.HeadCommand;
import com.justbash.commands.join.JoinCommand;
import com.justbash.commands.jq.JqCommand;
import com.justbash.commands.ls.LsCommand;
import com.justbash.commands.nl.NlCommand;
import com.justbash.commands.paste.PasteCommand;
import com.justbash.commands.printf.PrintfCommand;
import com.justbash.commands.rev.RevCommand;
import com.justbash.commands.rg.RgCommand;
import com.justbash.commands.seq.SeqCommand;
import com.justbash.commands.sort.SortCommand;
import com.justbash.commands.split.SplitCommand;
import com.justbash.commands.tail.TailCommand;
import com.justbash.commands.tr.TrCommand;
import com.justbash.commands.tree.TreeCommand;
import com.justbash.commands.uniq.UniqCommand;
import com.justbash.commands.wc.WcCommand;
import com.justbash.commands.xargs.XargsCommand;
import com.justbash.fs.IFileSystem;
import com.justbash.fs.InMemoryFs;
import com.justbash.fs.MkdirOptions;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TextCommandTest {

    private Bash createBash() {
        InMemoryFs fs = new InMemoryFs();
        Bash bash = new Bash(new BashOptions(
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.of(fs), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty()
        ));
        bash.registerCommand(new CatCommand());
        bash.registerCommand(new ColumnCommand());
        bash.registerCommand(new CommCommand());
        bash.registerCommand(new CutCommand());
        bash.registerCommand(new DiffCommand());
        bash.registerCommand(new ExprCommand());
        bash.registerCommand(new FileCommand());
        bash.registerCommand(new FoldCommand());
        bash.registerCommand(new HeadCommand());
        bash.registerCommand(new JoinCommand());
        bash.registerCommand(new JqCommand());
        bash.registerCommand(new LsCommand());
        bash.registerCommand(new NlCommand());
        bash.registerCommand(new PasteCommand());
        bash.registerCommand(new PrintfCommand());
        bash.registerCommand(new RevCommand());
        bash.registerCommand(new RgCommand());
        bash.registerCommand(new SeqCommand());
        bash.registerCommand(new SortCommand());
        bash.registerCommand(new SplitCommand());
        bash.registerCommand(new TailCommand());
        bash.registerCommand(new TrCommand());
        bash.registerCommand(new TreeCommand());
        bash.registerCommand(new UniqCommand());
        bash.registerCommand(new WcCommand());
        bash.registerCommand(new XargsCommand());
        return bash;
    }

    private Bash createBashWithFile(String path, String content) {
        InMemoryFs fs = new InMemoryFs();
        fs.writeFile(path, new IFileSystem.StringContent(content)).join();
        Bash bash = new Bash(new BashOptions(
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.of(fs), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty()
        ));
        bash.registerCommand(new CatCommand());
        bash.registerCommand(new ColumnCommand());
        bash.registerCommand(new CommCommand());
        bash.registerCommand(new CutCommand());
        bash.registerCommand(new DiffCommand());
        bash.registerCommand(new ExprCommand());
        bash.registerCommand(new FileCommand());
        bash.registerCommand(new FoldCommand());
        bash.registerCommand(new HeadCommand());
        bash.registerCommand(new JoinCommand());
        bash.registerCommand(new JqCommand());
        bash.registerCommand(new LsCommand());
        bash.registerCommand(new NlCommand());
        bash.registerCommand(new PasteCommand());
        bash.registerCommand(new PrintfCommand());
        bash.registerCommand(new RevCommand());
        bash.registerCommand(new RgCommand());
        bash.registerCommand(new SeqCommand());
        bash.registerCommand(new SortCommand());
        bash.registerCommand(new SplitCommand());
        bash.registerCommand(new TailCommand());
        bash.registerCommand(new TrCommand());
        bash.registerCommand(new TreeCommand());
        bash.registerCommand(new UniqCommand());
        bash.registerCommand(new WcCommand());
        bash.registerCommand(new XargsCommand());
        return bash;
    }

    @Test
    void printfSimple() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("printf %s hello").join();
        assertThat(result.stdout()).isEqualTo("hello");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void printfMultipleArgs() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("printf %s-%s hello world").join();
        assertThat(result.stdout()).isEqualTo("hello-world");
        bash.shutdown();
    }

    @Test
    void printfNumber() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("printf %d 42").join();
        assertThat(result.stdout()).isEqualTo("42");
        bash.shutdown();
    }

    @Test
    void printfNewline() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("printf line1\\nline2\\n").join();
        assertThat(result.stdout()).isEqualTo("line1\nline2\n");
        bash.shutdown();
    }

    @Test
    void printfHex() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("printf %x 255").join();
        assertThat(result.stdout()).isEqualTo("ff");
        bash.shutdown();
    }

    @Test
    void printfOctal() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("printf %o 8").join();
        assertThat(result.stdout()).isEqualTo("10");
        bash.shutdown();
    }

    @Test
    void printfChar() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("printf %c abc").join();
        assertThat(result.stdout()).isEqualTo("a");
        bash.shutdown();
    }

    @Test
    void printfPercentEscape() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("printf %%").join();
        assertThat(result.stdout()).isEqualTo("%");
        bash.shutdown();
    }

    @Test
    void printfNoArgs() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("printf").join();
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.stderr()).contains("usage");
        bash.shutdown();
    }

    @Test
    void lsCurrentDirectory() {
        InMemoryFs fs = new InMemoryFs();
        fs.mkdir("/home/user", new MkdirOptions(true)).join();
        Bash bash = new Bash(new BashOptions(
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.of(fs), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty()
        ));
        bash.registerCommand(new LsCommand());
        BashExecResult result = bash.exec("ls").join();
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void lsListsFile() {
        InMemoryFs fs = new InMemoryFs();
        fs.mkdir("/tmp", MkdirOptions.defaults()).join();
        fs.writeFile("/tmp/lsfile", new IFileSystem.StringContent("content")).join();
        fs.mkdir("/tmp/lsdir", MkdirOptions.defaults()).join();
        Bash bash = new Bash(new BashOptions(
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.of(fs), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty()
        ));
        bash.registerCommand(new LsCommand());
        BashExecResult result = bash.exec("ls /tmp").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).contains("lsfile");
        assertThat(result.stdout()).contains("lsdir");
        bash.shutdown();
    }

    @Test
    void lsLongFormat() {
        Bash bash = createBashWithFile("/tmp/lsfile", "content");
        BashExecResult result = bash.exec("ls -l /tmp/lsfile").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).contains("-rw-r--r--");
        assertThat(result.stdout()).contains("lsfile");
        bash.shutdown();
    }

    @Test
    void lsAllFiles() {
        InMemoryFs fs = new InMemoryFs();
        fs.mkdir("/tmp/lsall", MkdirOptions.defaults()).join();
        fs.writeFile("/tmp/lsall/.hidden", new IFileSystem.StringContent("")).join();
        fs.writeFile("/tmp/lsall/visible", new IFileSystem.StringContent("")).join();
        Bash bash = new Bash(new BashOptions(
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.of(fs), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty()
        ));
        bash.registerCommand(new LsCommand());
        BashExecResult result = bash.exec("ls -a /tmp/lsall").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).contains(".hidden");
        assertThat(result.stdout()).contains("visible");
        bash.shutdown();
    }

    @Test
    void lsLaCombined() {
        InMemoryFs fs = new InMemoryFs();
        fs.mkdir("/tmp/lsla", MkdirOptions.defaults()).join();
        fs.writeFile("/tmp/lsla/.hidden", new IFileSystem.StringContent("")).join();
        Bash bash = new Bash(new BashOptions(
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.of(fs), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty()
        ));
        bash.registerCommand(new LsCommand());
        BashExecResult result = bash.exec("ls -la /tmp/lsla").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).contains(".hidden");
        bash.shutdown();
    }

    @Test
    void lsNonExistent() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("ls /nonexistent").join();
        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.stderr()).contains("No such file or directory");
        bash.shutdown();
    }

    @Test
    void headDefaultLines() {
        Bash bash = createBashWithFile("/tmp/headtest",
            "line1\nline2\nline3\nline4\nline5\nline6\nline7\nline8\nline9\nline10\nline11\n");
        BashExecResult result = bash.exec("head /tmp/headtest").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout().split("\n").length).isEqualTo(10);
        bash.shutdown();
    }

    @Test
    void headNOption() {
        Bash bash = createBashWithFile("/tmp/headn", "a\nb\nc\nd\n");
        BashExecResult result = bash.exec("head -n 2 /tmp/headn").join();
        assertThat(result.stdout()).isEqualTo("a\nb\n");
        bash.shutdown();
    }

    @Test
    void headFileNotFound() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("head /nonexistent").join();
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.stderr()).contains("No such file or directory");
        bash.shutdown();
    }

    @Test
    void tailDefaultLines() {
        Bash bash = createBashWithFile("/tmp/tailtest",
            "line1\nline2\nline3\nline4\nline5\nline6\nline7\nline8\nline9\nline10\nline11\n");
        BashExecResult result = bash.exec("tail /tmp/tailtest").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout().split("\n").length).isEqualTo(10);
        bash.shutdown();
    }

    @Test
    void tailNOption() {
        Bash bash = createBashWithFile("/tmp/tailn", "a\nb\nc\nd\n");
        BashExecResult result = bash.exec("tail -n 2 /tmp/tailn").join();
        assertThat(result.stdout()).isEqualTo("c\nd\n");
        bash.shutdown();
    }

    @Test
    void tailFileNotFound() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("tail /nonexistent").join();
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.stderr()).contains("No such file or directory");
        bash.shutdown();
    }

    @Test
    void wcFile() {
        Bash bash = createBashWithFile("/tmp/wctest", "hello world\nsecond line\n");
        BashExecResult result = bash.exec("wc /tmp/wctest").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).contains("2");
        assertThat(result.stdout()).contains("4");
        bash.shutdown();
    }

    @Test
    void wcLinesOnly() {
        Bash bash = createBashWithFile("/tmp/wclines", "line1\nline2\nline3\n");
        BashExecResult result = bash.exec("wc -l /tmp/wclines").join();
        assertThat(result.stdout().trim()).isEqualTo("3 /tmp/wclines");
        bash.shutdown();
    }

    @Test
    void wcWordsOnly() {
        Bash bash = createBashWithFile("/tmp/wcwords", "one two three\n");
        BashExecResult result = bash.exec("wc -w /tmp/wcwords").join();
        assertThat(result.stdout().trim()).isEqualTo("3 /tmp/wcwords");
        bash.shutdown();
    }

    @Test
    void wcBytesOnly() {
        Bash bash = createBashWithFile("/tmp/wcbytes", "hello\n");
        BashExecResult result = bash.exec("wc -c /tmp/wcbytes").join();
        assertThat(result.stdout().trim()).startsWith("6");
        bash.shutdown();
    }

    @Test
    void wcMultipleFiles() {
        InMemoryFs fs = new InMemoryFs();
        fs.writeFile("/tmp/wc1", new IFileSystem.StringContent("a b\n")).join();
        fs.writeFile("/tmp/wc2", new IFileSystem.StringContent("x y z\n")).join();
        Bash bash = new Bash(new BashOptions(
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.of(fs), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty()
        ));
        bash.registerCommand(new WcCommand());
        BashExecResult result = bash.exec("wc /tmp/wc1 /tmp/wc2").join();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).contains("total");
        bash.shutdown();
    }

    @Test
    void wcFileNotFound() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("wc /nonexistent").join();
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.stderr()).contains("No such file or directory");
        bash.shutdown();
    }

    // ========== cut ==========

    @Test
    void cutFieldMode() {
        Bash bash = createBashWithFile("/tmp/cut1", "a,b,c\n");
        BashExecResult result = bash.exec("cut -d, -f2 /tmp/cut1").join();
        assertThat(result.stdout()).isEqualTo("b\n");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void cutFieldRange() {
        Bash bash = createBashWithFile("/tmp/cut2", "a,b,c,d\n");
        BashExecResult result = bash.exec("cut -d, -f1-3 /tmp/cut2").join();
        assertThat(result.stdout()).isEqualTo("a,b,c\n");
        bash.shutdown();
    }

    @Test
    void cutCharMode() {
        Bash bash = createBashWithFile("/tmp/cut3", "hello\n");
        BashExecResult result = bash.exec("cut -c1-3 /tmp/cut3").join();
        assertThat(result.stdout()).isEqualTo("hel\n");
        bash.shutdown();
    }

    @Test
    void cutSuppressNoDelim() {
        Bash bash = createBashWithFile("/tmp/cut4", "a,b\nno-delim\n");
        BashExecResult result = bash.exec("cut -d, -f1 -s /tmp/cut4").join();
        assertThat(result.stdout()).isEqualTo("a\n");
        bash.shutdown();
    }

    // ========== tr ==========
    // tr reads from stdin; we test the command directly since bash stdin
    // via ExecOptions is not wired up, and < redirection leaks to stdout.

    @Test
    void trTranslate() {
        TrCommand cmd = new TrCommand();
        com.justbash.SimpleCommandContext ctx = new com.justbash.SimpleCommandContext(
            new InMemoryFs(), "/", java.util.Map.of(), java.util.Map.of(),
            "hello\n", null
        );
        ExecResult result = cmd.execute(java.util.List.of("a-z", "A-Z"), ctx).join();
        assertThat(result.stdout()).isEqualTo("HELLO\n");
    }

    @Test
    void trDelete() {
        TrCommand cmd = new TrCommand();
        com.justbash.SimpleCommandContext ctx = new com.justbash.SimpleCommandContext(
            new InMemoryFs(), "/", java.util.Map.of(), java.util.Map.of(),
            "hello world\n", null
        );
        ExecResult result = cmd.execute(java.util.List.of("-d", " "), ctx).join();
        assertThat(result.stdout()).isEqualTo("helloworld\n");
    }

    @Test
    void trSqueeze() {
        TrCommand cmd = new TrCommand();
        com.justbash.SimpleCommandContext ctx = new com.justbash.SimpleCommandContext(
            new InMemoryFs(), "/", java.util.Map.of(), java.util.Map.of(),
            "hello   world\n", null
        );
        ExecResult result = cmd.execute(java.util.List.of("-s", " "), ctx).join();
        assertThat(result.stdout()).isEqualTo("hello world\n");
    }

    @Test
    void trPosixClass() {
        TrCommand cmd = new TrCommand();
        com.justbash.SimpleCommandContext ctx = new com.justbash.SimpleCommandContext(
            new InMemoryFs(), "/", java.util.Map.of(), java.util.Map.of(),
            "abc123\n", null
        );
        ExecResult result = cmd.execute(java.util.List.of("[:digit:]", "X"), ctx).join();
        assertThat(result.stdout()).isEqualTo("abcXXX\n");
    }

    // ========== sort ==========

    @Test
    void sortBasic() {
        Bash bash = createBashWithFile("/tmp/sort1", "banana\napple\ncherry\n");
        BashExecResult result = bash.exec("sort /tmp/sort1").join();
        assertThat(result.stdout()).isEqualTo("apple\nbanana\ncherry\n");
        bash.shutdown();
    }

    @Test
    void sortReverse() {
        Bash bash = createBashWithFile("/tmp/sort2", "b\na\nc\n");
        BashExecResult result = bash.exec("sort -r /tmp/sort2").join();
        assertThat(result.stdout()).isEqualTo("c\nb\na\n");
        bash.shutdown();
    }

    @Test
    void sortNumeric() {
        Bash bash = createBashWithFile("/tmp/sort3", "10\n2\n1\n");
        BashExecResult result = bash.exec("sort -n /tmp/sort3").join();
        assertThat(result.stdout()).isEqualTo("1\n2\n10\n");
        bash.shutdown();
    }

    @Test
    void sortUnique() {
        Bash bash = createBashWithFile("/tmp/sort4", "a\na\nb\n");
        BashExecResult result = bash.exec("sort -u /tmp/sort4").join();
        assertThat(result.stdout()).isEqualTo("a\nb\n");
        bash.shutdown();
    }

    // ========== uniq ==========

    @Test
    void uniqBasic() {
        Bash bash = createBashWithFile("/tmp/uniq1", "a\na\nb\n");
        BashExecResult result = bash.exec("uniq /tmp/uniq1").join();
        assertThat(result.stdout()).isEqualTo("a\nb\n");
        bash.shutdown();
    }

    @Test
    void uniqCount() {
        Bash bash = createBashWithFile("/tmp/uniq2", "a\na\nb\n");
        BashExecResult result = bash.exec("uniq -c /tmp/uniq2").join();
        assertThat(result.stdout()).contains("   2 a").contains("   1 b");
        bash.shutdown();
    }

    @Test
    void uniqRepeatedOnly() {
        Bash bash = createBashWithFile("/tmp/uniq3", "a\na\nb\n");
        BashExecResult result = bash.exec("uniq -d /tmp/uniq3").join();
        assertThat(result.stdout()).isEqualTo("a\n");
        bash.shutdown();
    }

    @Test
    void uniqUniqueOnly() {
        Bash bash = createBashWithFile("/tmp/uniq4", "a\na\nb\n");
        BashExecResult result = bash.exec("uniq -u /tmp/uniq4").join();
        assertThat(result.stdout()).isEqualTo("b\n");
        bash.shutdown();
    }

    // ========== rev ==========

    @Test
    void revBasic() {
        Bash bash = createBashWithFile("/tmp/rev1", "hello\n");
        BashExecResult result = bash.exec("rev /tmp/rev1").join();
        assertThat(result.stdout()).isEqualTo("olleh\n");
        bash.shutdown();
    }

    // ========== nl ==========

    @Test
    void nlDefault() {
        Bash bash = createBashWithFile("/tmp/nl1", "hello\n\nworld\n");
        BashExecResult result = bash.exec("nl /tmp/nl1").join();
        assertThat(result.stdout()).contains("     1\thello").contains("     2\tworld");
        bash.shutdown();
    }

    @Test
    void nlAllLines() {
        Bash bash = createBashWithFile("/tmp/nl2", "a\n\nb\n");
        BashExecResult result = bash.exec("nl -ba /tmp/nl2").join();
        assertThat(result.stdout()).contains("     1\ta").contains("     2\t").contains("     3\tb");
        bash.shutdown();
    }

    @Test
    void nlFormat() {
        Bash bash = createBashWithFile("/tmp/nl3", "hello\n");
        BashExecResult result = bash.exec("nl -n rz -w 3 /tmp/nl3").join();
        assertThat(result.stdout()).contains("001\thello");
        bash.shutdown();
    }

    // ========== seq ==========

    @Test
    void seqDefault() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("seq 3").join();
        assertThat(result.stdout()).isEqualTo("1\n2\n3\n");
        bash.shutdown();
    }

    @Test
    void seqRange() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("seq 2 5").join();
        assertThat(result.stdout()).isEqualTo("2\n3\n4\n5\n");
        bash.shutdown();
    }

    @Test
    void seqIncrement() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("seq 0 2 6").join();
        assertThat(result.stdout()).isEqualTo("0\n2\n4\n6\n");
        bash.shutdown();
    }

    @Test
    void seqSeparator() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("seq -s, 3").join();
        assertThat(result.stdout()).isEqualTo("1,2,3\n");
        bash.shutdown();
    }

    @Test
    void seqWidth() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("seq -w 9 11").join();
        assertThat(result.stdout()).isEqualTo("09\n10\n11\n");
        bash.shutdown();
    }

    // ========== fold ==========

    @Test
    void foldBasic() {
        Bash bash = createBashWithFile("/tmp/fold1", "abcdefghij\n");
        BashExecResult result = bash.exec("fold -w 3 /tmp/fold1").join();
        assertThat(result.stdout()).isEqualTo("abc\ndef\nghi\nj\n");
        bash.shutdown();
    }

    @Test
    void foldAtSpaces() {
        Bash bash = createBashWithFile("/tmp/fold2", "hello world foo\n");
        BashExecResult result = bash.exec("fold -w 8 -s /tmp/fold2").join();
        assertThat(result.stdout()).isEqualTo("hello\nworld\nfoo\n");
        bash.shutdown();
    }

    // ========== diff ==========

    @Test
    void diffSameFiles() {
        InMemoryFs fs = new InMemoryFs();
        fs.writeFile("/a", new IFileSystem.StringContent("hello\n")).join();
        fs.writeFile("/b", new IFileSystem.StringContent("hello\n")).join();
        Bash bash = new Bash(new BashOptions(
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.of(fs), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty()
        ));
        bash.registerCommand(new DiffCommand());
        BashExecResult result = bash.exec("diff /a /b").join();
        assertThat(result.stdout()).isEqualTo("");
        assertThat(result.exitCode()).isEqualTo(0);
        bash.shutdown();
    }

    @Test
    void diffDifferentFiles() {
        InMemoryFs fs = new InMemoryFs();
        fs.writeFile("/a", new IFileSystem.StringContent("hello\nworld\n")).join();
        fs.writeFile("/b", new IFileSystem.StringContent("hello\nfoo\n")).join();
        Bash bash = new Bash(new BashOptions(
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.of(fs), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty()
        ));
        bash.registerCommand(new DiffCommand());
        BashExecResult result = bash.exec("diff /a /b").join();
        assertThat(result.stdout()).isNotEmpty();
        assertThat(result.exitCode()).isEqualTo(1);
        bash.shutdown();
    }

    // ========== expr ==========

    @Test
    void exprArithmetic() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("expr 2 + 3").join();
        assertThat(result.stdout()).isEqualTo("5\n");
        bash.shutdown();
    }

    @Test
    void exprComparison() {
        Bash bash = createBash();
        BashExecResult result = bash.exec("expr 5 = 5").join();
        assertThat(result.stdout()).isEqualTo("1\n");
        bash.shutdown();
    }

    // ========== file ==========

    @Test
    void fileDetectText() {
        InMemoryFs fs = new InMemoryFs();
        fs.writeFile("/test.txt", new IFileSystem.StringContent("hello world\n")).join();
        Bash bash = new Bash(new BashOptions(
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.of(fs), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty()
        ));
        bash.registerCommand(new FileCommand());
        BashExecResult result = bash.exec("file /test.txt").join();
        assertThat(result.stdout()).contains("ASCII text");
        bash.shutdown();
    }

    @Test
    void fileDetectDirectory() {
        InMemoryFs fs = new InMemoryFs();
        fs.mkdir("/mydir").join();
        Bash bash = new Bash(new BashOptions(
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.of(fs), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty()
        ));
        bash.registerCommand(new FileCommand());
        BashExecResult result = bash.exec("file /mydir").join();
        assertThat(result.stdout()).contains("directory");
        bash.shutdown();
    }

    // ========== column ==========

    @Test
    void columnTableMode() {
        Bash bash = createBashWithFile("/tmp/col1", "one two\nthree four\n");
        BashExecResult result = bash.exec("column -t /tmp/col1").join();
        assertThat(result.stdout()).contains("one").contains("two").contains("three").contains("four");
        bash.shutdown();
    }

    // ========== tree ==========

    @Test
    void treeBasic() {
        InMemoryFs fs = new InMemoryFs();
        fs.mkdir("/a").join();
        fs.mkdir("/a/b").join();
        fs.writeFile("/a/file.txt", new IFileSystem.StringContent("x\n")).join();
        Bash bash = new Bash(new BashOptions(
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.of(fs), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty()
        ));
        bash.registerCommand(new TreeCommand());
        BashExecResult result = bash.exec("tree /a").join();
        assertThat(result.stdout()).contains("/a").contains("file.txt");
        bash.shutdown();
    }

    // ========== rg ==========

    @Test
    void rgBasic() {
        InMemoryFs fs = new InMemoryFs();
        fs.writeFile("/test.txt", new IFileSystem.StringContent("hello world\nfoo bar\n")).join();
        Bash bash = new Bash(new BashOptions(
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.of(fs), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty()
        ));
        bash.registerCommand(new RgCommand());
        BashExecResult result = bash.exec("rg foo /").join();
        assertThat(result.stdout()).contains("foo bar");
        bash.shutdown();
    }

    @Test
    void rgInvertMatch() {
        InMemoryFs fs = new InMemoryFs();
        fs.writeFile("/test.txt", new IFileSystem.StringContent("hello\nworld\n")).join();
        Bash bash = new Bash(new BashOptions(
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.of(fs), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty(),
            java.util.Optional.empty(), java.util.Optional.empty()
        ));
        bash.registerCommand(new RgCommand());
        BashExecResult result = bash.exec("rg -v hello /test.txt").join();
        assertThat(result.stdout()).contains("world").doesNotContain("hello");
        bash.shutdown();
    }

    // ========== jq ==========

    @Test
    void jqIdentity() {
        Bash bash = createBashWithFile("/tmp/jq1", "{\"name\":\"test\"}\n");
        BashExecResult result = bash.exec("jq . /tmp/jq1").join();
        assertThat(result.stdout()).contains("\"name\"").contains("\"test\"");
        bash.shutdown();
    }

    @Test
    void jqGetKey() {
        Bash bash = createBashWithFile("/tmp/jq2", "{\"name\":\"test\",\"age\":30}\n");
        BashExecResult result = bash.exec("jq .name /tmp/jq2").join();
        assertThat(result.stdout()).contains("\"test\"");
        bash.shutdown();
    }

    @Test
    void jqLength() {
        Bash bash = createBashWithFile("/tmp/jq3", "[1,2,3]\n");
        BashExecResult result = bash.exec("jq length /tmp/jq3").join();
        assertThat(result.stdout()).contains("3");
        bash.shutdown();
    }

    @Test
    void jqKeys() {
        Bash bash = createBashWithFile("/tmp/jq4", "{\"a\":1,\"b\":2}\n");
        BashExecResult result = bash.exec("jq keys /tmp/jq4").join();
        assertThat(result.stdout()).contains("a").contains("b");
        bash.shutdown();
    }
}
