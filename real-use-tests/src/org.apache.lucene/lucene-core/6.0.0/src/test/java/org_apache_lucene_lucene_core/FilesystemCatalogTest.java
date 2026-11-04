/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_lucene_lucene_core;

import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Lucene's filesystem storage subsystem to persist and reopen a catalog index.
public class FilesystemCatalogTest {
    @Test
    @Timeout(60)
    public void persistsCatalogAcrossDirectoryReopen() throws IOException {
        Path indexPath = Files.createTempDirectory("lucene-catalog-");
        try {
            try (Directory directory = new NIOFSDirectory(indexPath);
                    WhitespaceAnalyzer analyzer = new WhitespaceAnalyzer();
                    IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
                writer.addDocument(product("SKU-100", "Cordless Drill"));
                writer.addDocument(product("SKU-200", "Hose Reel"));
                writer.commit();
            }

            try (Directory directory = new NIOFSDirectory(indexPath)) {
                assertTrue(DirectoryReader.indexExists(directory));
                try (DirectoryReader reader = DirectoryReader.open(directory)) {
                    IndexSearcher searcher = new IndexSearcher(reader);
                    TopDocs results = searcher.search(new MatchAllDocsQuery(), 10);

                    assertEquals(2, results.totalHits);
                    assertEquals("SKU-100", searcher.doc(results.scoreDocs[0].doc).get("sku"));
                    assertEquals("Cordless Drill", searcher.doc(results.scoreDocs[0].doc).get("name"));
                    assertEquals("SKU-200", searcher.doc(results.scoreDocs[1].doc).get("sku"));
                }
            }
        } finally {
            deleteIndex(indexPath);
        }
    }

    private static Document product(String sku, String name) {
        Document document = new Document();
        document.add(new StringField("sku", sku, Field.Store.YES));
        document.add(new TextField("name", name, Field.Store.YES));
        return document;
    }

    private static void deleteIndex(Path indexPath) throws IOException {
        if (!Files.exists(indexPath)) {
            return;
        }
        try (Stream<Path> files = Files.list(indexPath)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                Files.deleteIfExists(file);
            }
        }
        Files.deleteIfExists(indexPath);
    }
}
