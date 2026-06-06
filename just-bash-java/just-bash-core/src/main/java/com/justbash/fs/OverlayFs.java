package com.justbash.fs;

import com.justbash.encoding.ByteString;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * OverlayFs - Copy-on-write filesystem backed by a real directory.
 *
 * <p>Reads come from the real filesystem, writes go to an in-memory layer.
 * Changes don't persist to disk and can't escape the root directory.
 *
 * <p>Security: Symlinks are blocked by default (allowSymlinks: false).
 */
public class OverlayFs implements IFileSystem {

    private static final String DEFAULT_MOUNT_POINT = "/home/user/project";
    private static final long DEFAULT_MAX_FILE_READ_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int DEFAULT_DIR_MODE = 0755;
    private static final int DEFAULT_FILE_MODE = 0644;
    private static final int MAX_SYMLINK_DEPTH = 40;

    private final Path root;
    private final String mountPoint;
    private final boolean readOnly;
    private final long maxFileReadSize;
    private final boolean allowSymlinks;
    private final Map<String, MemoryEntry> memory = new HashMap<>();
    private final Set<String> deleted = new HashSet<>();

    public OverlayFs(OverlayFsOptions options) {
        this.root = Path.of(options.root()).toAbsolutePath().normalize();
        this.mountPoint = options.mountPoint() != null
            ? normalizeMountPoint(options.mountPoint())
            : DEFAULT_MOUNT_POINT;
        this.readOnly = options.readOnly();
        this.maxFileReadSize = options.maxFileReadSize() > 0
            ? options.maxFileReadSize()
            : DEFAULT_MAX_FILE_READ_SIZE;
        this.allowSymlinks = options.allowSymlinks();

        if (!Files.isDirectory(this.root)) {
            throw new IllegalArgumentException(
                "OverlayFs root must be a directory: " + this.root);
        }

        createMountPointDirs();
    }

    public String getMountPoint() {
        return mountPoint;
    }

    // ------------------------------------------------------------------
    // IFileSystem implementation
    // ------------------------------------------------------------------

    @Override
    public CompletableFuture<String> readFile(String path, ReadFileOptions options) {
        return readFileBuffer(path).thenApply(bytes -> {
            String encoding = options != null ? options.encoding() : "utf8";
            if ("binary".equals(encoding)) {
                char[] chars = new char[bytes.length];
                for (int i = 0; i < bytes.length; i++) {
                    chars[i] = (char) (bytes[i] & 0xFF);
                }
                return new String(chars);
            }
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        });
    }

    @Override
    public CompletableFuture<ByteString> readFileBytes(String path) {
        return readFileBuffer(path).thenApply(bytes -> {
            char[] chars = new char[bytes.length];
            for (int i = 0; i < bytes.length; i++) {
                chars[i] = (char) (bytes[i] & 0xFF);
            }
            return ByteString.fromLatin1(new String(chars));
        });
    }

    @Override
    public CompletableFuture<byte[]> readFileBuffer(String path) {
        return CompletableFuture.supplyAsync(() -> readFileBufferSync(path, new HashSet<>()));
    }

