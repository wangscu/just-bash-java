package com.justbash.fs;

import com.justbash.encoding.ByteString;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface IFileSystem {
    CompletableFuture<String> readFile(String path, ReadFileOptions options);
    default CompletableFuture<String> readFile(String path) {
        return readFile(path, ReadFileOptions.utf8());
    }

    CompletableFuture<ByteString> readFileBytes(String path);
    CompletableFuture<byte[]> readFileBuffer(String path);

    CompletableFuture<Void> writeFile(String path, FileContent content,
                                       WriteFileOptions options);
    default CompletableFuture<Void> writeFile(String path, FileContent content) {
        return writeFile(path, content, WriteFileOptions.utf8());
    }

    CompletableFuture<Void> appendFile(String path, FileContent content,
                                        WriteFileOptions options);

    CompletableFuture<Boolean> exists(String path);
    CompletableFuture<FsStat> stat(String path);

    CompletableFuture<Void> mkdir(String path, MkdirOptions options);
    default CompletableFuture<Void> mkdir(String path) {
        return mkdir(path, MkdirOptions.defaults());
    }

    CompletableFuture<List<String>> readdir(String path);
    CompletableFuture<List<DirentEntry>> readdirWithFileTypes(String path);

    CompletableFuture<Void> rm(String path, RmOptions options);
    default CompletableFuture<Void> rm(String path) {
        return rm(path, RmOptions.defaults());
    }

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
