package com.justbash.fs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

public class OverlayFsTest {

    private static void assertThrowsFs(Class<? extends Throwable> expectedType, Runnable runnable) {
        CompletionException thrown = assertThrows(CompletionException.class, runnable::run);
        assertInstanceOf(expectedType, thrown.getCause());
    }

    @TempDir
    Path tempDir;

    private OverlayFs createOverlay() {
        return new OverlayFs(new OverlayFs.OverlayFsOptions(tempDir.toString()));
    }

    private OverlayFs createOverlayReadOnly() {
        return new OverlayFs(new OverlayFs.OverlayFsOptions(tempDir.toString(), null, true, 0, false));
    }

    // ------------------------------------------------------------------
    // Basic read
    // ------------------------------------------------------------------

    @Test
    void testReadRealFile() throws Exception {
        Files.writeString(tempDir.resolve("hello.txt"), "hello world");
        OverlayFs fs = createOverlay();

        String content = fs.readFile("/home/user/project/hello.txt").join();
        assertEquals("hello world", content);
    }

    @Test
    void testReadRealFileInSubdirectory() throws Exception {
        Files.createDirectories(tempDir.resolve("subdir"));
        Files.writeString(tempDir.resolve("subdir/nested.txt"), "nested content");
        OverlayFs fs = createOverlay();

        String content = fs.readFile("/home/user/project/subdir/nested.txt").join();
        assertEquals("nested content", content);
    }

    @Test
    void testReadNonExistentFile() {
        OverlayFs fs = createOverlay();

        assertThrowsFs(FsException.class, () ->
            fs.readFile("/home/user/project/nonexistent.txt").join()
        );
    }

    // ------------------------------------------------------------------
    // Copy-on-write
    // ------------------------------------------------------------------

    @Test
    void testWriteDoesNotModifyRealFile() throws Exception {
        Files.writeString(tempDir.resolve("cow.txt"), "original");
        OverlayFs fs = createOverlay();

        fs.writeFile("/home/user/project/cow.txt",
            new IFileSystem.StringContent("modified"), WriteFileOptions.utf8()).join();

        // Memory layer has modified content
        String overlayContent = fs.readFile("/home/user/project/cow.txt").join();
        assertEquals("modified", overlayContent);

        // Real file is unchanged
        String realContent = Files.readString(tempDir.resolve("cow.txt"));
        assertEquals("original", realContent);
    }

    @Test
    void testWriteCreatesNewFileInMemory() throws Exception {
        OverlayFs fs = createOverlay();

        fs.writeFile("/home/user/project/newfile.txt",
            new IFileSystem.StringContent("brand new"), WriteFileOptions.utf8()).join();

        String content = fs.readFile("/home/user/project/newfile.txt").join();
        assertEquals("brand new", content);

        // Real file system should not have it
        assertFalse(Files.exists(tempDir.resolve("newfile.txt")));
    }

    // ------------------------------------------------------------------
    // Delete tombstones
    // ------------------------------------------------------------------

    @Test
    void testDeleteRealFileAddsTombstone() throws Exception {
        Files.writeString(tempDir.resolve("delete-me.txt"), "delete me");
        OverlayFs fs = createOverlay();

        assertTrue(fs.exists("/home/user/project/delete-me.txt").join());

        fs.rm("/home/user/project/delete-me.txt").join();

        assertFalse(fs.exists("/home/user/project/delete-me.txt").join());
        assertThrowsFs(FsException.class, () ->
            fs.readFile("/home/user/project/delete-me.txt").join()
        );

        // Real file still exists
        assertTrue(Files.exists(tempDir.resolve("delete-me.txt")));
    }

    @Test
    void testDeleteMemoryOnlyFileNoTombstone() throws Exception {
        OverlayFs fs = createOverlay();
        fs.writeFile("/home/user/project/mem-only.txt",
            new IFileSystem.StringContent("memory"), WriteFileOptions.utf8()).join();

        fs.rm("/home/user/project/mem-only.txt").join();

        assertFalse(fs.exists("/home/user/project/mem-only.txt").join());
    }

    // ------------------------------------------------------------------
    // Mount point mapping
    // ------------------------------------------------------------------

    @Test
    void testMountPointDefault() {
        OverlayFs fs = createOverlay();
        assertEquals("/home/user/project", fs.getMountPoint());
    }

    @Test
    void testCustomMountPoint() {
        OverlayFs fs = new OverlayFs(
            new OverlayFs.OverlayFsOptions(tempDir.toString(), "/data", false, 0, false));
        assertEquals("/data", fs.getMountPoint());
    }