    private byte[] readFileBufferSync(String path, Set<String> seen) {
        String normalized = normalizePath(path);

        if (seen.contains(normalized)) {
            throw new FsException("ELOOP: too many levels of symbolic links, open '" + path + "'");
        }
        seen.add(normalized);

        if (deleted.contains(normalized)) {
            throw new FsException("ENOENT: no such file or directory, open '" + path + "'");
        }

        MemoryEntry entry = memory.get(normalized);
        if (entry != null) {
            if (entry instanceof MemorySymlinkEntry sym) {
                String target = resolveSymlink(normalized, sym.target());
                return readFileBufferSync(target, seen);
            }
            if (!(entry instanceof MemoryFileEntry file)) {
                throw new FsException("EISDIR: illegal operation on a directory, read '" + path + "'");
            }
            return file.content();
        }

        Path realPath = toRealPath(normalized);
        if (realPath == null) {
            throw new FsException("ENOENT: no such file or directory, open '" + path + "'");
        }

        Path canonical = resolveRealPath(realPath);
        if (canonical == null) {
            throw new FsException("ENOENT: no such file or directory, open '" + path + "'");
        }

        try {
            java.nio.file.attribute.BasicFileAttributes attrs = Files.readAttributes(
                canonical, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);

            if (attrs.isSymbolicLink()) {
                if (!allowSymlinks) {
                    throw new FsException("ENOENT: no such file or directory, open '" + path + "'");
                }
                String rawTarget = Files.readSymbolicLink(canonical).toString();
                String virtualTarget = realTargetToVirtual(normalized, rawTarget);
                String resolvedTarget = resolveSymlink(normalized, virtualTarget);
                return readFileBufferSync(resolvedTarget, seen);
            }

            if (attrs.isDirectory()) {
                throw new FsException("EISDIR: illegal operation on a directory, read '" + path + "'");
            }

            long size = attrs.size();
            if (maxFileReadSize > 0 && size > maxFileReadSize) {
                throw new FsException("EFBIG: file too large, read '" + path + "' (" + size + " bytes, max " + maxFileReadSize + ")");
            }

            return Files.readAllBytes(canonical);
        } catch (NoSuchFileException e) {
            throw new FsException("ENOENT: no such file or directory, open '" + path + "'");
        } catch (IOException e) {
            throw new FsException("ENOENT: no such file or directory, open '" + path + "'");
        }
    }

    @Override
    public CompletableFuture<Void> writeFile(String path, FileContent content, WriteFileOptions options) {
        return CompletableFuture.runAsync(() -> {
            assertWritable("write '" + path + "'");
            String normalized = normalizePath(path);
            ensureParentDirs(normalized);

            byte[] bytes;
            if (content instanceof StringContent sc) {
                bytes = sc.value().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            } else if (content instanceof ByteArrayContent bc) {
                bytes = bc.value();
            } else {
                bytes = new byte[0];
            }

            memory.put(normalized, new MemoryFileEntry(bytes, DEFAULT_FILE_MODE, Instant.now()));
            deleted.remove(normalized);
        });
    }

    @Override
    public CompletableFuture<Void> appendFile(String path, FileContent content, WriteFileOptions options) {
        return CompletableFuture.runAsync(() -> {
            assertWritable("append '" + path + "'");
            String normalized = normalizePath(path);

            byte[] newBytes;
            if (content instanceof StringContent sc) {
                newBytes = sc.value().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            } else if (content instanceof ByteArrayContent bc) {
                newBytes = bc.value();
            } else {
                newBytes = new byte[0];
            }

            byte[] existing;
            try {
                existing = readFileBufferSync(path, new HashSet<>());
            } catch (FsException e) {
                existing = new byte[0];
            }

            byte[] combined = new byte[existing.length + newBytes.length];
            System.arraycopy(existing, 0, combined, 0, existing.length);
            System.arraycopy(newBytes, 0, combined, existing.length, newBytes.length);

            ensureParentDirs(normalized);
            memory.put(normalized, new MemoryFileEntry(combined, DEFAULT_FILE_MODE, Instant.now()));
            deleted.remove(normalized);
        });
    }

    @Override
    public CompletableFuture<Boolean> exists(String path) {
        return CompletableFuture.supplyAsync(() -> existsInOverlay(path));
    }

    @Override
    public CompletableFuture<FsStat> stat(String path) {
        return CompletableFuture.supplyAsync(() -> statSync(path, new HashSet<>()));
    }

