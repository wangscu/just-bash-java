package com.justbash.fs;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class InMemoryFsTest {
    @Test
    void writeAndReadFile() throws Exception {
        InMemoryFs fs = new InMemoryFs();
        fs.writeFile("/test.txt",
            new IFileSystem.StringContent("hello"),
            WriteFileOptions.utf8()).join();

        String content = fs.readFile("/test.txt").join();
        assertThat(content).isEqualTo("hello");
    }

    @Test
    void mkdirCreatesDirectory() throws Exception {
        InMemoryFs fs = new InMemoryFs();
        fs.mkdir("/foo/bar", new MkdirOptions(true)).join();

        FsStat stat = fs.stat("/foo/bar").join();
        assertThat(stat.isDirectory()).isTrue();
    }

    @Test
    void readdirListsEntries() throws Exception {
        InMemoryFs fs = new InMemoryFs();
        fs.writeFile("/foo/a.txt",
            new IFileSystem.StringContent("a"),
            WriteFileOptions.utf8()).join();
        fs.writeFile("/foo/b.txt",
            new IFileSystem.StringContent("b"),
            WriteFileOptions.utf8()).join();

        var entries = fs.readdir("/foo").join();
        assertThat(entries).containsExactlyInAnyOrder("a.txt", "b.txt");
    }

    @Test
    void resolvePathResolvesRelativePaths() {
        InMemoryFs fs = new InMemoryFs();
        assertThat(fs.resolvePath("/home/user", "foo/bar"))
            .isEqualTo("/home/user/foo/bar");
        assertThat(fs.resolvePath("/home/user", "/abs/path"))
            .isEqualTo("/abs/path");
    }
}
