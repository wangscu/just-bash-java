package com.justbash.postgres;

import org.junit.jupiter.api.*;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SearchTest {

    private DataSource dataSource;
    private PgFileSystem fs;

    @BeforeAll
    void beforeAll() {
        dataSource = TestDbHelper.startPostgres();
    }

    @BeforeEach
    void beforeEach() throws SQLException {
        TestDbHelper.resetDb(dataSource);
        fs = new PgFileSystem(new PgFileSystemOptions(
            dataSource, 1, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
        fs.setup();
    }

    @Test
    void shouldFindByContentKeyword() {
        fs.writeFile("/doc1.txt", new com.justbash.fs.IFileSystem.StringContent("apple banana cherry"), com.justbash.fs.WriteFileOptions.utf8()).join();
        fs.writeFile("/doc2.txt", new com.justbash.fs.IFileSystem.StringContent("apple pie recipe"), com.justbash.fs.WriteFileOptions.utf8()).join();
        fs.writeFile("/doc3.txt", new com.justbash.fs.IFileSystem.StringContent("nothing here"), com.justbash.fs.WriteFileOptions.utf8()).join();

        List<SearchResult> results = fs.search("apple", new SearchOptions()).join();
        assertThat(results).hasSize(2);
        assertThat(results.stream().map(SearchResult::name)).containsExactlyInAnyOrder("doc1.txt", "doc2.txt");
    }

    @Test
    void shouldRankFilenameHigherThanContent() {
        // Both files contain "apple" in content; filename match should rank higher
        fs.writeFile("/apple-recipe.txt", new com.justbash.fs.IFileSystem.StringContent("apple pie recipe with apple and cinnamon"), com.justbash.fs.WriteFileOptions.utf8()).join();
        fs.writeFile("/banana-guide.txt", new com.justbash.fs.IFileSystem.StringContent("apple fruit is delicious and tasty apple pie"), com.justbash.fs.WriteFileOptions.utf8()).join();

        List<SearchResult> results = fs.search("apple", new SearchOptions()).join();
        assertThat(results).hasSize(2);
        // Filename match should be first (weight A > weight C)
        assertThat(results.get(0).name()).isEqualTo("apple-recipe.txt");
    }

    @Test
    void shouldScopeToSubtree() {
        fs.mkdir("/docs", new com.justbash.fs.MkdirOptions(true)).join();
        fs.mkdir("/other", new com.justbash.fs.MkdirOptions(true)).join();
        fs.writeFile("/docs/readme.txt", new com.justbash.fs.IFileSystem.StringContent("search term here"), com.justbash.fs.WriteFileOptions.utf8()).join();
        fs.writeFile("/other/file.txt", new com.justbash.fs.IFileSystem.StringContent("search term here"), com.justbash.fs.WriteFileOptions.utf8()).join();

        List<SearchResult> results = fs.search("search term", new SearchOptions().withPath("/docs")).join();
        assertThat(results).hasSize(1);
        assertThat(results.get(0).path()).isEqualTo("/docs/readme.txt");
    }

    @Test
    void shouldReturnEmptyForNoMatches() {
        fs.writeFile("/file.txt", new com.justbash.fs.IFileSystem.StringContent("content"), com.justbash.fs.WriteFileOptions.utf8()).join();
        List<SearchResult> results = fs.search("nonexistentxyz", new SearchOptions()).join();
        assertThat(results).isEmpty();
    }

    @Test
    void shouldLimitResults() {
        for (int i = 0; i < 10; i++) {
            fs.writeFile("/file" + i + ".txt", new com.justbash.fs.IFileSystem.StringContent("common keyword"), com.justbash.fs.WriteFileOptions.utf8()).join();
        }
        List<SearchResult> results = fs.search("common keyword", new SearchOptions().withLimit(5)).join();
        assertThat(results).hasSize(5);
    }
}