    private FsStat statSync(String path, Set<String> seen) {
        String normalized = normalizePath(path);

        if (seen.contains(normalized)) {
            throw new FsException("ELOOP: too many levels of symbolic links, stat '" + path + "'");
        }
        seen.add(normalized);

        if (deleted.contains(normalized)) {
            throw new FsException("ENOENT: no such file or directory, stat '" + path + "'");
        }

        MemoryEntry entry = memory.get(normalized);
        if (entry != null) {
            if (entry instanceof MemorySymlinkEntry sym) {
                String target = resolveSymlink(normalized, sym.target());
                return statSync(target, seen);
            }
            int size = 0;
            if (entry instanceof MemoryFileEntry file) {
                size = file.content().length;
            }
            return new FsStat(
                entry instanceof MemoryFileEntry,
                entry instanceof MemoryDirEntry,
                false,
                entry.mode(),
                size,
                entry.mtime()
            );
        }

        Path realPath = toRealPath(normalized);
        if (realPath == null) {
            throw new FsException("ENOENT: no such file or directory, stat '" + path + "'");
        }

        Path canonical = resolveRealPath(realPath);
        if (canonical == null) {
            throw new FsException("ENOENT: no such file or directory, stat '" + path + "'");
        }

        try {
            java.nio.file.attribute.BasicFileAttributes attrs = Files.readAttributes(
                canonical, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);

            if (attrs.isSymbolicLink()) {
                if (!allowSymlinks) {
                    throw new FsException("ENOENT: no such file or directory, stat '" + path + "'");
                }
                String rawTarget = Files.readSymbolicLink(canonical).toString();
                String virtualTarget = realTargetToVirtual(normalized, rawTarget);
                String resolvedTarget = resolveSymlink(normalized, virtualTarget);
                return statSync(resolvedTarget, seen);
            }

            return new FsStat(
                attrs.isRegularFile(),
                attrs.isDirectory(),
                attrs.isSymbolicLink(),
                DEFAULT_FILE_MODE,
                attrs.size(),
                Instant.ofEpochMilli(attrs.lastModifiedTime().toMillis())
            );
        } catch (NoSuchFileException e) {
            throw new FsException("ENOENT: no such file or directory, stat '" + path + "'");
        } catch (IOException e) {
            throw new FsException("ENOENT: no such file or directory, stat '" + path + "'");
        }
    }

