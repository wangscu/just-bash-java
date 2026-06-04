package com.justbash.fs;

import com.justbash.encoding.ByteString;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class InMemoryFs implements IFileSystem {
    private final Map<String, FsEntry> data = new HashMap<>();

    public InMemoryFs() {
        this(null);
    }

    public InMemoryFs(Map<String, InitialFile> initialFiles) {
        data.put("/", new DirectoryEntry(0755, Instant.now()));
        if (initialFiles != null) {
            for (Map.Entry<String, InitialFile> e : initialFiles.entrySet()) {
                writeFileSync(e.getKey(), e.getValue().content(),
                    WriteFileOptions.utf8());
            }
        }
    }

    public record InitialFile(FileContent content, Optional<Integer> mode) {}

    // --- IFileSystem implementation ---

    @Override
    public CompletableFuture<String> readFile(String path, ReadFileOptions options) {
        return CompletableFuture.supplyAsync(() -> readFileSync(path, options));
    }

    @Override
    public CompletableFuture<ByteString> readFileBytes(String path) {
        return CompletableFuture.supplyAsync(() -> {
            String content = readFileSync(path, ReadFileOptions.binary());
            return ByteString.fromLatin1(content);
        });
    }

    @Override
    public CompletableFuture<byte[]> readFileBuffer(String path) {
        return CompletableFuture.supplyAsync(() -> {
            String content = readFileSync(path, ReadFileOptions.binary());
            byte[] bytes = new byte[content.length()];
            for (int i = 0; i < content.length(); i++) {
                bytes[i] = (byte) content.charAt(i);
            }
            return bytes;
        });
    }

    @Override
    public CompletableFuture<Void> writeFile(String path, FileContent content,
                                              WriteFileOptions options) {
        return CompletableFuture.runAsync(() -> writeFileSync(path, content, options));
    }

    @Override
    public CompletableFuture<Void> appendFile(String path, FileContent content,
                                               WriteFileOptions options) {
        return CompletableFuture.runAsync(() -> {
            String existing = readFileSync(path, ReadFileOptions.utf8());
            String newContent = existing + switch (content) {
                case IFileSystem.StringContent sc -> sc.value();
                case IFileSystem.ByteArrayContent bc -> byteArrayToString(bc.value());
            };
            writeFileSync(path, new IFileSystem.StringContent(newContent), options);
        });
    }

    @Override
    public CompletableFuture<Boolean> exists(String path) {
        return CompletableFuture.completedFuture(data.containsKey(normalizePath(path)));
    }

    @Override
    public CompletableFuture<FsStat> stat(String path) {
        return CompletableFuture.supplyAsync(() -> {
            FsEntry entry = getEntry(path);
            return new FsStat(
                entry instanceof FileEntry,
                entry instanceof DirectoryEntry,
                entry instanceof SymlinkEntry,
                entry.mode(),
                entry instanceof FileEntry f ? f.content().length() : 0,
                entry.mtime()
            );
        });
    }

    @Override
    public CompletableFuture<Void> mkdir(String path, MkdirOptions options) {
        return CompletableFuture.runAsync(() -> {
            String normalized = normalizePath(path);
            if (options.recursive()) {
                ensureParentDirs(normalized);
            }
            data.put(normalized, new DirectoryEntry(0755, Instant.now()));
        });
    }

    @Override
    public CompletableFuture<List<String>> readdir(String path) {
        return CompletableFuture.supplyAsync(() -> {
            String normalized = normalizePath(path);
            if (!normalized.endsWith("/")) normalized += "/";
            final String prefix = normalized;

            List<String> entries = new ArrayList<>();
            for (String key : data.keySet()) {
                if (key.startsWith(prefix) && !key.equals(prefix)) {
                    String relative = key.substring(prefix.length());
                    if (!relative.contains("/")) {
                        entries.add(relative);
                    }
                }
            }
            return entries;
        });
    }

    @Override
    public CompletableFuture<List<DirentEntry>> readdirWithFileTypes(String path) {
        return readdir(path).thenApply(names ->
            names.stream().map(name -> {
                FsEntry entry = data.get(normalizePath(path) + "/" + name);
                return new DirentEntry(
                    name,
                    entry instanceof FileEntry,
                    entry instanceof DirectoryEntry,
                    entry instanceof SymlinkEntry
                );
            }).toList()
        );
    }

    @Override
    public CompletableFuture<Void> rm(String path, RmOptions options) {
        return CompletableFuture.runAsync(() -> {
            String normalized = normalizePath(path);
            if (options.recursive() && data.get(normalized) instanceof DirectoryEntry) {
                data.entrySet().removeIf(e -> e.getKey().startsWith(normalized));
            }
            data.remove(normalized);
        });
    }

    @Override
    public CompletableFuture<Void> cp(String src, String dest, CpOptions options) {
        return CompletableFuture.runAsync(() -> {
            FsEntry entry = getEntry(src);
            if (entry instanceof FileEntry f) {
                writeFileSync(dest,
                    new IFileSystem.StringContent(f.content()),
                    WriteFileOptions.utf8());
            }
        });
    }

    @Override
    public CompletableFuture<Void> mv(String src, String dest) {
        return CompletableFuture.runAsync(() -> {
            String srcNorm = normalizePath(src);
            FsEntry entry = data.remove(srcNorm);
            if (entry != null) {
                data.put(normalizePath(dest), entry);
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
        return List.copyOf(data.keySet());
    }

    @Override
    public CompletableFuture<Void> chmod(String path, int mode) {
        return CompletableFuture.runAsync(() -> {
            String normalized = normalizePath(path);
            FsEntry old = data.get(normalized);
            if (old instanceof FileEntry f) {
                data.put(normalized, new FileEntry(f.content(), mode, f.mtime()));
            } else if (old instanceof DirectoryEntry d) {
                data.put(normalized, new DirectoryEntry(mode, d.mtime()));
            }
        });
    }

    @Override
    public CompletableFuture<Void> symlink(String target, String linkPath) {
        return CompletableFuture.runAsync(() -> {
            data.put(normalizePath(linkPath),
                new SymlinkEntry(target, 0777, Instant.now()));
        });
    }

    @Override
    public CompletableFuture<Void> link(String existingPath, String newPath) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<String> readlink(String path) {
        return CompletableFuture.supplyAsync(() -> {
            FsEntry entry = getEntry(path);
            if (entry instanceof SymlinkEntry s) return s.target();
            throw new FsException("Not a symlink: " + path);
        });
    }

    @Override
    public CompletableFuture<FsStat> lstat(String path) {
        return stat(path);
    }

    @Override
    public CompletableFuture<String> realpath(String path) {
        return CompletableFuture.completedFuture(normalizePath(path));
    }

    @Override
    public CompletableFuture<Void> utimes(String path, Instant atime, Instant mtime) {
        return CompletableFuture.runAsync(() -> {
            String normalized = normalizePath(path);
            FsEntry old = data.get(normalized);
            if (old instanceof FileEntry f) {
                data.put(normalized, new FileEntry(f.content(), f.mode(), mtime));
            }
        });
    }

    // --- Sync internal methods ---

    public String readFileSync(String path, ReadFileOptions options) {
        String normalized = normalizePath(path);
        FsEntry entry = data.get(normalized);
        if (entry == null) {
            throw new FsException("ENOENT: no such file or directory: " + path);
        }
        if (entry instanceof DirectoryEntry) {
            throw new FsException("EISDIR: illegal operation on a directory: " + path);
        }
        if (entry instanceof FileEntry f) {
            return f.content();
        }
        throw new FsException("Unknown entry type");
    }

    public void writeFileSync(String path, FileContent content,
                               WriteFileOptions options) {
        String normalized = normalizePath(path);
        ensureParentDirs(normalized);
        String textContent = switch (content) {
            case IFileSystem.StringContent sc -> sc.value();
            case IFileSystem.ByteArrayContent bc -> byteArrayToString(bc.value());
        };
        data.put(normalized, new FileEntry(textContent, 0644, Instant.now()));
    }

    // --- Private helpers ---

    private FsEntry getEntry(String path) {
        FsEntry entry = data.get(normalizePath(path));
        if (entry == null) {
            throw new FsException("ENOENT: no such file or directory: " + path);
        }
        return entry;
    }

    private void ensureParentDirs(String path) {
        String dir = dirname(path);
        if (dir.equals("/")) return;
        if (!data.containsKey(dir)) {
            ensureParentDirs(dir);
            data.put(dir, new DirectoryEntry(0755, Instant.now()));
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

    private static String byteArrayToString(byte[] bytes) {
        char[] chars = new char[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            chars[i] = (char) (bytes[i] & 0xFF);
        }
        return new String(chars);
    }

    // --- Entry types ---

    public sealed interface FsEntry {
        int mode();
        Instant mtime();
    }

    public record FileEntry(String content, int mode, Instant mtime)
        implements FsEntry {}

    public record DirectoryEntry(int mode, Instant mtime)
        implements FsEntry {}

    public record SymlinkEntry(String target, int mode, Instant mtime)
        implements FsEntry {}
}
