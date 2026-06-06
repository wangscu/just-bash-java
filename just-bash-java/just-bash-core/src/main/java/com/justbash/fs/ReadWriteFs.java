package com.justbash.fs;

import com.justbash.encoding.ByteString;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * ReadWriteFs - Direct wrapper around the real filesystem.
 *
 * <p>All operations go directly to the underlying Java NIO filesystem.
 * Paths are relative to the configured root directory.
 *
 * <p>Security: Symlinks are blocked by default (allowSymlinks: false).
 * All real-FS access goes through resolveAndValidate() / validateParent()
 * gates which detect symlink traversal via path comparison.
 */
public class ReadWriteFs implements IFileSystem {

    private static final long DEFAULT_MAX_FILE_READ_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int DEFAULT_DIR_MODE = 0755;
    private static final int DEFAULT_FILE_MODE = 0644;

    private final Path root;
    private final Path canonicalRoot;
    private final long maxFileReadSize;
    private final boolean allowSymlinks;

    public ReadWriteFs(ReadWriteFsOptions options) {
        this.root = Path.of(options.root()).toAbsolutePath().normalize();
        this.maxFileReadSize = options.maxFileReadSize() > 0
            ? options.maxFileReadSize()
            : DEFAULT_MAX_FILE_READ_SIZE;
        this.allowSymlinks = options.allowSymlinks();

        if (!Files.exists(this.root)) {
            throw new IllegalArgumentException(
                "ReadWriteFs root does not exist: " + this.root);
        }
        if (!Files.isDirectory(this.root)) {
            throw new IllegalArgumentException(
                "ReadWriteFs root is not a directory: " + this.root);
        }

        // Compute canonical root (resolves symlinks like /var -> /private/var on macOS)
        try {
            this.canonicalRoot = this.root.toRealPath();
        } catch (IOException e) {
            throw new IllegalArgumentException(
                "ReadWriteFs root does not exist or is not accessible: " + this.root);
        }
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
        return CompletableFuture.supplyAsync(() -> {
            validatePath(path, "open");
            Path realPath = toRealPath(path);
            Path canonical = resolveAndValidate(realPath, path);

            try {
                // When symlinks are disabled, use NOFOLLOW_LINKS to prevent TOCTOU
                if (!allowSymlinks) {
                    java.nio.file.attribute.BasicFileAttributes attrs = Files.readAttributes(
                        canonical, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    if (attrs.isSymbolicLink()) {
                        throw new FsException("EACCES: permission denied, '" + path + "' is a symlink");
                    }
                    if (attrs.isDirectory()) {
                        throw new FsException("EISDIR: illegal operation on a directory, read '" + path + "'");
                    }

                    long size = attrs.size();
                    if (maxFileReadSize > 0 && size > maxFileReadSize) {
                        throw new FsException("EFBIG: file too large, read '" + path + "' (" + size + " bytes, max " + maxFileReadSize + ")");
                    }

                    return Files.readAllBytes(canonical);
                }

                // allowSymlinks: follow symlinks, check size
                long size = Files.size(canonical);
                if (maxFileReadSize > 0 && size > maxFileReadSize) {
                    throw new FsException("EFBIG: file too large, read '" + path + "' (" + size + " bytes, max " + maxFileReadSize + ")");
                }

                return Files.readAllBytes(canonical);
            } catch (NoSuchFileException e) {
                throw new FsException("ENOENT: no such file or directory, open '" + path + "'");
            } catch (AccessDeniedException e) {
                throw new FsException("EACCES: permission denied, open '" + path + "'");
            } catch (IOException e) {
                if (e.getMessage() != null && e.getMessage().contains("Is a directory")) {
                    throw new FsException("EISDIR: illegal operation on a directory, read '" + path + "'");
                }
                throw new FsException("ENOENT: no such file or directory, open '" + path + "'");
            }
        });
    }

    @Override
    public CompletableFuture<Void> writeFile(String path, FileContent content, WriteFileOptions options) {
        return CompletableFuture.runAsync(() -> {
            validatePath(path, "write");
            Path realPath = toRealPath(path);
            Path canonical = resolveAndValidate(realPath, path);

            byte[] bytes;
            if (content instanceof StringContent sc) {
                bytes = sc.value().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            } else if (content instanceof ByteArrayContent bc) {
                bytes = bc.value();
            } else {
                bytes = new byte[0];
            }

            // Ensure parent directory exists
            Path parent = canonical.getParent();
            if (parent != null) {
                try {
                    Files.createDirectories(parent);
                } catch (IOException e) {
                    throw new FsException("ENOENT: no such file or directory, write '" + path + "'");
                }
            }

            // Re-validate after mkdir to catch TOCTOU parent-swap attacks
            canonical = resolveAndValidate(realPath, path);

            try {
                Set<OpenOption> openOptions = new HashSet<>();
                openOptions.add(StandardOpenOption.WRITE);
                openOptions.add(StandardOpenOption.CREATE);
                openOptions.add(StandardOpenOption.TRUNCATE_EXISTING);

                OpenOption[] opts;
                if (!allowSymlinks) {
                    opts = new OpenOption[openOptions.size() + 1];
                    int i = 0;
                    for (OpenOption opt : openOptions) opts[i++] = opt;
                    opts[i] = LinkOption.NOFOLLOW_LINKS;
                } else {
                    opts = openOptions.toArray(new OpenOption[0]);
                }

                try (OutputStream out = Files.newOutputStream(canonical, opts)) {
                    out.write(bytes);
                }
            } catch (IOException e) {
                if (e.getMessage() != null && e.getMessage().contains("Is a directory")) {
                    throw new FsException("EISDIR: illegal operation on a directory, write '" + path + "'");
                }
                throw new FsException("ENOENT: no such file or directory, write '" + path + "'");
            }
        });
    }

    @Override
    public CompletableFuture<Void> appendFile(String path, FileContent content, WriteFileOptions options) {
        return CompletableFuture.runAsync(() -> {
            validatePath(path, "append");
            Path realPath = toRealPath(path);
            Path canonical = resolveAndValidate(realPath, path);

            byte[] bytes;
            if (content instanceof StringContent sc) {
                bytes = sc.value().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            } else if (content instanceof ByteArrayContent bc) {
                bytes = bc.value();
            } else {
                bytes = new byte[0];
            }

            // Ensure parent directory exists
            Path parent = canonical.getParent();
            if (parent != null) {
                try {
                    Files.createDirectories(parent);
                } catch (IOException e) {
                    throw new FsException("ENOENT: no such file or directory, append '" + path + "'");
                }
            }

            // Re-validate after mkdir
            canonical = resolveAndValidate(realPath, path);

            try {
                Set<OpenOption> openOptions = new HashSet<>();
                openOptions.add(StandardOpenOption.WRITE);
                openOptions.add(StandardOpenOption.CREATE);
                openOptions.add(StandardOpenOption.APPEND);

                OpenOption[] opts;
                if (!allowSymlinks) {
                    opts = new OpenOption[openOptions.size() + 1];
                    int i = 0;
                    for (OpenOption opt : openOptions) opts[i++] = opt;
                    opts[i] = LinkOption.NOFOLLOW_LINKS;
                } else {
                    opts = openOptions.toArray(new OpenOption[0]);
                }

                try (OutputStream out = Files.newOutputStream(canonical, opts)) {
                    out.write(bytes);
                }
            } catch (IOException e) {
                throw new FsException("ENOENT: no such file or directory, append '" + path + "'");
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> exists(String path) {
        return CompletableFuture.supplyAsync(() -> {
            if (path.contains("\0")) return false;
            Path realPath = toRealPath(path);
            try {
                Path canonical = resolveAndValidate(realPath, path);
                return Files.exists(canonical, LinkOption.NOFOLLOW_LINKS);
            } catch (Exception e) {
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<FsStat> stat(String path) {
        return CompletableFuture.supplyAsync(() -> {
            validatePath(path, "stat");
            Path realPath = toRealPath(path);
            Path canonical = resolveAndValidate(realPath, path);

            try {
                java.nio.file.attribute.BasicFileAttributes attrs = Files.readAttributes(
                    canonical, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);

                if (!allowSymlinks && attrs.isSymbolicLink()) {
                    throw new FsException("EACCES: permission denied, '" + path + "' is a symlink");
                }

                long size = attrs.size();
                Instant mtime = Instant.ofEpochMilli(attrs.lastModifiedTime().toMillis());

                // Try to get mode from POSIX attributes
                int mode = DEFAULT_FILE_MODE;
                try {
                    java.nio.file.attribute.PosixFileAttributes posix = Files.readAttributes(
                        canonical, java.nio.file.attribute.PosixFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    mode = posix.permissions().hashCode(); // rough approximation
                } catch (Exception e) {
                    // Not POSIX, use defaults
                }

                return new FsStat(
                    attrs.isRegularFile(),
                    attrs.isDirectory(),
                    attrs.isSymbolicLink(),
                    mode,
                    size,
                    mtime
                );
            } catch (NoSuchFileException e) {
                throw new FsException("ENOENT: no such file or directory, stat '" + path + "'");
            } catch (IOException e) {
                throw new FsException("ENOENT: no such file or directory, stat '" + path + "'");
            }
        });
    }

    @Override
    public CompletableFuture<FsStat> lstat(String path) {
        return CompletableFuture.supplyAsync(() -> {
            validatePath(path, "lstat");
            Path realPath = toRealPath(path);
            Path canonical = validateParent(realPath, path);

            try {
                java.nio.file.attribute.BasicFileAttributes attrs = Files.readAttributes(
                    canonical, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);

                long size = attrs.size();
                Instant mtime = Instant.ofEpochMilli(attrs.lastModifiedTime().toMillis());
                int mode = DEFAULT_FILE_MODE;

                return new FsStat(
                    attrs.isRegularFile(),
                    attrs.isDirectory(),
                    attrs.isSymbolicLink(),
                    mode,
                    size,
                    mtime
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
            validatePath(path, "mkdir");
            Path realPath = toRealPath(path);
            Path canonical = resolveAndValidate(realPath, path);

            try {
                if (options != null && options.recursive()) {
                    Files.createDirectories(canonical);
                } else {
                    Files.createDirectory(canonical);
                }
            } catch (FileAlreadyExistsException e) {
                if (options == null || !options.recursive()) {
                    throw new FsException("EEXIST: file already exists, mkdir '" + path + "'");
                }
                // recursive mkdir ignores existing directories
            } catch (NoSuchFileException e) {
                throw new FsException("ENOENT: no such file or directory, mkdir '" + path + "'");
            } catch (IOException e) {
                throw new FsException("ENOENT: no such file or directory, mkdir '" + path + "'");
            }
        });
    }

    @Override
    public CompletableFuture<List<String>> readdir(String path) {
        return readdirWithFileTypes(path).thenApply(entries ->
            entries.stream().map(DirentEntry::name).toList()
        );
    }

    @Override
    public CompletableFuture<List<DirentEntry>> readdirWithFileTypes(String path) {
        return CompletableFuture.supplyAsync(() -> {
            validatePath(path, "scandir");
            Path realPath = toRealPath(path);
            Path canonical = resolveAndValidate(realPath, path);

            try {
                // Defense-in-depth: check it's actually a directory
                if (!allowSymlinks) {
                    java.nio.file.attribute.BasicFileAttributes attrs = Files.readAttributes(
                        canonical, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    if (attrs.isSymbolicLink()) {
                        throw new FsException("EACCES: permission denied, '" + path + "' is a symlink");
                    }
                    if (!attrs.isDirectory()) {
                        throw new FsException("ENOTDIR: not a directory, scandir '" + path + "'");
                    }
                } else {
                    if (!Files.isDirectory(canonical, LinkOption.NOFOLLOW_LINKS)) {
                        if (Files.isRegularFile(canonical, LinkOption.NOFOLLOW_LINKS)) {
                            throw new FsException("ENOTDIR: not a directory, scandir '" + path + "'");
                        }
                        throw new FsException("ENOENT: no such file or directory, scandir '" + path + "'");
                    }
                }

                List<DirentEntry> result = new ArrayList<>();
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(canonical)) {
                    for (Path child : stream) {
                        String name = child.getFileName().toString();
                        java.nio.file.attribute.BasicFileAttributes attrs = Files.readAttributes(
                            child, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                        result.add(new DirentEntry(
                            name,
                            attrs.isRegularFile(),
                            attrs.isDirectory(),
                            attrs.isSymbolicLink()
                        ));
                    }
                }
                result.sort(Comparator.comparing(DirentEntry::name));
                return result;
            } catch (NotDirectoryException e) {
                throw new FsException("ENOTDIR: not a directory, scandir '" + path + "'");
            } catch (NoSuchFileException e) {
                throw new FsException("ENOENT: no such file or directory, scandir '" + path + "'");
            } catch (IOException e) {
                throw new FsException("ENOENT: no such file or directory, scandir '" + path + "'");
            }
        });
    }

    @Override
    public CompletableFuture<Void> rm(String path, RmOptions options) {
        return CompletableFuture.runAsync(() -> {
            validatePath(path, "rm");
            Path realPath = toRealPath(path);
            Path canonical = resolveAndValidate(realPath, path);

            boolean recursive = options != null && options.recursive();
            boolean force = options != null && options.force();

            try {
                if (recursive) {
                    deleteRecursively(canonical);
                } else {
                    Files.delete(canonical);
                }
            } catch (NoSuchFileException e) {
                if (!force) {
                    throw new FsException("ENOENT: no such file or directory, rm '" + path + "'");
                }
            } catch (DirectoryNotEmptyException e) {
                throw new FsException("ENOTEMPTY: directory not empty, rm '" + path + "'");
            } catch (IOException e) {
                throw new FsException("ENOENT: no such file or directory, rm '" + path + "'");
            }
        });
    }

    @Override
    public CompletableFuture<Void> cp(String src, String dest, CpOptions options) {
        return CompletableFuture.runAsync(() -> {
            validatePath(src, "cp");
            validatePath(dest, "cp");
            Path srcReal = toRealPath(src);
            Path destReal = toRealPath(dest);
            Path srcCanonical = resolveAndValidate(srcReal, src);
            Path destCanonical = resolveAndValidate(destReal, dest);

            boolean recursive = options != null && options.recursive();

            try {
                if (recursive) {
                    copyDirectory(srcCanonical, destCanonical, src);
                } else {
                    Files.copy(srcCanonical, destCanonical,
                        StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (NoSuchFileException e) {
                throw new FsException("ENOENT: no such file or directory, cp '" + src + "'");
            } catch (IOException e) {
                throw new FsException("ENOENT: no such file or directory, cp '" + src + "'");
            }
        });
    }

    @Override
    public CompletableFuture<Void> mv(String src, String dest) {
        return CompletableFuture.runAsync(() -> {
            validatePath(src, "mv");
            validatePath(dest, "mv");
            Path srcReal = toRealPath(src);
            Path destReal = toRealPath(dest);
            // Use validateParent (not resolveAndValidate) because rename operates on
            // directory entries — it does NOT follow the final symlink component.
            Path srcCanonical = validateParent(srcReal, src);
            Path destCanonical = validateParent(destReal, dest);

            // Ensure destination parent directory exists
            Path destParent = destCanonical.getParent();
            if (destParent != null) {
                try {
                    Files.createDirectories(destParent);
                } catch (IOException e) {
                    throw new FsException("ENOENT: no such file or directory, mv '" + dest + "'");
                }
            }

            try {
                Files.move(srcCanonical, destCanonical,
                    StandardCopyOption.REPLACE_EXISTING);
            } catch (NoSuchFileException e) {
                throw new FsException("ENOENT: no such file or directory, mv '" + src + "'");
            } catch (IOException e) {
                throw new FsException("ENOENT: no such file or directory, mv '" + src + "'");
            }
        });
    }

    @Override
    public String resolvePath(String base, String path) {
        if (path.startsWith("/")) return normalizePath(path);
        return normalizePath(Path.of(base).resolve(path).normalize().toString().replace('\\', '/'));
    }

    @Override
    public List<String> getAllPaths() {
        List<String> paths = new ArrayList<>();
        paths.add("/");
        scanDir("/", paths);
        return paths;
    }

    @Override
    public CompletableFuture<Void> chmod(String path, int mode) {
        return CompletableFuture.runAsync(() -> {
            validatePath(path, "chmod");
            Path realPath = toRealPath(path);
            Path canonical = resolveAndValidate(realPath, path);

            try {
                // Try POSIX first
                try {
                    Set<java.nio.file.attribute.PosixFilePermission> perms = modeToPosixPermissions(mode);
                    Files.setPosixFilePermissions(canonical, perms);
                    return;
                } catch (UnsupportedOperationException e) {
                    // Not a POSIX filesystem, ignore
                }

                // Fallback: try to set via attribute view
                // On Windows this would use ACLs, but for now we just accept the call
            } catch (NoSuchFileException e) {
                throw new FsException("ENOENT: no such file or directory, chmod '" + path + "'");
            } catch (IOException e) {
                throw new FsException("ENOENT: no such file or directory, chmod '" + path + "'");
            }
        });
    }

    @Override
    public CompletableFuture<Void> symlink(String target, String linkPath) {
        return CompletableFuture.runAsync(() -> {
            if (!allowSymlinks) {
                throw new FsException("EPERM: operation not permitted, symlink '" + linkPath + "'");
            }
            validatePath(linkPath, "symlink");
            Path realLinkPath = toRealPath(linkPath);
            Path canonicalLinkPath = validateParent(realLinkPath, linkPath);

            // Validate and transform symlink target
            String normalizedLinkPath = normalizePath(linkPath);
            String linkDir = dirname(normalizedLinkPath);
            String resolvedVirtualTarget = target.startsWith("/")
                ? normalizePath(target)
                : normalizePath(linkDir.equals("/") ? "/" + target : linkDir + "/" + target);

            // Convert to real path within root
            Path resolvedRealTarget = canonicalRoot.resolve(resolvedVirtualTarget.substring(1)).normalize();
            if (!resolvedRealTarget.startsWith(canonicalRoot)) {
                throw new FsException("EACCES: permission denied, symlink target escapes sandbox");
            }

            // Compute relative target from link directory
            Path linkDirPath = canonicalLinkPath.getParent();
            String safeTarget;
            if (target.startsWith("/")) {
                safeTarget = resolvedRealTarget.toString();
            } else {
                Path rel = linkDirPath.relativize(resolvedRealTarget);
                safeTarget = rel.toString();
                if (safeTarget.isEmpty()) safeTarget = ".";
            }

            try {
                Files.createSymbolicLink(canonicalLinkPath, Path.of(safeTarget));
            } catch (FileAlreadyExistsException e) {
                throw new FsException("EEXIST: file already exists, symlink '" + linkPath + "'");
            } catch (IOException e) {
                throw new FsException("EPERM: operation not permitted, symlink '" + linkPath + "'");
            }
        });
    }

    @Override
    public CompletableFuture<Void> link(String existingPath, String newPath) {
        return CompletableFuture.runAsync(() -> {
            validatePath(existingPath, "link");
            validatePath(newPath, "link");
            Path realExisting = toRealPath(existingPath);
            Path realNew = toRealPath(newPath);
            Path canonicalExisting = resolveAndValidate(realExisting, existingPath);
            Path canonicalNew = resolveAndValidate(realNew, newPath);

            try {
                Files.createLink(canonicalNew, canonicalExisting);
            } catch (NoSuchFileException e) {
                throw new FsException("ENOENT: no such file or directory, link '" + existingPath + "'");
            } catch (FileAlreadyExistsException e) {
                throw new FsException("EEXIST: file already exists, link '" + newPath + "'");
            } catch (IOException e) {
                throw new FsException("EPERM: operation not permitted, link '" + existingPath + "'");
            }
        });
    }

    @Override
    public CompletableFuture<String> readlink(String path) {
        return CompletableFuture.supplyAsync(() -> {
            validatePath(path, "readlink");
            Path realPath = toRealPath(path);
            Path canonical = validateParent(realPath, path);

            try {
                // Check if it's actually a symlink
                java.nio.file.attribute.BasicFileAttributes attrs = Files.readAttributes(
                    canonical, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!attrs.isSymbolicLink()) {
                    throw new FsException("EINVAL: invalid argument, readlink '" + path + "'");
                }

                Path rawTarget = Files.readSymbolicLink(canonical);

                // Convert raw OS target to virtual path
                String normalizedVirtual = normalizePath(path);
                String linkDir = dirname(normalizedVirtual);

                // Resolve raw target to absolute real path
                Path resolvedRealTarget = rawTarget.isAbsolute()
                    ? rawTarget
                    : canonical.getParent().resolve(rawTarget).normalize();

                // Check if target is within root
                Path canonicalTarget;
                try {
                    canonicalTarget = resolvedRealTarget.toRealPath();
                } catch (IOException e) {
                    canonicalTarget = resolvedRealTarget;
                }

                if (isPathWithinRoot(canonicalTarget, canonicalRoot)) {
                    String virtualTarget = canonicalTarget.toString().substring(canonicalRoot.toString().length());
                    if (virtualTarget.isEmpty()) virtualTarget = "/";
                    if (linkDir.equals("/")) {
                        return virtualTarget.startsWith("/")
                            ? (virtualTarget.length() > 1 ? virtualTarget.substring(1) : ".")
                            : virtualTarget;
                    }
                    // Compute relative path from link's virtual directory
                    Path linkDirReal = canonicalRoot.resolve(linkDir.substring(1)).normalize();
                    Path targetPath = canonicalRoot.resolve(virtualTarget.substring(1)).normalize();
                    Path rel = linkDirReal.relativize(targetPath);
                    String relStr = rel.toString();
                    return relStr.isEmpty() ? "." : relStr;
                }

                // Outside root - return basename only
                return rawTarget.getFileName().toString();
            } catch (NoSuchFileException e) {
                throw new FsException("ENOENT: no such file or directory, readlink '" + path + "'");
            } catch (IOException e) {
                if (e.getMessage() != null && e.getMessage().contains("Invalid argument")) {
                    throw new FsException("EINVAL: invalid argument, readlink '" + path + "'");
                }
                throw new FsException("ENOENT: no such file or directory, readlink '" + path + "'");
            }
        });
    }

    @Override
    public CompletableFuture<String> realpath(String path) {
        return CompletableFuture.supplyAsync(() -> {
            validatePath(path, "realpath");
            Path realPath = toRealPath(path);

            // Validate path respects symlink policy before resolving
            try {
                resolveAndValidate(realPath, path);
            } catch (Exception e) {
                throw new FsException("ENOENT: no such file or directory, realpath '" + path + "'");
            }

            try {
                Path resolved = realPath.toRealPath();
                if (isPathWithinRoot(resolved, canonicalRoot)) {
                    String relative = canonicalRoot.relativize(resolved).toString().replace('\\', '/');
                    return relative.isEmpty() ? "/" : "/" + relative;
                }
                throw new FsException("ENOENT: no such file or directory, realpath '" + path + "'");
            } catch (NoSuchFileException e) {
                throw new FsException("ENOENT: no such file or directory, realpath '" + path + "'");
            } catch (IOException e) {
                throw new FsException("ENOENT: no such file or directory, realpath '" + path + "'");
            }
        });
    }

    @Override
    public CompletableFuture<Void> utimes(String path, Instant atime, Instant mtime) {
        return CompletableFuture.runAsync(() -> {
            validatePath(path, "utimes");
            Path realPath = toRealPath(path);
            Path canonical = resolveAndValidate(realPath, path);

            try {
                Files.setLastModifiedTime(canonical, java.nio.file.attribute.FileTime.from(mtime));
            } catch (NoSuchFileException e) {
                throw new FsException("ENOENT: no such file or directory, utimes '" + path + "'");
            } catch (IOException e) {
                throw new FsException("ENOENT: no such file or directory, utimes '" + path + "'");
            }
        });
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /**
     * Validate that a resolved real path stays within the sandbox root and
     * return the canonical (symlink-resolved) path for use in subsequent I/O.
     */
    private Path resolveAndValidate(Path realPath, String virtualPath) {
        Path canonical;
        try {
            if (allowSymlinks) {
                canonical = realPath.toRealPath();
            } else {
                canonical = resolveNoSymlinks(realPath);
            }
        } catch (IOException e) {
            // Path doesn't exist - validate parent and use normalized path
            Path normalized = realPath.normalize().toAbsolutePath();
            if (!isPathWithinRoot(normalized, root)) {
                throw new FsException("EACCES: permission denied, '" + virtualPath + "' resolves outside sandbox");
            }
            return normalized;
        }

        if (canonical == null) {
            throw new FsException("EACCES: permission denied, '" + virtualPath + "' resolves outside sandbox");
        }

        if (!isPathWithinRoot(canonical, canonicalRoot)) {
            throw new FsException("EACCES: permission denied, '" + virtualPath + "' resolves outside sandbox");
        }
        return canonical;
    }

    /**
     * Validate the parent directory of a path (for operations that should not
     * follow the final component's symlink).
     */
    private Path validateParent(Path realPath, String virtualPath) {
        Path parent = realPath.getParent();
        if (parent == null) {
            return resolveAndValidate(realPath, virtualPath);
        }
        Path canonicalParent = resolveAndValidate(parent, virtualPath);
        return canonicalParent.resolve(realPath.getFileName());
    }

    /**
     * Convert a virtual path to a real filesystem path.
     */
    private Path toRealPath(String virtualPath) {
        String normalized = normalizePath(virtualPath);
        if (normalized.equals("/")) {
            return root;
        }
        return root.resolve(normalized.substring(1));
    }

    /**
     * Resolve a path without following symlinks. Validates that no component
     * in the path is a symlink.
     */
    private Path resolveNoSymlinks(Path realPath) throws IOException {
        Path normalized = realPath.normalize().toAbsolutePath();
        if (!normalized.startsWith(root)) {
            return null;
        }

        // Check each component for symlinks
        Path current = root;
        int nameCount = root.getNameCount();
        int targetCount = normalized.getNameCount();
        for (int i = nameCount; i < targetCount; i++) {
            current = current.resolve(normalized.getName(i));
            if (Files.isSymbolicLink(current)) {
                return null; // Symlink detected - reject
            }
        }
        return normalized;
    }

    private boolean isPathWithinRoot(Path path, Path root) {
        Path normalizedPath = path.normalize().toAbsolutePath();
        Path normalizedRoot = root.normalize().toAbsolutePath();
        return normalizedPath.startsWith(normalizedRoot);
    }

    private void validatePath(String path, String operation) {
        if (path.contains("\0")) {
            throw new FsException("EINVAL: invalid argument, " + operation + " '" + path + "' (contains null byte)");
        }
    }

    private void scanDir(String virtualDir, List<String> paths) {
        Path realPath = toRealPath(virtualDir);

        Path canonical;
        try {
            canonical = resolveAndValidate(realPath, virtualDir);
        } catch (Exception e) {
            return;
        }

        if (!Files.isDirectory(canonical, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(canonical)) {
            for (Path child : stream) {
                String name = child.getFileName().toString();
                String virtualPath = virtualDir.equals("/") ? "/" + name : virtualDir + "/" + name;
                paths.add(virtualPath);

                java.nio.file.attribute.BasicFileAttributes attrs = Files.readAttributes(
                    child, java.nio.file.attribute.BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (attrs.isDirectory()) {
                    scanDir(virtualPath, paths);
                }
            }
        } catch (IOException e) {
            // Ignore errors
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path child : stream) {
                    deleteRecursively(child);
                }
            }
        }
        Files.delete(path);
    }

    private void copyDirectory(Path src, Path dest, String srcVirtualPath) throws IOException {
        if (!Files.isDirectory(src, LinkOption.NOFOLLOW_LINKS)) {
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
            return;
        }

        if (!Files.exists(dest, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectories(dest);
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(src)) {
            for (Path child : stream) {
                String name = child.getFileName().toString();
                Path destChild = dest.resolve(name);

                // Skip symlinks that escape sandbox during recursive copy
                if (Files.isSymbolicLink(child)) {
                    Path linkTarget = Files.readSymbolicLink(child);
                    Path resolvedTarget = linkTarget.isAbsolute()
                        ? linkTarget
                        : child.getParent().resolve(linkTarget).normalize();
                    try {
                        Path canonicalTarget = resolvedTarget.toRealPath();
                        if (!isPathWithinRoot(canonicalTarget, canonicalRoot)) {
                            continue; // Skip escaping symlinks
                        }
                    } catch (IOException e) {
                        // Broken symlink - skip it to be safe
                        continue;
                    }
                }

                copyDirectory(child, destChild, srcVirtualPath + "/" + name);
            }
        }
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

    private static Set<java.nio.file.attribute.PosixFilePermission> modeToPosixPermissions(int mode) {
        Set<java.nio.file.attribute.PosixFilePermission> perms = new HashSet<>();
        if ((mode & 0400) != 0) perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_READ);
        if ((mode & 0200) != 0) perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
        if ((mode & 0100) != 0) perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE);
        if ((mode & 0040) != 0) perms.add(java.nio.file.attribute.PosixFilePermission.GROUP_READ);
        if ((mode & 0020) != 0) perms.add(java.nio.file.attribute.PosixFilePermission.GROUP_WRITE);
        if ((mode & 0010) != 0) perms.add(java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE);
        if ((mode & 0004) != 0) perms.add(java.nio.file.attribute.PosixFilePermission.OTHERS_READ);
        if ((mode & 0002) != 0) perms.add(java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE);
        if ((mode & 0001) != 0) perms.add(java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE);
        return perms;
    }
}
