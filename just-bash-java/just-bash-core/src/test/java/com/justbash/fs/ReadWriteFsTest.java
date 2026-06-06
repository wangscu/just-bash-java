package com.justbash.fs;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReadWriteFsTest {

    @TempDir
    Path tempDir;

    private ReadWriteFs createFs() {
        return new ReadWriteFs(new ReadWriteFsOptions(tempDir.toString(), 0, true));
    }

    // --- constructor ---

    @Test
    void shouldCreateWithValidRootDirectory() {
        ReadWriteFs fs = createFs();
        assertThat(fs).isNotNull();
    }

    @Test
    void shouldThrowForNonExistentRoot() {
        assertThatThrownBy(() ->
            new ReadWriteFs(new ReadWriteFsOptions("/nonexistent/path/12345"))
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("does not exist");
    }

    @Test
    void shouldThrowForFileAsRoot() throws IOException {
        Path file = tempDir.resolve("file.txt");
        Files.writeString(file, "content");
        assertThatThrownBy(() ->
            new ReadWriteFs(new ReadWriteFsOptions(file.toString()))
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("not a directory");
    }

    // --- reading files ---

    @Test
    void shouldReadFilesFromFilesystem() throws IOException {
        Files.writeString(tempDir.resolve("test.txt"), "real content");
        ReadWriteFs fs = createFs();

        String content = fs.readFile("/test.txt").join();
        assertThat(content).isEqualTo("real content");
    }

    @Test
    void shouldReadNestedFiles() throws IOException {
        Path subdir = tempDir.resolve("subdir");
        Files.createDirectories(subdir);
        Files.writeString(subdir.resolve("file.txt"), "nested");
        ReadWriteFs fs = createFs();

        String content = fs.readFile("/subdir/file.txt").join();
        assertThat(content).isEqualTo("nested");
    }

    @Test
    void shouldReadFilesAsBuffer() throws IOException {
        byte[] data = new byte[]{0x48, 0x65, 0x6c, 0x6c, 0x6f};
        Files.write(tempDir.resolve("binary.bin"), data);
        ReadWriteFs fs = createFs();

        byte[] buffer = fs.readFileBuffer("/binary.bin").join();
        assertThat(buffer).containsExactly(data);
    }

    @Test
    void shouldThrowENOENTForNonExistentFile() {
        ReadWriteFs fs = createFs();
        assertThatThrownBy(() -> fs.readFile("/nonexistent.txt").join())
            .hasCauseInstanceOf(FsException.class)
            .hasMessageContaining("ENOENT");
    }

    @Test
    void shouldThrowEISDIRWhenReadingDirectory() {
        ReadWriteFs fs = createFs();
        fs.mkdir("/dir").join();
        assertThatThrownBy(() -> fs.readFile("/dir").join())
            .hasCauseInstanceOf(FsException.class)
            .hasMessageContaining("EISDIR");
    }

    // --- writing files ---

    @Test
    void shouldWriteFilesToFilesystem() {
        ReadWriteFs fs = createFs();

        fs.writeFile("/new.txt", new IFileSystem.StringContent("new content")).join();

        String content = fs.readFile("/new.txt").join();
        assertThat(content).isEqualTo("new content");

        assertThat(tempDir.resolve("new.txt")).hasContent("new content");
    }

    @Test
    void shouldCreateParentDirectoriesWhenWriting() {
        ReadWriteFs fs = createFs();

        fs.writeFile("/deep/nested/file.txt", new IFileSystem.StringContent("content")).join();

        assertThat(tempDir.resolve("deep/nested/file.txt")).hasContent("content");
    }

    @Test
    void shouldOverwriteExistingFiles() throws IOException {
        Files.writeString(tempDir.resolve("test.txt"), "original");
        ReadWriteFs fs = createFs();

        fs.writeFile("/test.txt", new IFileSystem.StringContent("modified")).join();

        assertThat(tempDir.resolve("test.txt")).hasContent("modified");
    }

    @Test
    void shouldWriteBinaryContent() throws IOException {
        ReadWriteFs fs = createFs();
        byte[] data = new byte[]{0x00, 0x01, 0x02, (byte) 0xff};

        fs.writeFile("/binary.bin", new IFileSystem.ByteArrayContent(data)).join();

        byte[] written = Files.readAllBytes(tempDir.resolve("binary.bin"));
        assertThat(written).containsExactly(data);
    }

    // --- appending files ---

    @Test
    void shouldAppendToExistingFiles() throws IOException {
        Files.writeString(tempDir.resolve("append.txt"), "start");
        ReadWriteFs fs = createFs();

        fs.appendFile("/append.txt", new IFileSystem.StringContent("-end"), WriteFileOptions.utf8()).join();

        assertThat(tempDir.resolve("append.txt")).hasContent("start-end");
    }

    @Test
    void shouldCreateFileIfItDoesNotExist() {
        ReadWriteFs fs = createFs();

        fs.appendFile("/new.txt", new IFileSystem.StringContent("content"), WriteFileOptions.utf8()).join();

        assertThat(tempDir.resolve("new.txt")).hasContent("content");
    }

    // --- exists ---

    @Test
    void shouldReturnTrueForExistingFiles() throws IOException {
        Files.writeString(tempDir.resolve("exists.txt"), "content");
        ReadWriteFs fs = createFs();

        assertThat(fs.exists("/exists.txt").join()).isTrue();
    }

    @Test
    void shouldReturnTrueForExistingDirectories() {
        ReadWriteFs fs = createFs();
        fs.mkdir("/dir").join();

        assertThat(fs.exists("/dir").join()).isTrue();
    }

    @Test
    void shouldReturnFalseForNonExistentPaths() {
        ReadWriteFs fs = createFs();
        assertThat(fs.exists("/nonexistent").join()).isFalse();
    }

    @Test
    void shouldReturnFalseForPathsWithNullBytes() {
        ReadWriteFs fs = createFs();
        assertThat(fs.exists("/file\0.txt").join()).isFalse();
    }

    // --- stat ---

    @Test
    void shouldStatFiles() throws IOException {
        Files.writeString(tempDir.resolve("file.txt"), "content");
        ReadWriteFs fs = createFs();

        FsStat stat = fs.stat("/file.txt").join();
        assertThat(stat.isFile()).isTrue();
        assertThat(stat.isDirectory()).isFalse();
        assertThat(stat.size()).isEqualTo(7);
    }

    @Test
    void shouldStatDirectories() {
        ReadWriteFs fs = createFs();
        fs.mkdir("/dir").join();

        FsStat stat = fs.stat("/dir").join();
        assertThat(stat.isFile()).isFalse();
        assertThat(stat.isDirectory()).isTrue();
    }

    @Test
    void shouldThrowENOENTForNonExistentPaths() {
        ReadWriteFs fs = createFs();
        assertThatThrownBy(() -> fs.stat("/nonexistent").join())
            .hasCauseInstanceOf(FsException.class)
            .hasMessageContaining("ENOENT");
    }

    // --- lstat ---

    @Test
    void shouldLstatSymlinksWithoutFollowing() throws IOException {
        Files.writeString(tempDir.resolve("target.txt"), "content");
        try {
            Files.createSymbolicLink(tempDir.resolve("link"), tempDir.resolve("target.txt"));
        } catch (Exception e) {
            // Skip on systems that don't support symlinks
            return;
        }
        ReadWriteFs fs = createFs();

        FsStat stat = fs.lstat("/link").join();
        assertThat(stat.isSymbolicLink()).isTrue();
    }

    // --- mkdir ---

    @Test
    void shouldCreateDirectories() {
        ReadWriteFs fs = createFs();

        fs.mkdir("/newdir").join();

        assertThat(Files.isDirectory(tempDir.resolve("newdir"))).isTrue();
    }

    @Test
    void shouldCreateNestedDirectoriesWithRecursiveOption() {
        ReadWriteFs fs = createFs();

        fs.mkdir("/a/b/c", new MkdirOptions(true)).join();

        assertThat(Files.isDirectory(tempDir.resolve("a/b/c"))).isTrue();
    }

    @Test
    void shouldThrowENOENTWithoutRecursiveForMissingParent() {
        ReadWriteFs fs = createFs();

        assertThatThrownBy(() -> fs.mkdir("/missing/dir").join())
            .hasCauseInstanceOf(FsException.class)
            .hasMessageContaining("ENOENT");
    }

    @Test
    void shouldThrowEEXISTForExistingDirectoryWithoutRecursive() {
        ReadWriteFs fs = createFs();
        fs.mkdir("/existing").join();

        assertThatThrownBy(() -> fs.mkdir("/existing").join())
            .hasCauseInstanceOf(FsException.class)
            .hasMessageContaining("EEXIST");
    }

    // --- readdir ---

    @Test
    void shouldListDirectoryContents() throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "a");
        Files.writeString(tempDir.resolve("b.txt"), "b");
        Files.createDirectories(tempDir.resolve("subdir"));
        ReadWriteFs fs = createFs();

        List<String> entries = fs.readdir("/").join();
        assertThat(entries).contains("a.txt", "b.txt", "subdir");
    }

    @Test
    void shouldThrowENOENTForNonExistentDirectory() {
        ReadWriteFs fs = createFs();
        assertThatThrownBy(() -> fs.readdir("/nonexistent").join())
            .hasCauseInstanceOf(FsException.class)
            .hasMessageContaining("ENOENT");
    }

    @Test
    void shouldThrowENOTDIRForFiles() throws IOException {
        Files.writeString(tempDir.resolve("file.txt"), "content");
        ReadWriteFs fs = createFs();

        assertThatThrownBy(() -> fs.readdir("/file.txt").join())
            .hasCauseInstanceOf(FsException.class)
            .hasMessageContaining("ENOTDIR");
    }

    // --- rm ---

    @Test
    void shouldDeleteFiles() throws IOException {
        Files.writeString(tempDir.resolve("delete.txt"), "content");
        ReadWriteFs fs = createFs();

        fs.rm("/delete.txt").join();

        assertThat(Files.exists(tempDir.resolve("delete.txt"))).isFalse();
    }

    @Test
    void shouldDeleteDirectoriesRecursively() throws IOException {
        Files.createDirectories(tempDir.resolve("dir"));
        Files.writeString(tempDir.resolve("dir/file.txt"), "content");
        ReadWriteFs fs = createFs();

        fs.rm("/dir", new RmOptions(true, false)).join();

        assertThat(Files.exists(tempDir.resolve("dir"))).isFalse();
    }

    @Test
    void shouldThrowENOENTWithoutForceOption() {
        ReadWriteFs fs = createFs();
        assertThatThrownBy(() -> fs.rm("/nonexistent").join())
            .hasCauseInstanceOf(FsException.class)
            .hasMessageContaining("ENOENT");
    }

    @Test
    void shouldNotThrowWithForceOptionForNonExistentFiles() {
        ReadWriteFs fs = createFs();
        fs.rm("/nonexistent", new RmOptions(false, true)).join();
        // Should not throw
    }

    // --- cp ---

    @Test
    void shouldCopyFiles() throws IOException {
        Files.writeString(tempDir.resolve("source.txt"), "content");
        ReadWriteFs fs = createFs();

        fs.cp("/source.txt", "/dest.txt", CpOptions.defaults()).join();

        assertThat(tempDir.resolve("dest.txt")).hasContent("content");
    }

    @Test
    void shouldCopyDirectoriesRecursively() throws IOException {
        Files.createDirectories(tempDir.resolve("srcdir"));
        Files.writeString(tempDir.resolve("srcdir/file.txt"), "content");
        ReadWriteFs fs = createFs();

        fs.cp("/srcdir", "/destdir", new CpOptions(true)).join();

        assertThat(tempDir.resolve("destdir/file.txt")).hasContent("content");
    }

    @Test
    void shouldThrowENOENTForNonExistentSource() {
        ReadWriteFs fs = createFs();
        assertThatThrownBy(() -> fs.cp("/nonexistent", "/dest", CpOptions.defaults()).join())
            .hasCauseInstanceOf(FsException.class)
            .hasMessageContaining("ENOENT");
    }

    // --- mv ---

    @Test
    void shouldMoveFiles() throws IOException {
        Files.writeString(tempDir.resolve("source.txt"), "content");
        ReadWriteFs fs = createFs();

        fs.mv("/source.txt", "/dest.txt").join();

        assertThat(Files.exists(tempDir.resolve("source.txt"))).isFalse();
        assertThat(tempDir.resolve("dest.txt")).hasContent("content");
    }

    @Test
    void shouldMoveDirectories() throws IOException {
        Files.createDirectories(tempDir.resolve("srcdir"));
        Files.writeString(tempDir.resolve("srcdir/file.txt"), "content");
        ReadWriteFs fs = createFs();

        fs.mv("/srcdir", "/destdir").join();

        assertThat(Files.exists(tempDir.resolve("srcdir"))).isFalse();
        assertThat(tempDir.resolve("destdir/file.txt")).hasContent("content");
    }

    // --- chmod ---

    @Test
    void shouldChangeFilePermissions() throws IOException {
        Files.writeString(tempDir.resolve("file.txt"), "content");
        ReadWriteFs fs = createFs();

        fs.chmod("/file.txt", 0755).join();

        // Just verify it doesn't throw
    }

    @Test
    void shouldThrowENOENTForNonExistentChmodTarget() {
        ReadWriteFs fs = createFs();
        assertThatThrownBy(() -> fs.chmod("/nonexistent", 0755).join())
            .hasCauseInstanceOf(FsException.class)
            .hasMessageContaining("ENOENT");
    }

    // --- symlink ---

    @Test
    void shouldCreateSymbolicLinks() throws IOException {
        Files.writeString(tempDir.resolve("target.txt"), "content");
        ReadWriteFs fs = createFs();

        try {
            fs.symlink("target.txt", "/link").join();
        } catch (Exception e) {
            // Skip on systems that don't support symlinks
            return;
        }

        String content = fs.readFile("/link").join();
        assertThat(content).isEqualTo("content");
    }

    @Test
    void shouldThrowEEXISTForExistingPath() throws IOException {
        Files.writeString(tempDir.resolve("existing"), "content");
        ReadWriteFs fs = createFs();

        assertThatThrownBy(() -> fs.symlink("target", "/existing").join())
            .hasCauseInstanceOf(FsException.class)
            .hasMessageContaining("EEXIST");
    }

    // --- link ---

    @Test
    void shouldCreateHardLinks() throws IOException {
        Files.writeString(tempDir.resolve("original.txt"), "content");
        ReadWriteFs fs = createFs();

        fs.link("/original.txt", "/hardlink.txt").join();

        String content = fs.readFile("/hardlink.txt").join();
        assertThat(content).isEqualTo("content");
    }

    @Test
    void shouldThrowENOENTForNonExistentLinkSource() {
        ReadWriteFs fs = createFs();
        assertThatThrownBy(() -> fs.link("/nonexistent", "/link").join())
            .hasCauseInstanceOf(FsException.class)
            .hasMessageContaining("ENOENT");
    }

    @Test
    void shouldThrowEEXISTForExistingDestination() throws IOException {
        Files.writeString(tempDir.resolve("source.txt"), "content");
        Files.writeString(tempDir.resolve("existing.txt"), "existing");
        ReadWriteFs fs = createFs();

        assertThatThrownBy(() -> fs.link("/source.txt", "/existing.txt").join())
            .hasCauseInstanceOf(FsException.class)
            .hasMessageContaining("EEXIST");
    }

    // --- readlink ---

    @Test
    void shouldReadSymlinkTarget() throws IOException {
        try {
            Files.createSymbolicLink(tempDir.resolve("link"), Path.of("target.txt"));
        } catch (Exception e) {
            // Skip on systems that don't support symlinks
            return;
        }
        ReadWriteFs fs = createFs();

        String target = fs.readlink("/link").join();
        assertThat(target).isEqualTo("target.txt");
    }

    @Test
    void shouldThrowENOENTForNonExistentSymlink() {
        ReadWriteFs fs = createFs();
        assertThatThrownBy(() -> fs.readlink("/nonexistent").join())
            .hasCauseInstanceOf(FsException.class)
            .hasMessageContaining("ENOENT");
    }

    @Test
    void shouldThrowEINVALForNonSymlink() throws IOException {
        Files.writeString(tempDir.resolve("file.txt"), "content");
        ReadWriteFs fs = createFs();

        assertThatThrownBy(() -> fs.readlink("/file.txt").join())
            .hasCauseInstanceOf(FsException.class)
            .hasMessageContaining("EINVAL");
    }

    // --- resolvePath ---

    @Test
    void shouldResolveRelativePaths() {
        ReadWriteFs fs = createFs();

        assertThat(fs.resolvePath("/dir", "file.txt")).isEqualTo("/dir/file.txt");
        assertThat(fs.resolvePath("/dir", "../file.txt")).isEqualTo("/file.txt");
    }

    @Test
    void shouldHandleAbsolutePaths() {
        ReadWriteFs fs = createFs();

        assertThat(fs.resolvePath("/dir", "/other/file.txt")).isEqualTo("/other/file.txt");
    }

    // --- getAllPaths ---

    @Test
    void shouldReturnAllPathsInFilesystem() throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "a");
        Files.createDirectories(tempDir.resolve("subdir"));
        Files.writeString(tempDir.resolve("subdir/b.txt"), "b");
        ReadWriteFs fs = createFs();

        List<String> paths = fs.getAllPaths();
        assertThat(paths).contains("/a.txt", "/subdir", "/subdir/b.txt");
    }

    // --- readdirWithFileTypes ---

    @Test
    void shouldReturnEntriesWithCorrectTypeInfo() throws IOException {
        ReadWriteFs fs = createFs();
        fs.writeFile("/file.txt", new IFileSystem.StringContent("content")).join();
        fs.mkdir("/subdir").join();

        List<DirentEntry> entries = fs.readdirWithFileTypes("/").join();

        DirentEntry file = entries.stream().filter(e -> e.name().equals("file.txt")).findFirst().orElseThrow();
        assertThat(file.isFile()).isTrue();
        assertThat(file.isDirectory()).isFalse();

        DirentEntry subdir = entries.stream().filter(e -> e.name().equals("subdir")).findFirst().orElseThrow();
        assertThat(subdir.isFile()).isFalse();
        assertThat(subdir.isDirectory()).isTrue();
    }

    @Test
    void shouldReturnEntriesSortedCaseSensitively() throws IOException {
        ReadWriteFs fs = createFs();
        fs.writeFile("/Zebra.txt", new IFileSystem.StringContent("z")).join();
        fs.writeFile("/apple.txt", new IFileSystem.StringContent("a")).join();
        fs.writeFile("/Banana.txt", new IFileSystem.StringContent("b")).join();

        List<DirentEntry> entries = fs.readdirWithFileTypes("/").join();
        List<String> names = entries.stream().map(DirentEntry::name).toList();

        assertThat(names).containsExactly("Banana.txt", "Zebra.txt", "apple.txt");
    }

    @Test
    void shouldReturnSameNamesAsReaddir() throws IOException {
        ReadWriteFs fs = createFs();
        fs.writeFile("/a.txt", new IFileSystem.StringContent("a")).join();
        fs.writeFile("/b.txt", new IFileSystem.StringContent("b")).join();
        fs.mkdir("/sub").join();

        List<String> namesFromReaddir = fs.readdir("/").join();
        List<DirentEntry> entriesWithTypes = fs.readdirWithFileTypes("/").join();
        List<String> namesFromWithTypes = entriesWithTypes.stream().map(DirentEntry::name).toList();

        assertThat(namesFromWithTypes).isEqualTo(namesFromReaddir);
    }

    // --- utimes ---

    @Test
    void shouldSetModificationTime() throws IOException {
        Files.writeString(tempDir.resolve("file.txt"), "content");
        ReadWriteFs fs = createFs();

        Instant now = Instant.now();
        fs.utimes("/file.txt", now, now).join();

        // Verify it doesn't throw
    }

    // --- realpath ---

    @Test
    void shouldReturnCanonicalPath() throws IOException {
        Files.writeString(tempDir.resolve("file.txt"), "content");
        ReadWriteFs fs = createFs();

        String result = fs.realpath("/file.txt").join();
        assertThat(result).isEqualTo("/file.txt");
    }

    // --- maxFileReadSize ---

    @Test
    void shouldEnforceMaxFileReadSize() throws IOException {
        Files.writeString(tempDir.resolve("large.txt"), "x".repeat(1000));
        ReadWriteFs fs = new ReadWriteFs(
            new ReadWriteFsOptions(tempDir.toString(), 100, true)
        );

        assertThatThrownBy(() -> fs.readFile("/large.txt").join())
            .hasCauseInstanceOf(FsException.class)
            .hasMessageContaining("EFBIG");
    }
}