    @Override
    public CompletableFuture<FsStat> lstat(String path) {
        return CompletableFuture.supplyAsync(() -> {
            String normalized = normalizePath(path);

            if (deleted.contains(normalized)) {
                throw new FsException("ENOENT: no such file or directory, lstat '" + path + "'");
            }

            MemoryEntry entry = memory.get(normalized);
            if (entry != null) {
                int size = 0;
                if (entry instanceof MemoryFileEntry file) {
                    size = file.content().length;
                } else if (entry instanceof MemorySymlinkEntry sym) {
                    size = sym.target().length();
                }
                return new FsStat(
                    entry instanceof MemoryFileEntry,
                    entry instanceof MemoryDirEntry,
                    entry instanceof MemorySymlinkEntry,
                    entry.mode(),
                    size,
                    entry.mtime()
                );
            }

            Path realPath = toRealPath(normalized);
            if (realPath == null) {
                throw new FsException("ENOENT: no such file or directory, lstat '" + path + "'");
            }

            Path canonical = resolveRealPathParent(realPath);
            if (canonical == null) {
                throw new FsException("ENOENT: no such file or directory, lstat '" + path + "'");
            }

            try {
                java.nio.file.attribute.BasicFileAttributes attrs = Files.readAttributes(
                    canonical, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                return new FsStat(
                    attrs.isRegularFile(),
                    attrs.isDirectory(),
                    attrs.isSymbolicLink(),
                    DEFAULT_FILE_MODE,
                    attrs.size(),
                    Instant.ofEpochMilli(attrs.lastModifiedTime().toMillis())
                );
            } catch (NoSuchFileException e) {
                throw new FsException("ENOENT: no such file or directory, lstat '" + path + "'");
            } catch (IOException e) {
                throw new FsException("ENOENT: no such file or directory, lstat '" + path + "'");
            }
        });
    }

    @Override
    public CompletableFuture<Void> mkdir(String path, MkdirOptions options) {
        return CompletableFuture.runAsync(() -> {
            assertWritable("mkdir '" + path + "'");
            String normalized = normalizePath(path);

            boolean exists = existsInOverlay(normalized);
            if (exists) {
                if (options == null || !options.recursive()) {
                    throw new FsException("EEXIST: file already exists, mkdir '" + path + "'");
                }
                return;
            }

            String parent = dirname(normalized);
            if (!parent.equals("/")) {
                boolean parentExists = existsInOverlay(parent);
                if (!parentExists) {
                    if (options != null && options.recursive()) {
                        mkdir(parent, new MkdirOptions(true)).join();
                    } else {
                        throw new FsException("ENOENT: no such file or directory, mkdir '" + path + "'");
                    }
                }
            }

            memory.put(normalized, new MemoryDirEntry(DEFAULT_DIR_MODE, Instant.now()));
            deleted.remove(normalized);
        });
    }

    @Override
    public CompletableFuture<List<String>> readdir(String path) {
        return CompletableFuture.supplyAsync(() -> {
            String normalized = normalizePath(path);
            Map<String, DirentEntry> entries = readdirCore(path, normalized);
            List<String> names = new ArrayList<>(entries.keySet());
            Collections.sort(names);
            return names;
        });
    }

    @Override
    public CompletableFuture<List<DirentEntry>> readdirWithFileTypes(String path) {
        return CompletableFuture.supplyAsync(() -> {
            String normalized = normalizePath(path);
            Map<String, DirentEntry> entries = readdirCore(path, normalized);
            List<DirentEntry> result = new ArrayList<>(entries.values());
            result.sort((a, b) -> a.name().compareTo(b.name()));
            return result;
        });
    }

    private Map<String, DirentEntry> readdirCore(String path, String normalized) {
        if (deleted.contains(normalized)) {
            throw new FsException("ENOENT: no such file or directory, scandir '" + path + "'");
        }

        Map<String, DirentEntry> entries = new HashMap<>();
        Set<String> deletedChildren = new HashSet<>();

        String prefix = normalized.equals("/") ? "/" : normalized + "/";
        for (String deletedPath : deleted) {
            if (deletedPath.startsWith(prefix)) {
                String rest = deletedPath.substring(prefix.length());
                int slashIdx = rest.indexOf('/');
                if (slashIdx == -1 && !rest.isEmpty()) {
                    deletedChildren.add(rest);
                }
            }
        }

        for (Map.Entry<String, MemoryEntry> e : memory.entrySet()) {
            String memPath = e.getKey();
            if (memPath.equals(normalized)) continue;
            if (memPath.startsWith(prefix)) {
                String rest = memPath.substring(prefix.length());
                int slashIdx = rest.indexOf('/');
                if (slashIdx == -1 && !rest.isEmpty() && !deletedChildren.contains(rest)) {
                    MemoryEntry entry = e.getValue();
                    entries.put(rest, new DirentEntry(
                        rest,
                        entry instanceof MemoryFileEntry,
                        entry instanceof MemoryDirEntry,
                        entry instanceof MemorySymlinkEntry
                    ));
                }
            }
        }

        Path realPath = toRealPath(normalized);
        if (realPath != null) {
            Path canonical = resolveRealPath(realPath);
            if (canonical != null && Files.isDirectory(canonical)) {
                try {
                    try (var stream = Files.newDirectoryStream(canonical)) {
                        for (Path child : stream) {
                            String name = child.getFileName().toString();
                            if (!deletedChildren.contains(name) && !entries.containsKey(name)) {
                                java.nio.file.attribute.BasicFileAttributes attrs = Files.readAttributes(
                                    child, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                                entries.put(name, new DirentEntry(
                                    name,
                                    attrs.isRegularFile(),
                                    attrs.isDirectory(),
                                    attrs.isSymbolicLink()
                                ));
                            }
                        }
                    }
                } catch (NoSuchFileException | java.nio.file.NotDirectoryException e) {
                    // Directory doesn't exist on real fs - that's ok if we have memory entries
                } catch (IOException e) {
                    // Ignore other IO errors
                }
            }
        }

        return entries;
    }

    @Override
    public CompletableFuture<Void> rm(String path, RmOptions options) {
        return CompletableFuture.runAsync(() -> {
            assertWritable("rm '" + path + "'");
            String normalized = normalizePath(path);

            boolean exists = existsInOverlay(normalized);
            if (!exists) {
                if (options != null && options.force()) return;
                throw new FsException("ENOENT: no such file or directory, rm '" + path + "'");
            }

            try {
                FsStat st = statSync(normalized, new HashSet<>());
                if (st.isDirectory()) {
                    List<String> children = readdir(normalized).join();
                    if (!children.isEmpty()) {
                        if (options == null || !options.recursive()) {
                            throw new FsException("ENOTEMPTY: directory not empty, rm '" + path + "'");
                        }
                        for (String child : children) {
                            String childPath = normalized.equals("/") ? "/" + child : normalized + "/" + child;
                            rm(childPath, options).join();
                        }
                    }
                }
            } catch (FsException e) {
                if (e.getMessage().contains("ENOTEMPTY") || e.getMessage().contains("EISDIR")) {
                    throw e;
                }
                // If stat/readdir fails, proceed to mark as deleted
            }

            memory.remove(normalized);
            if (existsOnRealFs(normalized)) {
                deleted.add(normalized);
            }
        });
    }

    @Override
    public CompletableFuture<Void> cp(String src, String dest, CpOptions options) {
        return CompletableFuture.runAsync(() -> {
            assertWritable("cp '" + dest + "'");
            String destNorm = normalizePath(dest);
            ensureParentDirs(destNorm);

            try {
                byte[] content = readFileBufferSync(src, new HashSet<>());
                memory.put(destNorm, new MemoryFileEntry(content, DEFAULT_FILE_MODE, Instant.now()));
                deleted.remove(destNorm);
            } catch (FsException e) {
                throw new FsException("ENOENT: no such file or directory, cp '" + src + "'");
            }
        });
    }

    @Override
    public CompletableFuture<Void> mv(String src, String dest) {
        return CompletableFuture.runAsync(() -> {
            assertWritable("mv '" + dest + "'");
            String srcNorm = normalizePath(src);
            String destNorm = normalizePath(dest);
            ensureParentDirs(destNorm);

            // Try memory layer first
            MemoryEntry entry = memory.remove(srcNorm);
            if (entry != null) {
                memory.put(destNorm, entry);
                deleted.remove(destNorm);
                return;
            }

            // Fall back to real fs: read from real, write to memory, mark source deleted
            try {
                byte[] content = readFileBufferSync(src, new HashSet<>());
                memory.put(destNorm, new MemoryFileEntry(content, DEFAULT_FILE_MODE, Instant.now()));
                deleted.remove(destNorm);
                if (existsOnRealFs(srcNorm)) {
                    deleted.add(srcNorm);
                }
            } catch (FsException e) {
                throw new FsException("ENOENT: no such file or directory, mv '" + src + "'");
            }
        });
    }

    @Override
    public String resolvePath(String base, String path) {
        if (path.startsWith("/")) return normalizePath(path);
        return normalizePath(base + "/" + path);
    }

    @Override
    public List<String> getAllPaths() {
        List<String> paths = new ArrayList<>();
        paths.addAll(memory.keySet());

        // Also include real fs paths
        try {
            Files.walk(root).forEach(p -> {
                String relative = root.relativize(p).toString().replace('\\', '/');
                if (relative.isEmpty()) return;
                String virtualPath = mountPoint.equals("/") ? "/" + relative : mountPoint + "/" + relative;
                virtualPath = normalizePath(virtualPath);
                if (!memory.containsKey(virtualPath) && !deleted.contains(virtualPath)) {
                    paths.add(virtualPath);
                }
            });
        } catch (IOException e) {
            // Ignore
        }
        return paths;
    }

    @Override
    public CompletableFuture<Void> chmod(String path, int mode) {
        return CompletableFuture.runAsync(() -> {
            assertWritable("chmod '" + path + "'");
            String normalized = normalizePath(path);

            MemoryEntry old = memory.get(normalized);
            if (old != null) {
                memory.put(normalized, withMode(old, mode));
                return;
            }

            // Copy-up from real fs
            try {
                byte[] content = readFileBufferSync(path, new HashSet<>());
                memory.put(normalized, new MemoryFileEntry(content, mode, Instant.now()));
            } catch (FsException e) {
                throw new FsException("ENOENT: no such file or directory, chmod '" + path + "'");
            }
        });
    }

    @Override
    public CompletableFuture<Void> symlink(String target, String linkPath) {
        return CompletableFuture.runAsync(() -> {
            assertWritable("symlink '" + linkPath + "'");
            if (!allowSymlinks) {
                throw new FsException("EPERM: operation not permitted, symlink '" + linkPath + "'");
            }
            String normalized = normalizePath(linkPath);
            ensureParentDirs(normalized);
            memory.put(normalized, new MemorySymlinkEntry(target, 0777, Instant.now()));
            deleted.remove(normalized);
        });
    }

    @Override
    public CompletableFuture<Void> link(String existingPath, String newPath) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<String> readlink(String path) {
        return CompletableFuture.supplyAsync(() -> {
            String normalized = normalizePath(path);

            MemoryEntry entry = memory.get(normalized);
            if (entry instanceof MemorySymlinkEntry sym) {
                return sym.target();
            }

            Path realPath = toRealPath(normalized);
            if (realPath == null) {
                throw new FsException("ENOENT: no such file or directory, readlink '" + path + "'");
            }

            Path canonical = resolveRealPathParent(realPath);
            if (canonical == null) {
                throw new FsException("ENOENT: no such file or directory, readlink '" + path + "'");
            }

            try {
                java.nio.file.attribute.BasicFileAttributes attrs = Files.readAttributes(
                    canonical, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!attrs.isSymbolicLink()) {
                    throw new FsException("EINVAL: invalid argument, readlink '" + path + "'");
                }
                return Files.readSymbolicLink(canonical).toString();
            } catch (NoSuchFileException e) {
                throw new FsException("ENOENT: no such file or directory, readlink '" + path + "'");
            } catch (IOException e) {
                throw new FsException("ENOENT: no such file or directory, readlink '" + path + "'");
            }
        });
    }

    @Override
    public CompletableFuture<String> realpath(String path) {
        return CompletableFuture.supplyAsync(() -> {
            String normalized = normalizePath(path);
            // Follow symlinks up to MAX_SYMLINK_DEPTH
            Set<String> seen = new HashSet<>();
            String current = normalized;
            for (int i = 0; i < MAX_SYMLINK_DEPTH; i++) {
                if (seen.contains(current)) {
                    throw new FsException("ELOOP: too many levels of symbolic links, realpath '" + path + "'");
                }
                seen.add(current);

                MemoryEntry entry = memory.get(current);
                if (entry instanceof MemorySymlinkEntry sym) {
                    current = resolveSymlink(current, sym.target());
                    continue;
                }

                Path realPath = toRealPath(current);
                if (realPath != null) {
                    Path canonical = resolveRealPath(realPath);
                    if (canonical != null) {
                        try {
                            java.nio.file.attribute.BasicFileAttributes attrs = Files.readAttributes(
                                canonical, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                            if (attrs.isSymbolicLink()) {
                                if (!allowSymlinks) {
                                    break;
                                }
                                String rawTarget = Files.readSymbolicLink(canonical).toString();
                                String virtualTarget = realTargetToVirtual(current, rawTarget);
                                current = resolveSymlink(current, virtualTarget);
                                continue;
                            }
                        } catch (IOException e) {
                            break;
                        }
                    }
                }
                break;
            }
            return normalizePath(current);
        });
    }

    @Override
    public CompletableFuture<Void> utimes(String path, Instant atime, Instant mtime) {
        return CompletableFuture.runAsync(() -> {
            assertWritable("utimes '" + path + "'");
            String normalized = normalizePath(path);

            MemoryEntry old = memory.get(normalized);
            if (old != null) {
                memory.put(normalized, withMtime(old, mtime));
                return;
            }

            // Copy-up from real fs
            try {
                byte[] content = readFileBufferSync(path, new HashSet<>());
                memory.put(normalized, new MemoryFileEntry(content, DEFAULT_FILE_MODE, mtime));
            } catch (FsException e) {
                throw new FsException("ENOENT: no such file or directory, utimes '" + path + "'");
            }
        });
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    private void assertWritable(String operation) {
        if (readOnly) {
            throw new FsException("EROFS: read-only file system, " + operation);
        }
    }

    private void createMountPointDirs() {
        String[] parts = mountPoint.split("/");
        String current = "";
        for (String part : parts) {
            if (part.isEmpty()) continue;
            current += "/" + part;
            if (!memory.containsKey(current)) {
                memory.put(current, new MemoryDirEntry(DEFAULT_DIR_MODE, Instant.now()));
            }
        }
        if (!memory.containsKey("/")) {
            memory.put("/", new MemoryDirEntry(DEFAULT_DIR_MODE, Instant.now()));
        }
    }

    private String getRelativeToMount(String normalizedPath) {
        if (mountPoint.equals("/")) {
            return normalizedPath;
        }
        if (normalizedPath.equals(mountPoint)) {
            return "/";
        }
        if (normalizedPath.startsWith(mountPoint + "/")) {
            return normalizedPath.substring(mountPoint.length());
        }
        return null;
    }

    private Path toRealPath(String virtualPath) {
        String normalized = normalizePath(virtualPath);
        String relative = getRelativeToMount(normalized);
        if (relative == null) {
            return null;
        }
        if (relative.equals("/")) {
            relative = "";
        } else if (relative.startsWith("/")) {
            relative = relative.substring(1);
        }

        Path realPath;
        if (relative.isEmpty()) {
            realPath = root;
        } else {
            realPath = root.resolve(relative).normalize();
        }

        // Security: ensure path doesn't escape root
        if (!realPath.startsWith(root)) {
            return null;
        }

        return realPath;
    }

    private Path resolveRealPath(Path realPath) {
        if (realPath == null) return null;
        if (!realPath.startsWith(root)) return null;

        if (!allowSymlinks) {
            // Check that no component is a symlink
            Path current = root;
            int nameCount = root.getNameCount();
            int targetCount = realPath.getNameCount();
            for (int i = nameCount; i < targetCount; i++) {
                current = current.resolve(realPath.getName(i));
                try {
                    if (Files.isSymbolicLink(current)) {
                        return null;
                    }
                } catch (SecurityException e) {
                    return null;
                }
            }
        }

        return realPath;
    }

    private Path resolveRealPathParent(Path realPath) {
        if (realPath == null) return null;
        Path parent = realPath.getParent();
        if (parent == null) return null;
        Path canonicalParent = resolveRealPath(parent);
        if (canonicalParent == null) return null;
        return canonicalParent.resolve(realPath.getFileName());
    }

    private boolean existsInOverlay(String path) {
        String normalized = normalizePath(path);

        if (deleted.contains(normalized)) {
            return false;
        }

        if (memory.containsKey(normalized)) {
            return true;
        }

        Path realPath = toRealPath(normalized);
        if (realPath == null) {
            return false;
        }

        Path canonical = resolveRealPathParent(realPath);
        if (canonical == null) {
            return false;
        }

        return Files.exists(canonical, LinkOption.NOFOLLOW_LINKS);
    }

    private boolean existsOnRealFs(String path) {
        String normalized = normalizePath(path);
        Path realPath = toRealPath(normalized);
        if (realPath == null) return false;
        Path canonical = resolveRealPathParent(realPath);
        if (canonical == null) return false;
        return Files.exists(canonical, LinkOption.NOFOLLOW_LINKS);
    }

    private String resolveSymlink(String symlinkPath, String target) {
        if (target.startsWith("/")) {
            return normalizePath(target);
        }
        String dir = dirname(symlinkPath);
        return normalizePath(dir + "/" + target);
    }

    private String realTargetToVirtual(String symlinkVirtualPath, String rawTarget) {
        if (!rawTarget.startsWith("/")) {
            return rawTarget;
        }
        Path targetPath = Path.of(rawTarget).toAbsolutePath().normalize();
        if (targetPath.startsWith(root)) {
            String relative = root.relativize(targetPath).toString().replace('\\', '/');
            if (relative.isEmpty()) {
                return mountPoint;
            }
            if (mountPoint.equals("/")) {
                return "/" + relative;
            }
            return mountPoint + "/" + relative;
        }
        // Outside root - return basename
        return targetPath.getFileName().toString();
    }

    private void ensureParentDirs(String path) {
        String dir = dirname(path);
        if (dir.equals("/")) return;
        if (!memory.containsKey(dir) && !existsInOverlay(dir)) {
            ensureParentDirs(dir);
            memory.put(dir, new MemoryDirEntry(DEFAULT_DIR_MODE, Instant.now()));
        }
        deleted.remove(dir);
    }

    private static String normalizePath(String path) {
        String normalized = path.replaceAll("/+", "/");
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isEmpty() ? "/" : normalized;
    }

    private static String dirname(String path) {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash <= 0) return "/";
        return path.substring(0, lastSlash);
    }

    private static String normalizeMountPoint(String mp) {
        String normalized = mp.replaceAll("/+", "/");
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isEmpty() ? "/" : normalized;
    }

    private static MemoryEntry withMode(MemoryEntry entry, int mode) {
        return switch (entry) {
            case MemoryFileEntry f -> new MemoryFileEntry(f.content(), mode, f.mtime());
            case MemoryDirEntry d -> new MemoryDirEntry(mode, d.mtime());
            case MemorySymlinkEntry s -> new MemorySymlinkEntry(s.target(), mode, s.mtime());
        };
    }

    private static MemoryEntry withMtime(MemoryEntry entry, Instant mtime) {
        return switch (entry) {
            case MemoryFileEntry f -> new MemoryFileEntry(f.content(), f.mode(), mtime);
            case MemoryDirEntry d -> new MemoryDirEntry(d.mode(), mtime);
            case MemorySymlinkEntry s -> new MemorySymlinkEntry(s.target(), s.mode(), mtime);
        };
    }

    // ------------------------------------------------------------------
    // Memory entry types
    // ------------------------------------------------------------------

    public sealed interface MemoryEntry {
        int mode();
        Instant mtime();
    }

    public record MemoryFileEntry(byte[] content, int mode, Instant mtime) implements MemoryEntry {}

    public record MemoryDirEntry(int mode, Instant mtime) implements MemoryEntry {}

    public record MemorySymlinkEntry(String target, int mode, Instant mtime) implements MemoryEntry {}

    // ------------------------------------------------------------------
    // Options record
    // ------------------------------------------------------------------

    public record OverlayFsOptions(
        String root,
        String mountPoint,
        boolean readOnly,
        long maxFileReadSize,
        boolean allowSymlinks
    ) {
        public OverlayFsOptions(String root) {
            this(root, null, false, 0, false);
        }
    }
}
