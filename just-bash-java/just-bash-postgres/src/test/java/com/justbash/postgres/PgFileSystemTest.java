package com.justbash.postgres;

import com.justbash.fs.*;
import org.junit.jupiter.api.*;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PgFileSystemTest {

    private DataSource dataSource;
    private PgFileSystem fs;

    @BeforeAll
    void beforeAll() throws SQLException {
        dataSource = TestDbHelper.startPostgres();
    }

    @BeforeEach
    void beforeEach() throws SQLException {
        TestDbHelper.resetDb(dataSource);
        fs = new PgFileSystem(new PgFileSystemOptions(dataSource, 1, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
        fs.setup();
    }

    // writeFile + readFile

    @Test
    void shouldWriteAndReadTextFile() {
        fs.writeFile("/hello.txt", new IFileSystem.StringContent("hello world"), WriteFileOptions.utf8()).join();
        String content = fs.readFile("/hello.txt").join();
        assertThat(content).isEqualTo("hello world");
    }

    @Test
    void shouldWriteAndReadNestedFile() {
        fs.mkdir("/docs", new MkdirOptions(true)).join();
        fs.writeFile("/docs/readme.md", new IFileSystem.StringContent("# README"), WriteFileOptions.utf8()).join();
        assertThat(fs.readFile("/docs/readme.md").join()).isEqualTo("# README");
    }

    @Test
    void shouldOverwriteExistingFile() {
        fs.writeFile("/file.txt", new IFileSystem.StringContent("v1"), WriteFileOptions.utf8()).join();
        fs.writeFile("/file.txt", new IFileSystem.StringContent("v2"), WriteFileOptions.utf8()).join();
        assertThat(fs.readFile("/file.txt").join()).isEqualTo("v2");
    }

    @Test
    void shouldThrowENOENTForMissingFile() {
        assertThatThrownBy(() -> fs.readFile("/nonexistent.txt").join())
            .hasCauseInstanceOf(FsError.class)
            .hasMessageContaining("ENOENT");
    }

    @Test
    void shouldThrowEISDIRForDirectoryRead() {
        fs.mkdir("/mydir", new MkdirOptions(true)).join();
        assertThatThrownBy(() -> fs.readFile("/mydir").join())
            .hasCauseInstanceOf(FsError.class)
            .hasMessageContaining("EISDIR");
    }

    // readFileBuffer

    @Test
    void shouldReadFileBufferAsBytes() {
        fs.writeFile("/data.bin", new IFileSystem.ByteArrayContent(new byte[]{0x01, 0x02, 0x03}), WriteFileOptions.utf8()).join();
        byte[] bytes = fs.readFileBuffer("/data.bin").join();
        assertThat(bytes).containsExactly(0x01, 0x02, 0x03);
    }

    @Test
    void shouldReadTextAsBuffer() {
        fs.writeFile("/text.txt", new IFileSystem.StringContent("hello"), WriteFileOptions.utf8()).join();
        byte[] bytes = fs.readFileBuffer("/text.txt").join();
        assertThat(bytes).containsExactly("hello".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    // appendFile

    @Test
    void shouldAppendToExistingFile() {
        fs.writeFile("/log.txt", new IFileSystem.StringContent("line1\n"), WriteFileOptions.utf8()).join();
        fs.appendFile("/log.txt", new IFileSystem.StringContent("line2\n"), WriteFileOptions.utf8()).join();
        assertThat(fs.readFile("/log.txt").join()).isEqualTo("line1\nline2\n");
    }

    @Test
    void shouldAppendCreateFileIfNotExists() {
        fs.appendFile("/new.txt", new IFileSystem.StringContent("content"), WriteFileOptions.utf8()).join();
        assertThat(fs.readFile("/new.txt").join()).isEqualTo("content");
    }

    @Test
    void shouldAppendBinaryToTextFile() {
        fs.writeFile("/mixed.txt", new IFileSystem.StringContent("hello"), WriteFileOptions.utf8()).join();
        fs.appendFile("/mixed.txt", new IFileSystem.ByteArrayContent(new byte[]{0x00, 0x01}), WriteFileOptions.utf8()).join();
        byte[] bytes = fs.readFileBuffer("/mixed.txt").join();
        assertThat(bytes).hasSizeGreaterThan(5);
    }

    // exists

    @Test
    void shouldReturnFalseForMissingFile() {
        assertThat(fs.exists("/missing.txt").join()).isFalse();
    }

    @Test
    void shouldReturnTrueForExistingFile() {
        fs.writeFile("/exists.txt", new IFileSystem.StringContent("yes"), WriteFileOptions.utf8()).join();
        assertThat(fs.exists("/exists.txt").join()).isTrue();
    }

    @Test
    void shouldReturnTrueForExistingDirectory() {
        fs.mkdir("/dir", new MkdirOptions(true)).join();
        assertThat(fs.exists("/dir").join()).isTrue();
    }

    // stat

    @Test
    void shouldStatFile() {
        fs.writeFile("/file.txt", new IFileSystem.StringContent("content"), WriteFileOptions.utf8()).join();
        FsStat stat = fs.stat("/file.txt").join();
        assertThat(stat.isFile()).isTrue();
        assertThat(stat.isDirectory()).isFalse();
        assertThat(stat.size()).isGreaterThan(0);
    }

    @Test
    void shouldStatDirectory() {
        fs.mkdir("/dir", new MkdirOptions(true)).join();
        FsStat stat = fs.stat("/dir").join();
        assertThat(stat.isFile()).isFalse();
        assertThat(stat.isDirectory()).isTrue();
    }

    @Test
    void shouldThrowENOENTForStatOfMissing() {
        assertThatThrownBy(() -> fs.stat("/missing").join())
            .hasCauseInstanceOf(FsError.class)
            .hasMessageContaining("ENOENT");
    }

    // lstat

    @Test
    void shouldLstatSymlink() {
        fs.writeFile("/target.txt", new IFileSystem.StringContent("target"), WriteFileOptions.utf8()).join();
        fs.symlink("/target.txt", "/link.txt").join();
        FsStat stat = fs.lstat("/link.txt").join();
        assertThat(stat.isSymbolicLink()).isTrue();
        assertThat(stat.isFile()).isFalse();
    }

    // mkdir

    @Test
    void shouldCreateSingleDirectory() {
        fs.mkdir("/mydir", new MkdirOptions(false)).join();
        assertThat(fs.exists("/mydir").join()).isTrue();
        assertThat(fs.stat("/mydir").join().isDirectory()).isTrue();
    }

    @Test
    void shouldCreateNestedDirectoriesRecursively() {
        fs.mkdir("/a/b/c", new MkdirOptions(true)).join();
        assertThat(fs.exists("/a/b/c").join()).isTrue();
    }

    @Test
    void shouldThrowEEXISTForDuplicateDirectory() {
        fs.mkdir("/dup", new MkdirOptions(true)).join();
        assertThatThrownBy(() -> fs.mkdir("/dup", new MkdirOptions(false)).join())
            .hasCauseInstanceOf(FsError.class)
            .hasMessageContaining("EEXIST");
    }

    @Test
    void shouldThrowENOENTForMkdirWithoutParent() {
        assertThatThrownBy(() -> fs.mkdir("/noparent/sub", new MkdirOptions(false)).join())
            .hasCauseInstanceOf(FsError.class)
            .hasMessageContaining("ENOENT");
    }

    // readdir

    @Test
    void shouldListDirectoryContents() {
        fs.writeFile("/a.txt", new IFileSystem.StringContent("a"), WriteFileOptions.utf8()).join();
        fs.writeFile("/b.txt", new IFileSystem.StringContent("b"), WriteFileOptions.utf8()).join();
        List<String> names = fs.readdir("/").join();
        assertThat(names).containsExactly("a.txt", "b.txt");
    }

    @Test
    void shouldThrowENOENTForReaddirOfMissing() {
        assertThatThrownBy(() -> fs.readdir("/missing").join())
            .hasCauseInstanceOf(FsError.class)
            .hasMessageContaining("ENOENT");
    }

    @Test
    void shouldThrowENOTDIRForReaddirOfFile() {
        fs.writeFile("/file.txt", new IFileSystem.StringContent("x"), WriteFileOptions.utf8()).join();
        assertThatThrownBy(() -> fs.readdir("/file.txt").join())
            .hasCauseInstanceOf(FsError.class)
            .hasMessageContaining("ENOTDIR");
    }

    // readdirWithFileTypes

    @Test
    void shouldListDirectoryWithTypes() {
        fs.writeFile("/file.txt", new IFileSystem.StringContent("x"), WriteFileOptions.utf8()).join();
        fs.mkdir("/subdir", new MkdirOptions(true)).join();
        List<DirentEntry> entries = fs.readdirWithFileTypes("/").join();
        assertThat(entries).hasSize(2);
        assertThat(entries.stream().filter(e -> e.name().equals("file.txt")).findFirst().orElseThrow().isFile()).isTrue();
        assertThat(entries.stream().filter(e -> e.name().equals("subdir")).findFirst().orElseThrow().isDirectory()).isTrue();
    }

    // rm

    @Test
    void shouldRemoveFile() {
        fs.writeFile("/del.txt", new IFileSystem.StringContent("x"), WriteFileOptions.utf8()).join();
        fs.rm("/del.txt", new RmOptions(false, false)).join();
        assertThat(fs.exists("/del.txt").join()).isFalse();
    }

    @Test
    void shouldRemoveEmptyDirectory() {
        fs.mkdir("/empty", new MkdirOptions(true)).join();
        fs.rm("/empty", new RmOptions(false, false)).join();
        assertThat(fs.exists("/empty").join()).isFalse();
    }

    @Test
    void shouldThrowENOTEMPTYForNonEmptyDirectory() {
        fs.mkdir("/full", new MkdirOptions(true)).join();
        fs.writeFile("/full/file.txt", new IFileSystem.StringContent("x"), WriteFileOptions.utf8()).join();
        assertThatThrownBy(() -> fs.rm("/full", new RmOptions(false, false)).join())
            .hasCauseInstanceOf(FsError.class)
            .hasMessageContaining("ENOTEMPTY");
    }

    @Test
    void shouldRecursivelyRemoveDirectory() {
        fs.mkdir("/tree/a/b", new MkdirOptions(true)).join();
        fs.writeFile("/tree/a/b/file.txt", new IFileSystem.StringContent("x"), WriteFileOptions.utf8()).join();
        fs.rm("/tree", new RmOptions(true, false)).join();
        assertThat(fs.exists("/tree").join()).isFalse();
    }

    @Test
    void shouldForceIgnoreMissing() {
        fs.rm("/missing", new RmOptions(false, true)).join();
        // no exception
    }

    // cp

    @Test
    void shouldCopyFile() {
        fs.writeFile("/src.txt", new IFileSystem.StringContent("hello"), WriteFileOptions.utf8()).join();
        fs.cp("/src.txt", "/dst.txt", new CpOptions(false)).join();
        assertThat(fs.readFile("/dst.txt").join()).isEqualTo("hello");
    }

    @Test
    void shouldThrowENOENTForMissingSource() {
        assertThatThrownBy(() -> fs.cp("/missing", "/dst", new CpOptions(false)).join())
            .hasCauseInstanceOf(FsError.class)
            .hasMessageContaining("ENOENT");
    }

    @Test
    void shouldThrowEISDIRForNonRecursiveDirectoryCopy() {
        fs.mkdir("/dir", new MkdirOptions(true)).join();
        assertThatThrownBy(() -> fs.cp("/dir", "/dir2", new CpOptions(false)).join())
            .hasCauseInstanceOf(FsError.class)
            .hasMessageContaining("EISDIR");
    }

    @Test
    void shouldRecursivelyCopyDirectory() {
        fs.mkdir("/src/dir", new MkdirOptions(true)).join();
        fs.writeFile("/src/dir/file.txt", new IFileSystem.StringContent("x"), WriteFileOptions.utf8()).join();
        fs.cp("/src", "/dst", new CpOptions(true)).join();
        assertThat(fs.readFile("/dst/dir/file.txt").join()).isEqualTo("x");
    }

    @Test
    void shouldThrowEINVALForCopyToSelf() {
        fs.writeFile("/file.txt", new IFileSystem.StringContent("x"), WriteFileOptions.utf8()).join();
        assertThatThrownBy(() -> fs.cp("/file.txt", "/file.txt", new CpOptions(false)).join())
            .hasCauseInstanceOf(FsError.class)
            .hasMessageContaining("EINVAL");
    }

    // mv

    @Test
    void shouldRenameFile() {
        fs.writeFile("/old.txt", new IFileSystem.StringContent("content"), WriteFileOptions.utf8()).join();
        fs.mv("/old.txt", "/new.txt").join();
        assertThat(fs.exists("/old.txt").join()).isFalse();
        assertThat(fs.readFile("/new.txt").join()).isEqualTo("content");
    }

    @Test
    void shouldMoveFileAcrossDirectories() {
        fs.mkdir("/src", new MkdirOptions(true)).join();
        fs.mkdir("/dst", new MkdirOptions(true)).join();
        fs.writeFile("/src/file.txt", new IFileSystem.StringContent("x"), WriteFileOptions.utf8()).join();
        fs.mv("/src/file.txt", "/dst/file.txt").join();
        assertThat(fs.exists("/dst/file.txt").join()).isTrue();
    }

    @Test
    void shouldMoveDirectoryWithDescendants() {
        fs.mkdir("/old/a", new MkdirOptions(true)).join();
        fs.writeFile("/old/a/file.txt", new IFileSystem.StringContent("x"), WriteFileOptions.utf8()).join();
        fs.mv("/old", "/new").join();
        assertThat(fs.readFile("/new/a/file.txt").join()).isEqualTo("x");
    }

    @Test
    void shouldThrowEINVALForMoveToSelf() {
        fs.writeFile("/file.txt", new IFileSystem.StringContent("x"), WriteFileOptions.utf8()).join();
        assertThatThrownBy(() -> fs.mv("/file.txt", "/file.txt").join())
            .hasCauseInstanceOf(FsError.class)
            .hasMessageContaining("EINVAL");
    }

    @Test
    void shouldThrowENOENTForMissingMvSource() {
        assertThatThrownBy(() -> fs.mv("/missing", "/dst").join())
            .hasCauseInstanceOf(FsError.class)
            .hasMessageContaining("ENOENT");
    }

    // chmod

    @Test
    void shouldChangeMode() {
        fs.writeFile("/file.txt", new IFileSystem.StringContent("x"), WriteFileOptions.utf8()).join();
        fs.chmod("/file.txt", 0755).join();
        assertThat(fs.stat("/file.txt").join().mode()).isEqualTo(0755);
    }

    @Test
    void shouldThrowENOENTForChmodOfMissing() {
        assertThatThrownBy(() -> fs.chmod("/missing", 0644).join())
            .hasCauseInstanceOf(FsError.class)
            .hasMessageContaining("ENOENT");
    }

    @Test
    void shouldRejectInvalidMode() {
        assertThatThrownBy(() -> fs.chmod("/file.txt", 010000))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid mode");
    }

    // utimes

    @Test
    void shouldUpdateMtime() {
        fs.writeFile("/file.txt", new IFileSystem.StringContent("x"), WriteFileOptions.utf8()).join();
        Instant now = Instant.parse("2024-01-15T10:30:00Z");
        fs.utimes("/file.txt", now, now).join();
        assertThat(fs.stat("/file.txt").join().mtime()).isEqualTo(now);
    }

    @Test
    void shouldThrowENOENTForUtimesOfMissing() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> fs.utimes("/missing", now, now).join())
            .hasCauseInstanceOf(FsError.class)
            .hasMessageContaining("ENOENT");
    }

    // symlink

    @Test
    void shouldCreateAndFollowSymlink() {
        fs.writeFile("/target.txt", new IFileSystem.StringContent("target"), WriteFileOptions.utf8()).join();
        fs.symlink("/target.txt", "/link.txt").join();
        assertThat(fs.readFile("/link.txt").join()).isEqualTo("target");
    }

    @Test
    void shouldReadlinkReturnTarget() {
        fs.writeFile("/target.txt", new IFileSystem.StringContent("target"), WriteFileOptions.utf8()).join();
        fs.symlink("/target.txt", "/link.txt").join();
        assertThat(fs.readlink("/link.txt").join()).isEqualTo("/target.txt");
    }

    @Test
    void shouldThrowEINVALForReadlinkOfNonSymlink() {
        fs.writeFile("/file.txt", new IFileSystem.StringContent("x"), WriteFileOptions.utf8()).join();
        assertThatThrownBy(() -> fs.readlink("/file.txt").join())
            .hasCauseInstanceOf(FsError.class)
            .hasMessageContaining("EINVAL");
    }

    @Test
    void shouldThrowELOOPForSymlinkCycle() {
        fs.writeFile("/a.txt", new IFileSystem.StringContent("a"), WriteFileOptions.utf8()).join();
        fs.symlink("/b.txt", "/a.txt").join();
        fs.symlink("/a.txt", "/b.txt").join();
        assertThatThrownBy(() -> fs.readFile("/a.txt").join())
            .hasCauseInstanceOf(FsError.class)
            .hasMessageContaining("ELOOP");
    }

    // link (hard link)

    @Test
    void shouldCreateHardLinkCopy() {
        fs.writeFile("/orig.txt", new IFileSystem.StringContent("original"), WriteFileOptions.utf8()).join();
        fs.link("/orig.txt", "/hl.txt").join();
        assertThat(fs.readFile("/hl.txt").join()).isEqualTo("original");
    }

    @Test
    void shouldThrowENOENTForLinkOfMissing() {
        assertThatThrownBy(() -> fs.link("/missing", "/hl").join())
            .hasCauseInstanceOf(FsError.class)
            .hasMessageContaining("ENOENT");
    }

    @Test
    void shouldThrowEPERMForLinkOfDirectory() {
        fs.mkdir("/dir", new MkdirOptions(true)).join();
        assertThatThrownBy(() -> fs.link("/dir", "/hl").join())
            .hasCauseInstanceOf(FsError.class)
            .hasMessageContaining("EPERM");
    }

    // realpath

    @Test
    void shouldResolveRealpath() {
        fs.writeFile("/file.txt", new IFileSystem.StringContent("x"), WriteFileOptions.utf8()).join();
        assertThat(fs.realpath("/file.txt").join()).isEqualTo("/file.txt");
    }

    @Test
    void shouldResolveRealpathThroughSymlink() {
        fs.writeFile("/target.txt", new IFileSystem.StringContent("x"), WriteFileOptions.utf8()).join();
        fs.symlink("/target.txt", "/link.txt").join();
        assertThat(fs.realpath("/link.txt").join()).isEqualTo("/target.txt");
    }

    // resolvePath

    @Test
    void shouldResolveAbsolutePath() {
        assertThat(fs.resolvePath("/base", "/absolute")).isEqualTo("/absolute");
    }

    @Test
    void shouldResolveRelativePath() {
        assertThat(fs.resolvePath("/home/user", "docs")).isEqualTo("/home/user/docs");
    }

    @Test
    void shouldResolveDotDot() {
        assertThat(fs.resolvePath("/home/user", "../docs")).isEqualTo("/home/docs");
    }
}