    @Test
    void testReadThroughCustomMountPoint() throws Exception {
        Files.writeString(tempDir.resolve("custom.txt"), "custom mount");
        OverlayFs fs = new OverlayFs(
            new OverlayFs.OverlayFsOptions(tempDir.toString(), "/data", false, 0, false));

        String content = fs.readFile("/data/custom.txt").join();
        assertEquals("custom mount", content);
    }

    @Test
    void testPathsOutsideMountPointReturnNull() {
        OverlayFs fs = createOverlay();
        assertFalse(fs.exists("/outside/mount/point.txt").join());
    }

    // ------------------------------------------------------------------
    // Read-only mode
    // ------------------------------------------------------------------

    @Test
    void testReadOnlyBlocksWrite() {
        OverlayFs fs = createOverlayReadOnly();

        assertThrowsFs(FsException.class, () ->
            fs.writeFile("/home/user/project/readonly.txt",
                new IFileSystem.StringContent("should fail"), WriteFileOptions.utf8()).join()
        );
    }

    @Test
    void testReadOnlyBlocksDelete() throws Exception {
        Files.writeString(tempDir.resolve("readonly-del.txt"), "content");
        OverlayFs fs = createOverlayReadOnly();

        assertThrowsFs(FsException.class, () ->
            fs.rm("/home/user/project/readonly-del.txt").join()
        );
    }

    @Test
    void testReadOnlyAllowsRead() throws Exception {
        Files.writeString(tempDir.resolve("readonly-read.txt"), "readable");
        OverlayFs fs = createOverlayReadOnly();

        String content = fs.readFile("/home/user/project/readonly-read.txt").join();
        assertEquals("readable", content);
    }

    // ------------------------------------------------------------------
    // Symlink security
    // ------------------------------------------------------------------

    @Test
    void testSymlinkBlockedByDefault() {
        OverlayFs fs = createOverlay();

        assertThrowsFs(FsException.class, () ->
            fs.symlink("target", "/home/user/project/link.txt").join()
        );
    }

    @Test
    void testSymlinkAllowedWhenEnabled() {
        OverlayFs fs = new OverlayFs(
            new OverlayFs.OverlayFsOptions(tempDir.toString(), null, false, 0, true));

        assertDoesNotThrow(() ->
            fs.symlink("target", "/home/user/project/link.txt").join()
        );
    }

    @Test
    void testMemorySymlinkFollowed() {
        OverlayFs fs = new OverlayFs(
            new OverlayFs.OverlayFsOptions(tempDir.toString(), null, false, 0, true));

        fs.writeFile("/home/user/project/real.txt",
            new IFileSystem.StringContent("real content"), WriteFileOptions.utf8()).join();
        fs.symlink("real.txt", "/home/user/project/link.txt").join();

        String content = fs.readFile("/home/user/project/link.txt").join();
        assertEquals("real content", content);
    }

    @Test
    void testSymlinkLoopDetected() {
        OverlayFs fs = new OverlayFs(
            new OverlayFs.OverlayFsOptions(tempDir.toString(), null, false, 0, true));

        fs.symlink("a", "/home/user/project/a").join();

        assertThrowsFs(FsException.class, () ->
            fs.readFile("/home/user/project/a").join()
        );
    }

    // ------------------------------------------------------------------
    // Directory operations
    // ------------------------------------------------------------------

    @Test
    void testMkdir() throws Exception {
        OverlayFs fs = createOverlay();
        fs.mkdir("/home/user/project/newdir").join();

        assertTrue(fs.exists("/home/user/project/newdir").join());
        FsStat stat = fs.stat("/home/user/project/newdir").join();
        assertTrue(stat.isDirectory());
    }

    @Test
    void testReaddirFromRealFs() throws Exception {
        Files.writeString(tempDir.resolve("a.txt"), "a");
        Files.writeString(tempDir.resolve("b.txt"), "b");
        OverlayFs fs = createOverlay();

        List<String> entries = fs.readdir("/home/user/project").join();
        assertTrue(entries.contains("a.txt"));
        assertTrue(entries.contains("b.txt"));
    }

