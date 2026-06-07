package com.justbash.postgres;

import com.justbash.encoding.ByteString;
import com.justbash.fs.*;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class PgFileSystem implements IFileSystem {

    private static final long DEFAULT_MAX_FILE_SIZE = 100L * 1024 * 1024; // 100 MB
    private static final int MAX_SYMLINK_DEPTH = 16;
    private static final int MAX_PATH_DEPTH = 256;
    private static final int MAX_CP_NODES = 10000;
    private static final long DEFAULT_STATEMENT_TIMEOUT_MS = 30000;
    private static final int MAX_SEARCH_LIMIT = 100;

    private final DataSource dataSource;
    private final long sessionId;
    private final Optional<EmbeddingProvider> embed;
    private final Optional<Integer> embeddingDimensions;
    private final long maxFileSize;
    private final long statementTimeoutMs;

    public PgFileSystem(PgFileSystemOptions options) {
        this.dataSource = options.dataSource();
        this.sessionId = options.sessionId();
        this.embed = options.embed();
        this.embeddingDimensions = options.embeddingDimensions();
        this.maxFileSize = options.maxFileSize().orElse(DEFAULT_MAX_FILE_SIZE);
        this.statementTimeoutMs = options.statementTimeoutMs().orElse(DEFAULT_STATEMENT_TIMEOUT_MS);
    }

    public void setup() throws SQLException {
        PgSchema.setupSchema(dataSource);
        if (embeddingDimensions.isPresent()) {
            PgSchema.setupVectorColumn(dataSource, embeddingDimensions.get());
        }
        withSessionSync(conn -> {
            String rootLtree = PathEncoding.pathToLtree("/", sessionId);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO fs_nodes (session_id, name, node_type, path, mode) VALUES (?, '/', 'directory', ?::ltree, ?) ON CONFLICT (session_id, path) DO NOTHING")) {
                ps.setLong(1, sessionId);
                ps.setString(2, rootLtree);
                ps.setInt(3, 0755);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @FunctionalInterface
    private interface SqlFunction<T> {
        T apply(Connection conn) throws SQLException;
    }

    // ------------------------------------------------------------------
    // Transaction wrapper (sets RLS context)
    // ------------------------------------------------------------------

    private <T> CompletableFuture<T> withSession(SqlFunction<T> fn) {
        return CompletableFuture.supplyAsync(() -> withSessionSync(fn));
    }

    private <T> T withSessionSync(SqlFunction<T> fn) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps1 = conn.prepareStatement(
                    "SELECT set_config('app.session_id', ?, true)")) {
                ps1.setString(1, String.valueOf(sessionId));
                ps1.execute();
            }
            try (PreparedStatement ps2 = conn.prepareStatement(
                    "SELECT set_config('statement_timeout', ?, true)")) {
                ps2.setString(1, String.valueOf(statementTimeoutMs));
                ps2.execute();
            }
            T result = fn.apply(conn);
            conn.commit();
            return result;
        } catch (SQLException e) {
            throw new FsException("Database error: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Low-level helpers (operate within a transaction)
    // ------------------------------------------------------------------

    private record FsRow(
        long id, long session_id, Long parent_id, String name, String node_type,
        String path, String content, byte[] binary_data, String symlink_target,
        int mode, long size_bytes, Timestamp mtime, Timestamp created_at
    ) {}

    private record FsRowMeta(
        long id, long session_id, Long parent_id, String name, String node_type,
        String path, String symlink_target, int mode, long size_bytes,
        Timestamp mtime, Timestamp created_at
    ) {}

    private FsRow getNode(Connection conn, String posixPath) throws SQLException {
        String lt = PathEncoding.pathToLtree(posixPath, sessionId);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM fs_nodes WHERE session_id = ? AND path = ?::ltree LIMIT 1")) {
            ps.setLong(1, sessionId);
            ps.setString(2, lt);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapFsRow(rs) : null;
            }
        }
    }

    private FsRowMeta getNodeMeta(Connection conn, String posixPath) throws SQLException {
        String lt = PathEncoding.pathToLtree(posixPath, sessionId);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, session_id, parent_id, name, node_type, path, symlink_target, mode, size_bytes, mtime, created_at FROM fs_nodes WHERE session_id = ? AND path = ?::ltree LIMIT 1")) {
            ps.setLong(1, sessionId);
            ps.setString(2, lt);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapFsRowMeta(rs) : null;
            }
        }
    }

    private FsRow getNodeForUpdate(Connection conn, String posixPath) throws SQLException {
        String lt = PathEncoding.pathToLtree(posixPath, sessionId);
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM fs_nodes WHERE session_id = ? AND path = ?::ltree LIMIT 1 FOR UPDATE")) {
            ps.setLong(1, sessionId);
            ps.setString(2, lt);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapFsRow(rs) : null;
            }
        }
    }

    private FsRow resolveSymlink(Connection conn, String path, int maxDepth) throws SQLException {
        FsRow node = getNode(conn, path);
        if (node == null) throw new FsError("ENOENT", "no such file or directory", path);
        if ("symlink".equals(node.node_type) && node.symlink_target != null) {
            if (maxDepth <= 0) throw new FsError("ELOOP", "too many levels of symbolic links", path);
            return resolveSymlink(conn, PathEncoding.normalizePath(node.symlink_target), maxDepth - 1);
        }
        return node;
    }

    private FsRowMeta resolveSymlinkMeta(Connection conn, String path, int maxDepth) throws SQLException {
        FsRowMeta node = getNodeMeta(conn, path);
        if (node == null) throw new FsError("ENOENT", "no such file or directory", path);
        if ("symlink".equals(node.node_type) && node.symlink_target != null) {
            if (maxDepth <= 0) throw new FsError("ELOOP", "too many levels of symbolic links", path);
            return resolveSymlinkMeta(conn, PathEncoding.normalizePath(node.symlink_target), maxDepth - 1);
        }
        return node;
    }

    private static String parentPath(String posixPath) {
        String[] parts = posixPath.split("/");
        List<String> filtered = new ArrayList<>();
        for (String p : parts) {
            if (!p.isEmpty()) filtered.add(p);
        }
        if (filtered.size() <= 1) return "/";
        return "/" + String.join("/", filtered.subList(0, filtered.size() - 1));
    }

    private static String fileName(String posixPath) {
        String[] parts = posixPath.split("/");
        List<String> filtered = new ArrayList<>();
        for (String p : parts) {
            if (!p.isEmpty()) filtered.add(p);
        }
        return filtered.isEmpty() ? "/" : filtered.get(filtered.size() - 1);
    }

    private void validateFileSize(long size) {
        if (size > maxFileSize) {
            throw new FsException("EFBIG: file too large, " + size + " bytes exceeds maximum of " + maxFileSize + " bytes");
        }
    }

    private void validatePathDepth(String path) {
        int depth = 0;
        for (String part : path.split("/")) {
            if (!part.isEmpty()) depth++;
        }
        if (depth > MAX_PATH_DEPTH) {
            throw new FsException("Path too deep: " + depth + " levels exceeds maximum of " + MAX_PATH_DEPTH);
        }
    }

    // ------------------------------------------------------------------
    // Internal write (shared by writeFile, appendFile, link, cp)
    // ------------------------------------------------------------------

    private void internalWriteFile(Connection conn, String path, FileContent content,
                                    float[] precomputedEmbedding) throws SQLException {
        long size = contentSize(content);
        validateFileSize(size);
        validatePathDepth(path);

        String name = fileName(path);
        String parentPosix = parentPath(path);
        FsRowMeta parent = getNodeMeta(conn, parentPosix);
        if (parent == null) throw new FsError("ENOENT", "no such file or directory, open", path);

        FsRowMeta existing = getNodeMeta(conn, path);
        if (existing != null && "directory".equals(existing.node_type)) {
            throw new FsError("EISDIR", "illegal operation on a directory, open", path);
        }

        String lt = PathEncoding.pathToLtree(path, sessionId);
        boolean isText = content instanceof StringContent;
        String textContent = isText ? ((StringContent) content).value() : null;
        byte[] binaryData = isText ? null : ((ByteArrayContent) content).value();

        float[] embedding = null;
        if (precomputedEmbedding != null) {
            embedding = precomputedEmbedding;
        } else if (isText && embed.isPresent() && textContent != null && !textContent.isEmpty()) {
            try {
                float[] emb = embed.get().embed(textContent).join();
                validateEmbedding(emb);
                embedding = emb;
            } catch (Exception e) {
                // embedding failed, continue without it
            }
        }

        if (embedding != null) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO fs_nodes (session_id, parent_id, name, node_type, path, content, binary_data, size_bytes, mtime, embedding) VALUES (?, ?, ?, 'file', ?::ltree, ?, ?, ?, now(), ?::vector) ON CONFLICT (session_id, path) DO UPDATE SET content = EXCLUDED.content, binary_data = EXCLUDED.binary_data, size_bytes = EXCLUDED.size_bytes, mtime = now(), embedding = EXCLUDED.embedding")) {
                ps.setLong(1, sessionId);
                ps.setLong(2, parent.id);
                ps.setString(3, name);
                ps.setString(4, lt);
                ps.setString(5, textContent);
                ps.setBytes(6, binaryData);
                ps.setLong(7, size);
                ps.setString(8, vectorToString(embedding));
                ps.executeUpdate();
            }
        } else {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO fs_nodes (session_id, parent_id, name, node_type, path, content, binary_data, size_bytes, mtime) VALUES (?, ?, ?, 'file', ?::ltree, ?, ?, ?, now()) ON CONFLICT (session_id, path) DO UPDATE SET content = EXCLUDED.content, binary_data = EXCLUDED.binary_data, size_bytes = EXCLUDED.size_bytes, mtime = now()")) {
                ps.setLong(1, sessionId);
                ps.setLong(2, parent.id);
                ps.setString(3, name);
                ps.setString(4, lt);
                ps.setString(5, textContent);
                ps.setBytes(6, binaryData);
                ps.setLong(7, size);
                ps.executeUpdate();
            }
        }
    }

    // ------------------------------------------------------------------
    // Internal mkdir (shared by mkdir, cp)
    // ------------------------------------------------------------------

    private void internalMkdir(Connection conn, String path, MkdirOptions options) throws SQLException {
        validatePathDepth(path);
        boolean recursive = options != null && options.recursive();

        if (recursive) {
            String[] segments = path.split("/");
            List<String> allPaths = new ArrayList<>();
            List<String> allLtrees = new ArrayList<>();
            List<String> allNames = new ArrayList<>();
            String current = "/";
            for (String segment : segments) {
                if (segment.isEmpty()) continue;
                current = current.equals("/") ? "/" + segment : current + "/" + segment;
                allPaths.add(current);
                allLtrees.add(PathEncoding.pathToLtree(current, sessionId));
                allNames.add(segment);
            }

            if (allLtrees.isEmpty()) return;

            // Check existing
            StringBuilder placeholders = new StringBuilder();
            for (int i = 0; i < allLtrees.size(); i++) {
                if (i > 0) placeholders.append(",");
                placeholders.append("?::ltree");
            }
            Map<String, String> existingMap = new HashMap<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT path::text, node_type FROM fs_nodes WHERE session_id = ? AND path IN (" + placeholders + ")")) {
                ps.setLong(1, sessionId);
                for (int i = 0; i < allLtrees.size(); i++) {
                    ps.setString(2 + i, allLtrees.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        existingMap.put(rs.getString(1), rs.getString(2));
                    }
                }
            }

            for (int i = 0; i < allLtrees.size(); i++) {
                String nodeType = existingMap.get(allLtrees.get(i));
                if (nodeType != null && !"directory".equals(nodeType)) {
                    throw new FsError("ENOTDIR", "not a directory, mkdir", allPaths.get(i));
                }
            }

            for (int i = 0; i < allLtrees.size(); i++) {
                if (existingMap.containsKey(allLtrees.get(i))) continue;
                String parentLt = i == 0 ? PathEncoding.pathToLtree("/", sessionId) : allLtrees.get(i - 1);
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO fs_nodes (session_id, parent_id, name, node_type, path, mode) SELECT ?, p.id, ?, 'directory', ?::ltree, ? FROM fs_nodes p WHERE p.session_id = ? AND p.path = ?::ltree ON CONFLICT (session_id, path) DO NOTHING")) {
                    ps.setLong(1, sessionId);
                    ps.setString(2, allNames.get(i));
                    ps.setString(3, allLtrees.get(i));
                    ps.setInt(4, 0755);
                    ps.setLong(5, sessionId);
                    ps.setString(6, parentLt);
                    ps.executeUpdate();
                }
            }
        } else {
            FsRowMeta existing = getNodeMeta(conn, path);
            if (existing != null) throw new FsError("EEXIST", "file already exists, mkdir", path);
            String parentPosix = parentPath(path);
            FsRowMeta parent = getNodeMeta(conn, parentPosix);
            if (parent == null) throw new FsError("ENOENT", "no such file or directory, mkdir", path);
            String name = fileName(path);
            String lt = PathEncoding.pathToLtree(path, sessionId);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO fs_nodes (session_id, parent_id, name, node_type, path, mode) VALUES (?, ?, ?, 'directory', ?::ltree, ?)")) {
                ps.setLong(1, sessionId);
                ps.setLong(2, parent.id);
                ps.setString(3, name);
                ps.setString(4, lt);
                ps.setInt(5, 0755);
                ps.executeUpdate();
            }
        }
    }

    // ------------------------------------------------------------------
    // Internal readdir (shared by readdir, cp)
    // ------------------------------------------------------------------

    private List<String> internalReaddir(Connection conn, String path) throws SQLException {
        FsRowMeta node = getNodeMeta(conn, path);
        if (node == null) throw new FsError("ENOENT", "no such file or directory, scandir", path);
        if (!"directory".equals(node.node_type)) throw new FsError("ENOTDIR", "not a directory, scandir", path);

        List<String> names = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT name FROM fs_nodes WHERE session_id = ? AND parent_id = ? ORDER BY name")) {
            ps.setLong(1, sessionId);
            ps.setLong(2, node.id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) names.add(rs.getString(1));
            }
        }
        return names;
    }

    // ------------------------------------------------------------------
    // Internal cp (recursive, stays in same tx)
    // ------------------------------------------------------------------

    private void internalCp(Connection conn, String src, String dest, CpOptions options, Counter counter) throws SQLException {
        if (counter == null) counter = new Counter();

        if (dest.startsWith(src + "/") || dest.equals(src)) {
            throw new FsError("EINVAL", "cannot copy to a subdirectory of itself, cp", src);
        }

        FsRow srcNode = getNode(conn, src);
        if (srcNode == null) throw new FsError("ENOENT", "no such file or directory, cp", src);

        counter.count++;
        if (counter.count > MAX_CP_NODES) {
            throw new FsException("cp: too many nodes (exceeds limit of " + MAX_CP_NODES + ")");
        }

        if ("directory".equals(srcNode.node_type)) {
            if (options == null || !options.recursive()) {
                throw new FsError("EISDIR", "illegal operation on a directory, cp", src);
            }
            internalMkdir(conn, dest, new MkdirOptions(true));
            List<String> children = internalReaddir(conn, src);
            for (String child : children) {
                String srcChild = src.equals("/") ? "/" + child : src + "/" + child;
                String destChild = dest.equals("/") ? "/" + child : dest + "/" + child;
                internalCp(conn, srcChild, destChild, options, counter);
            }
            return;
        }

        // Skip re-embedding for copies
        FileContent content = srcNode.content != null
            ? new StringContent(srcNode.content)
            : new ByteArrayContent(internalReadFileBuffer(conn, src));
        internalWriteFile(conn, dest, content, null);
    }

    // ------------------------------------------------------------------
    // Internal readFileBuffer (shared by readFileBuffer, link, cp)
    // ------------------------------------------------------------------

    private byte[] internalReadFileBuffer(Connection conn, String path) throws SQLException {
        FsRow node = resolveSymlink(conn, path, MAX_SYMLINK_DEPTH);
        if ("directory".equals(node.node_type)) throw new FsError("EISDIR", "illegal operation on a directory, read", path);
        if (node.binary_data != null) return node.binary_data;
        if (node.content != null) return node.content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return new byte[0];
    }

    // ------------------------------------------------------------------
    // IFileSystem implementation
    // ------------------------------------------------------------------

    @Override
    public CompletableFuture<String> readFile(String path, ReadFileOptions options) {
        return withSession(conn -> {
            String p = PathEncoding.normalizePath(path);
            FsRow node = resolveSymlink(conn, p, MAX_SYMLINK_DEPTH);
            if ("directory".equals(node.node_type)) throw new FsError("EISDIR", "illegal operation on a directory, read", path);
            if (node.content != null) return node.content;
            if (node.binary_data != null) return new String(node.binary_data, java.nio.charset.StandardCharsets.UTF_8);
            return "";
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
        return withSession(conn -> internalReadFileBuffer(conn, PathEncoding.normalizePath(path)));
    }

    @Override
    public CompletableFuture<Void> writeFile(String path, FileContent content, WriteFileOptions options) {
        String normalized = PathEncoding.normalizePath(path);
        // Compute embedding outside transaction to avoid holding connection during API calls
        float[] embedding = null;
        if (content instanceof StringContent sc && embed.isPresent() && !sc.value().isEmpty()) {
            try {
                float[] emb = embed.get().embed(sc.value()).join();
                validateEmbedding(emb);
                embedding = emb;
            } catch (Exception e) {
                // embedding failed, continue without it
            }
        }
        final float[] finalEmbedding = embedding;
        return withSession(conn -> {
            internalWriteFile(conn, normalized, content, finalEmbedding);
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> appendFile(String path, FileContent content, WriteFileOptions options) {
        return withSession(conn -> {
            String p = PathEncoding.normalizePath(path);
            FsRow existing = getNodeForUpdate(conn, p);
            if (existing == null) {
                internalWriteFile(conn, p, content, null);
                return null;
            }

            long appendSize = contentSize(content);
            if (existing.size_bytes + appendSize > maxFileSize) {
                throw new FsException("EFBIG: file too large, " + (existing.size_bytes + appendSize) + " bytes exceeds maximum of " + maxFileSize + " bytes");
            }

            boolean isBinary = existing.binary_data != null || !(content instanceof StringContent);
            if (isBinary) {
                byte[] existingBytes = existing.binary_data != null ? existing.binary_data :
                    (existing.content != null ? existing.content.getBytes(java.nio.charset.StandardCharsets.UTF_8) : new byte[0]);
                byte[] appendBytes = content instanceof StringContent sc
                    ? sc.value().getBytes(java.nio.charset.StandardCharsets.UTF_8)
                    : ((ByteArrayContent) content).value();
                byte[] merged = new byte[existingBytes.length + appendBytes.length];
                System.arraycopy(existingBytes, 0, merged, 0, existingBytes.length);
                System.arraycopy(appendBytes, 0, merged, existingBytes.length, appendBytes.length);
                internalWriteFile(conn, p, new ByteArrayContent(merged), null);
            } else {
                String currentContent = existing.content != null ? existing.content : "";
                String newContent = currentContent + ((StringContent) content).value();
                internalWriteFile(conn, p, new StringContent(newContent), null);
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Boolean> exists(String path) {
        return withSession(conn -> getNodeMeta(conn, PathEncoding.normalizePath(path)) != null);
    }

    @Override
    public CompletableFuture<FsStat> stat(String path) {
        return withSession(conn -> {
            FsRowMeta node = resolveSymlinkMeta(conn, PathEncoding.normalizePath(path), MAX_SYMLINK_DEPTH);
            return toFsStat(node);
        });
    }

    @Override
    public CompletableFuture<FsStat> lstat(String path) {
        return withSession(conn -> {
            String p = PathEncoding.normalizePath(path);
            FsRowMeta node = getNodeMeta(conn, p);
            if (node == null) throw new FsError("ENOENT", "no such file or directory, lstat", path);
            return new FsStat(
                "file".equals(node.node_type),
                "directory".equals(node.node_type),
                "symlink".equals(node.node_type),
                node.mode,
                node.size_bytes,
                node.mtime.toInstant()
            );
        });
    }

    @Override
    public CompletableFuture<String> realpath(String path) {
        return withSession(conn -> internalRealpath(conn, PathEncoding.normalizePath(path), MAX_SYMLINK_DEPTH));
    }

    private String internalRealpath(Connection conn, String path, int maxDepth) throws SQLException {
        FsRowMeta node = getNodeMeta(conn, path);
        if (node == null) throw new FsError("ENOENT", "no such file or directory, realpath", path);
        if ("symlink".equals(node.node_type) && node.symlink_target != null) {
            if (maxDepth <= 0) throw new FsError("ELOOP", "too many levels of symbolic links, realpath", path);
            return internalRealpath(conn, PathEncoding.normalizePath(node.symlink_target), maxDepth - 1);
        }
        return PathEncoding.ltreeToPath(node.path);
    }

    @Override
    public CompletableFuture<Void> mkdir(String path, MkdirOptions options) {
        return withSession(conn -> {
            internalMkdir(conn, PathEncoding.normalizePath(path), options);
            return null;
        });
    }

    @Override
    public CompletableFuture<List<String>> readdir(String path) {
        return withSession(conn -> internalReaddir(conn, PathEncoding.normalizePath(path)));
    }

    @Override
    public CompletableFuture<List<DirentEntry>> readdirWithFileTypes(String path) {
        return withSession(conn -> {
            String p = PathEncoding.normalizePath(path);
            FsRowMeta node = getNodeMeta(conn, p);
            if (node == null) throw new FsError("ENOENT", "no such file or directory, scandir", path);
            if (!"directory".equals(node.node_type)) throw new FsError("ENOTDIR", "not a directory, scandir", path);

            List<DirentEntry> entries = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT name, node_type FROM fs_nodes WHERE session_id = ? AND parent_id = ? ORDER BY name")) {
                ps.setLong(1, sessionId);
                ps.setLong(2, node.id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String name = rs.getString(1);
                        String type = rs.getString(2);
                        entries.add(new DirentEntry(
                            name,
                            "file".equals(type),
                            "directory".equals(type),
                            "symlink".equals(type)
                        ));
                    }
                }
            }
            return entries;
        });
    }

    @Override
    public CompletableFuture<Void> rm(String path, RmOptions options) {
        return withSession(conn -> {
            String p = PathEncoding.normalizePath(path);
            FsRowMeta node = getNodeMeta(conn, p);
            if (node == null) {
                if (options != null && options.force()) return null;
                throw new FsError("ENOENT", "no such file or directory, rm", path);
            }

            if ("directory".equals(node.node_type)) {
                if (options == null || !options.recursive()) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT 1 FROM fs_nodes WHERE session_id = ? AND parent_id = ? LIMIT 1")) {
                        ps.setLong(1, sessionId);
                        ps.setLong(2, node.id);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                throw new FsError("ENOTEMPTY", "directory not empty, rm", path);
                            }
                        }
                    }
                }
            }

            if (options != null && options.recursive() && "directory".equals(node.node_type)) {
                String lt = PathEncoding.pathToLtree(p, sessionId);
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM fs_nodes WHERE session_id = ? AND path <@ ?::text::ltree")) {
                    ps.setLong(1, sessionId);
                    ps.setString(2, lt);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM fs_nodes WHERE session_id = ? AND id = ?")) {
                    ps.setLong(1, sessionId);
                    ps.setLong(2, node.id);
                    ps.executeUpdate();
                }
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> cp(String src, String dest, CpOptions options) {
        return withSession(conn -> {
            internalCp(conn, PathEncoding.normalizePath(src), PathEncoding.normalizePath(dest), options, null);
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> mv(String src, String dest) {
        return withSession(conn -> {
            String srcPath = PathEncoding.normalizePath(src);
            String destPath = PathEncoding.normalizePath(dest);

            if (destPath.startsWith(srcPath + "/") || destPath.equals(srcPath)) {
                throw new FsError("EINVAL", "cannot move to a subdirectory of itself, mv", src);
            }

            FsRow srcNode = getNodeForUpdate(conn, srcPath);
            if (srcNode == null) throw new FsError("ENOENT", "no such file or directory, mv", src);

            String destParentPosix = parentPath(destPath);
            FsRowMeta destParent = getNodeMeta(conn, destParentPosix);
            if (destParent == null) throw new FsError("ENOENT", "no such file or directory, mv", dest);

            // Handle existing destination
            FsRowMeta destNode = getNodeMeta(conn, destPath);
            if (destNode != null) {
                if ("directory".equals(destNode.node_type) && !"directory".equals(srcNode.node_type)) {
                    throw new FsError("EISDIR", "cannot overwrite directory with non-directory, mv", dest);
                }
                if (!"directory".equals(destNode.node_type) && "directory".equals(srcNode.node_type)) {
                    throw new FsError("ENOTDIR", "cannot overwrite non-directory with directory, mv", dest);
                }
                if ("directory".equals(destNode.node_type)) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT 1 FROM fs_nodes WHERE session_id = ? AND parent_id = ? LIMIT 1")) {
                        ps.setLong(1, sessionId);
                        ps.setLong(2, destNode.id);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                throw new FsError("ENOTEMPTY", "directory not empty, mv", dest);
                            }
                        }
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM fs_nodes WHERE session_id = ? AND id = ?")) {
                    ps.setLong(1, sessionId);
                    ps.setLong(2, destNode.id);
                    ps.executeUpdate();
                }
            }

            String newName = fileName(destPath);
            String newLtree = PathEncoding.pathToLtree(destPath, sessionId);
            String oldLtree = PathEncoding.pathToLtree(srcPath, sessionId);

            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE fs_nodes SET name = ?, path = ?::ltree, parent_id = ?, mtime = now() WHERE session_id = ? AND id = ?")) {
                ps.setString(1, newName);
                ps.setString(2, newLtree);
                ps.setLong(3, destParent.id);
                ps.setLong(4, sessionId);
                ps.setLong(5, srcNode.id);
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    throw new FsError("ENOENT", "no such file or directory, mv", src);
                }
            }

            if ("directory".equals(srcNode.node_type)) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE fs_nodes SET path = (?::ltree || subpath(path, nlevel(?::ltree))) WHERE session_id = ? AND path <@ ?::text::ltree")) {
                    ps.setString(1, newLtree);
                    ps.setString(2, oldLtree);
                    ps.setLong(3, sessionId);
                    ps.setString(4, oldLtree);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE fs_nodes AS child SET parent_id = parent.id FROM fs_nodes AS parent WHERE child.session_id = ? AND parent.session_id = ? AND child.path <@ ?::text::ltree AND child.path != ?::text::ltree AND parent.path = subltree(child.path, 0, nlevel(child.path) - 1)")) {
                    ps.setLong(1, sessionId);
                    ps.setLong(2, sessionId);
                    ps.setString(3, newLtree);
                    ps.setString(4, newLtree);
                    ps.executeUpdate();
                }
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> chmod(String path, int mode) {
        if (mode < 0 || mode > 07777) {
            throw new IllegalArgumentException("Invalid mode: " + mode + " (must be integer between 0 and 4095/0o7777)");
        }
        return withSession(conn -> {
            String p = PathEncoding.normalizePath(path);
            FsRowMeta node = getNodeMeta(conn, p);
            if (node == null) throw new FsError("ENOENT", "no such file or directory, chmod", path);
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE fs_nodes SET mode = ? WHERE session_id = ? AND id = ?")) {
                ps.setInt(1, mode);
                ps.setLong(2, sessionId);
                ps.setLong(3, node.id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> utimes(String path, Instant atime, Instant mtime) {
        return withSession(conn -> {
            String p = PathEncoding.normalizePath(path);
            FsRowMeta node = getNodeMeta(conn, p);
            if (node == null) throw new FsError("ENOENT", "no such file or directory, utimes", path);
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE fs_nodes SET mtime = ? WHERE session_id = ? AND id = ?")) {
                ps.setTimestamp(1, Timestamp.from(mtime));
                ps.setLong(2, sessionId);
                ps.setLong(3, node.id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> symlink(String target, String linkPath) {
        return withSession(conn -> {
            String normalizedTarget = PathEncoding.normalizePath(target);
            String p = PathEncoding.normalizePath(linkPath);

            if (normalizedTarget.length() > 4096) {
                throw new FsException("Symlink target exceeds maximum length of 4096 characters");
            }
            validatePathDepth(normalizedTarget);

            String parentPosix = parentPath(p);
            FsRowMeta parent = getNodeMeta(conn, parentPosix);
            if (parent == null) throw new FsError("ENOENT", "no such file or directory, symlink", linkPath);

            String name = fileName(p);
            String lt = PathEncoding.pathToLtree(p, sessionId);

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO fs_nodes (session_id, parent_id, name, node_type, path, symlink_target, mode) VALUES (?, ?, ?, 'symlink', ?::ltree, ?, ?)")) {
                ps.setLong(1, sessionId);
                ps.setLong(2, parent.id);
                ps.setString(3, name);
                ps.setString(4, lt);
                ps.setString(5, normalizedTarget);
                ps.setInt(6, 0777);
                ps.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> link(String existingPath, String newPath) {
        return withSession(conn -> {
            String src = PathEncoding.normalizePath(existingPath);
            FsRow srcNode = getNode(conn, src);
            if (srcNode == null) throw new FsError("ENOENT", "no such file or directory, link", existingPath);
            if ("directory".equals(srcNode.node_type)) throw new FsError("EPERM", "operation not permitted, link", existingPath);

            FileContent content = srcNode.content != null
                ? new StringContent(srcNode.content)
                : new ByteArrayContent(internalReadFileBuffer(conn, src));
            internalWriteFile(conn, PathEncoding.normalizePath(newPath), content, null);
            return null;
        });
    }

    @Override
    public CompletableFuture<String> readlink(String path) {
        return withSession(conn -> {
            String p = PathEncoding.normalizePath(path);
            FsRowMeta node = getNodeMeta(conn, p);
            if (node == null) throw new FsError("ENOENT", "no such file or directory, readlink", path);
            if (!"symlink".equals(node.node_type)) throw new FsError("EINVAL", "invalid argument, readlink", path);
            if (node.symlink_target == null) {
                throw new FsException("Corrupt symlink node at '" + path + "': symlink_target is null");
            }
            return node.symlink_target;
        });
    }

    @Override
    public String resolvePath(String base, String path) {
        if (path.startsWith("/")) return PathEncoding.normalizePath(path);
        if ("/".equals(base)) return PathEncoding.normalizePath("/" + path);
        return PathEncoding.normalizePath(base + "/" + path);
    }

    @Override
    public List<String> getAllPaths() {
        return List.of();
    }

    // ------------------------------------------------------------------
    // Search methods (beyond IFileSystem)
    // ------------------------------------------------------------------

    public CompletableFuture<List<SearchResult>> search(String query, SearchOptions opts) {
        return withSession(conn -> {
            int limit = clampLimit(opts != null ? opts.limit().orElse(null) : null);
            String scopeLtree = PathEncoding.pathToLtree(opts != null ? opts.path().orElse("/") : "/", sessionId);

            List<SearchResult> results = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT path::text as path, name, ts_rank(search_vector, websearch_to_tsquery('english', ?)) AS rank, ts_headline('english', left(coalesce(content, ''), 100000), websearch_to_tsquery('english', ?), 'MaxWords=35, MinWords=15, MaxFragments=1') AS snippet FROM fs_nodes WHERE session_id = ? AND path <@ ?::text::ltree AND node_type = 'file' AND search_vector @@ websearch_to_tsquery('english', ?) ORDER BY rank DESC LIMIT ?")) {
                ps.setString(1, query);
                ps.setString(2, query);
                ps.setLong(3, sessionId);
                ps.setString(4, scopeLtree);
                ps.setString(5, query);
                ps.setInt(6, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String snippet = rs.getString(4);
                        results.add(new SearchResult(
                            PathEncoding.ltreeToPath(rs.getString(1)),
                            rs.getString(2),
                            rs.getDouble(3),
                            snippet != null ? Optional.of(snippet) : Optional.empty()
                        ));
                    }
                }
            }
            return results;
        });
    }

    public CompletableFuture<List<SearchResult>> semanticSearch(String query, SearchOptions opts) {
        if (embed.isEmpty()) {
            throw new FsException("No embedding provider configured");
        }
        float[] embedding = embed.get().embed(query).join();
        validateEmbedding(embedding);
        return withSession(conn -> {
            int limit = clampLimit(opts != null ? opts.limit().orElse(null) : null);
            String scopeLtree = PathEncoding.pathToLtree(opts != null ? opts.path().orElse("/") : "/", sessionId);

            List<SearchResult> results = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT path::text as path, name, 1 - (embedding <=> ?::vector) AS rank FROM fs_nodes WHERE session_id = ? AND path <@ ?::text::ltree AND node_type = 'file' AND embedding IS NOT NULL ORDER BY embedding <=> ?::vector LIMIT ?")) {
                ps.setString(1, vectorToString(embedding));
                ps.setLong(2, sessionId);
                ps.setString(3, scopeLtree);
                ps.setString(4, vectorToString(embedding));
                ps.setInt(5, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(new SearchResult(
                            PathEncoding.ltreeToPath(rs.getString(1)),
                            rs.getString(2),
                            rs.getDouble(3)
                        ));
                    }
                }
            }
            return results;
        });
    }

    public CompletableFuture<List<SearchResult>> hybridSearch(String query, HybridSearchOptions opts) {
        if (embed.isEmpty()) {
            throw new FsException("No embedding provider configured");
        }
        float[] embedding = embed.get().embed(query).join();
        validateEmbedding(embedding);
        return withSession(conn -> {
            int limit = clampLimit(opts != null ? opts.limit().orElse(null) : null);
            double textWeight = opts != null ? opts.effectiveTextWeight() : 0.4;
            double vectorWeight = opts != null ? opts.effectiveVectorWeight() : 0.6;
            String scopeLtree = PathEncoding.pathToLtree(opts != null && opts.path().isPresent() ? opts.path().get() : "/", sessionId);

            List<SearchResult> results = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT path::text as path, name, (? * ts_rank(search_vector, websearch_to_tsquery('english', ?)) + ? * (1 - (embedding <=> ?::vector))) AS rank FROM fs_nodes WHERE session_id = ? AND path <@ ?::text::ltree AND node_type = 'file' AND search_vector @@ websearch_to_tsquery('english', ?) AND embedding IS NOT NULL ORDER BY rank DESC LIMIT ?")) {
                ps.setDouble(1, textWeight);
                ps.setString(2, query);
                ps.setDouble(3, vectorWeight);
                ps.setString(4, vectorToString(embedding));
                ps.setLong(5, sessionId);
                ps.setString(6, scopeLtree);
                ps.setString(7, query);
                ps.setInt(8, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(new SearchResult(
                            PathEncoding.ltreeToPath(rs.getString(1)),
                            rs.getString(2),
                            rs.getDouble(3)
                        ));
                    }
                }
            }
            return results;
        });
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    private static FsStat toFsStat(FsRowMeta node) {
        return new FsStat(
            "file".equals(node.node_type),
            "directory".equals(node.node_type),
            false,
            node.mode,
            node.size_bytes,
            node.mtime.toInstant()
        );
    }

    private static FsRow mapFsRow(ResultSet rs) throws SQLException {
        return new FsRow(
            rs.getLong("id"),
            rs.getLong("session_id"),
            rs.getObject("parent_id", Long.class),
            rs.getString("name"),
            rs.getString("node_type"),
            rs.getString("path"),
            rs.getString("content"),
            rs.getBytes("binary_data"),
            rs.getString("symlink_target"),
            rs.getInt("mode"),
            rs.getLong("size_bytes"),
            rs.getTimestamp("mtime"),
            rs.getTimestamp("created_at")
        );
    }

    private static FsRowMeta mapFsRowMeta(ResultSet rs) throws SQLException {
        return new FsRowMeta(
            rs.getLong("id"),
            rs.getLong("session_id"),
            rs.getObject("parent_id", Long.class),
            rs.getString("name"),
            rs.getString("node_type"),
            rs.getString("path"),
            rs.getString("symlink_target"),
            rs.getInt("mode"),
            rs.getLong("size_bytes"),
            rs.getTimestamp("mtime"),
            rs.getTimestamp("created_at")
        );
    }

    private static long contentSize(FileContent content) {
        return switch (content) {
            case StringContent sc -> sc.value().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            case ByteArrayContent bc -> bc.value().length;
        };
    }

    private void validateEmbedding(float[] embedding) {
        if (embeddingDimensions.isPresent() && embedding.length != embeddingDimensions.get()) {
            throw new FsException("Embedding dimension mismatch: expected " + embeddingDimensions.get() + ", got " + embedding.length);
        }
        for (int i = 0; i < embedding.length; i++) {
            if (!Float.isFinite(embedding[i])) {
                throw new FsException("Embedding contains non-finite value at index " + i + ": " + embedding[i]);
            }
        }
    }

    private static String vectorToString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    private static int clampLimit(Integer limit) {
        int val = limit != null ? limit : 20;
        if (val < 1) return 1;
        return Math.min(val, MAX_SEARCH_LIMIT);
    }

    private static class Counter {
        int count = 0;
    }
}
