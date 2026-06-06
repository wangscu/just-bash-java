package com.justbash.fs;

/**
 * Options for ReadWriteFs.
 *
 * @param root            The root directory on the real filesystem. All paths are relative to this root.
 * @param maxFileReadSize Maximum file size in bytes that can be read. Defaults to 10MB (10485760).
 * @param allowSymlinks   Whether to allow following and creating symlinks. When false (default),
 *                        any path traversing a symlink is rejected and symlink() throws EPERM.
 */
public record ReadWriteFsOptions(String root, long maxFileReadSize, boolean allowSymlinks) {
    public ReadWriteFsOptions(String root) {
        this(root, 0, false);
    }
}