    @Test
    void testReaddirWithFileTypes() throws Exception {
        Files.writeString(tempDir.resolve("file.txt"), "content");
        Files.createDirectories(tempDir.resolve("subdir"));
        OverlayFs fs = createOverlay();

        List<DirentEntry> entries = fs.readdirWithFileTypes("/home/user/project").join();

        DirentEntry fileEntry = entries.stream().filter(e -> e.name().equals("file.txt")).findFirst().orElse(null);
        DirentEntry dirEntry = entries.stream().filter(e -> e.name().equals("subdir")).findFirst().orElse(null);

        assertNotNull(fileEntry);
        assertTrue(fileEntry.isFile());
        assertNotNull(dirEntry);
        assertTrue(dirEntry.isDirectory());
    }

    @Test
    void testReaddirMergesMemoryAndReal() throws Exception {
        Files.writeString(tempDir.resolve("real.txt"), "real");
        OverlayFs fs = createOverlay();
        fs.writeFile("/home/user/project/mem.txt",
            new IFileSystem.StringContent("mem"), WriteFileOptions.utf8()).join();

        List<String> entries = fs.readdir("/home/user/project").join();
        assertTrue(entries.contains("real.txt"));
        assertTrue(entries.contains("mem.txt"));
    }

    @Test
    void testReaddirHidesDeletedFiles() throws Exception {
        Files.writeString(tempDir.resolve("hide-me.txt"), "hide");
        OverlayFs fs = createOverlay();
        fs.rm("/home/user/project/hide-me.txt").join();

        List<String> entries = fs.readdir("/home/user/project").join();
        assertFalse(entries.contains("hide-me.txt"));
    }

    @Test
    void testRmRecursive() throws Exception {
        Files.createDirectories(tempDir.resolve("tree/sub"));
        Files.writeString(tempDir.resolve("tree/sub/leaf.txt"), "leaf");
        OverlayFs fs = createOverlay();

        fs.rm("/home/user/project/tree", new RmOptions(true, true)).join();

        assertFalse(fs.exists("/home/user/project/tree").join());
        assertTrue(fs.exists("/home/user/project").join());
    }

    @Test
    void testRmNonRecursiveDirectoryFails() throws Exception {
        Files.createDirectories(tempDir.resolve("nonempty"));
        Files.writeString(tempDir.resolve("nonempty/child.txt"), "child");
        OverlayFs fs = createOverlay();

        assertThrowsFs(FsException.class, () ->
            fs.rm("/home/user/project/nonempty").join()
        );
    }

    // ------------------------------------------------------------------
    // Append
    // ------------------------------------------------------------------

    @Test
    void testAppendToRealFile() throws Exception {
        Files.writeString(tempDir.resolve("append.txt"), "hello ");
        OverlayFs fs = createOverlay();

        fs.appendFile("/home/user/project/append.txt",
            new IFileSystem.StringContent("world"), WriteFileOptions.utf8()).join();

        String content = fs.readFile("/home/user/project/append.txt").join();
        assertEquals("hello world", content);

        // Real file unchanged
        assertEquals("hello ", Files.readString(tempDir.resolve("append.txt")));
    }

    @Test
    void testAppendToNewFile() throws Exception {
        OverlayFs fs = createOverlay();
        fs.appendFile("/home/user/project/new-append.txt",
            new IFileSystem.StringContent("content"), WriteFileOptions.utf8()).join();

        String content = fs.readFile("/home/user/project/new-append.txt").join();
        assertEquals("content", content);
    }

    // ------------------------------------------------------------------
    // Copy and move
    // ------------------------------------------------------------------

    @Test
    void testCp() throws Exception {
        Files.writeString(tempDir.resolve("source.txt"), "source content");
        OverlayFs fs = createOverlay();

        fs.cp("/home/user/project/source.txt", "/home/user/project/dest.txt", null).join();

        String content = fs.readFile("/home/user/project/dest.txt").join();
        assertEquals("source content", content);
    }

    @Test
    void testMv() throws Exception {
        Files.writeString(tempDir.resolve("mv-source.txt"), "move me");
        OverlayFs fs = createOverlay();

        fs.mv("/home/user/project/mv-source.txt", "/home/user/project/mv-dest.txt").join();

        assertFalse(fs.exists("/home/user/project/mv-source.txt").join());
        assertTrue(fs.exists("/home/user/project/mv-dest.txt").join());
        assertEquals("move me", fs.readFile("/home/user/project/mv-dest.txt").join());
    }

    // ------------------------------------------------------------------
    // Stat and lstat
    // ------------------------------------------------------------------

    @Test
    void testStatRealFile() throws Exception {
        Files.writeString(tempDir.resolve("stat.txt"), "stat me");
        OverlayFs fs = createOverlay();

        FsStat stat = fs.stat("/home/user/project/stat.txt").join();
        assertTrue(stat.isFile());
        assertFalse(stat.isDirectory());
    }

    @Test
    void testLstatMemorySymlink() {
        OverlayFs fs = new OverlayFs(
            new OverlayFs.OverlayFsOptions(tempDir.toString(), null, false, 0, true));
        fs.symlink("target", "/home/user/project/sym.txt").join();

        FsStat stat = fs.lstat("/home/user/project/sym.txt").join();
        assertTrue(stat.isSymbolicLink());
    }

    // ------------------------------------------------------------------
    // Exists
    // ------------------------------------------------------------------

    @Test
    void testExistsRealFile() throws Exception {
        Files.writeString(tempDir.resolve("exists.txt"), "yes");
        OverlayFs fs = createOverlay();

        assertTrue(fs.exists("/home/user/project/exists.txt").join());
    }

    @Test
    void testExistsAfterDelete() throws Exception {
        Files.writeString(tempDir.resolve("deleted-exists.txt"), "no");
        OverlayFs fs = createOverlay();
        fs.rm("/home/user/project/deleted-exists.txt").join();

        assertFalse(fs.exists("/home/user/project/deleted-exists.txt").join());
    }

    // ------------------------------------------------------------------
    // Chmod and utimes
    // ------------------------------------------------------------------

    @Test
    void testChmodCopiesUp() throws Exception {
        Files.writeString(tempDir.resolve("chmod.txt"), "chmod me");
        OverlayFs fs = createOverlay();

        fs.chmod("/home/user/project/chmod.txt", 0777).join();

        FsStat stat = fs.stat("/home/user/project/chmod.txt").join();
        assertEquals(0777, stat.mode());
    }

    @Test
    void testUtimesCopiesUp() throws Exception {
        Files.writeString(tempDir.resolve("utimes.txt"), "utimes");
        OverlayFs fs = createOverlay();

        java.time.Instant newTime = java.time.Instant.parse("2020-01-01T00:00:00Z");
        fs.utimes("/home/user/project/utimes.txt", newTime, newTime).join();

        FsStat stat = fs.stat("/home/user/project/utimes.txt").join();
        assertEquals(newTime, stat.mtime());
    }

    // ------------------------------------------------------------------
    // Readlink
    // ------------------------------------------------------------------

    @Test
    void testReadlinkMemorySymlink() {
        OverlayFs fs = new OverlayFs(
            new OverlayFs.OverlayFsOptions(tempDir.toString(), null, false, 0, true));
        fs.symlink("my-target", "/home/user/project/readlink.txt").join();

        String target = fs.readlink("/home/user/project/readlink.txt").join();
        assertEquals("my-target", target);
    }

    // ------------------------------------------------------------------
    // Max file size
    // ------------------------------------------------------------------

    @Test
    void testMaxFileReadSize() throws Exception {
        Files.writeString(tempDir.resolve("big.txt"), "x".repeat(100));
        OverlayFs fs = new OverlayFs(
            new OverlayFs.OverlayFsOptions(tempDir.toString(), null, false, 50, false));

        assertThrowsFs(FsException.class, () ->
            fs.readFile("/home/user/project/big.txt").join()
        );
    }

    // ------------------------------------------------------------------
    // Path security
    // ------------------------------------------------------------------

    @Test
    void testPathEscapingBlocked() {
        OverlayFs fs = createOverlay();
        // Try to access a path outside the root using .. traversal
        assertFalse(fs.exists("/home/user/project/../../../../../etc/passwd").join());
    }

    // ------------------------------------------------------------------
    // Realpath
    // ------------------------------------------------------------------

    @Test
    void testRealpathResolvesSymlinks() {
        OverlayFs fs = new OverlayFs(
            new OverlayFs.OverlayFsOptions(tempDir.toString(), null, false, 0, true));
        fs.writeFile("/home/user/project/target.txt",
            new IFileSystem.StringContent("target"), WriteFileOptions.utf8()).join();
        fs.symlink("target.txt", "/home/user/project/link.txt").join();

        String resolved = fs.realpath("/home/user/project/link.txt").join();
        assertEquals("/home/user/project/target.txt", resolved);
    }

    // ------------------------------------------------------------------
    // Resolve path
    // ------------------------------------------------------------------

    @Test
    void testResolvePath() {
        OverlayFs fs = createOverlay();
        assertEquals("/home/user/project/sub/file.txt",
            fs.resolvePath("/home/user/project", "sub/file.txt"));
    }
}
