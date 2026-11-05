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
import org.apache.lucene.store.RAMDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Lucene's bulk index import subsystem to consolidate tenant catalog indexes.
public class BulkImportCatalogTest {
    @Test
    @Timeout(60)
    public void importsTenantIndexesIntoOneSearchableCatalog() throws IOException {
        try (Directory northDirectory = tenantIndex("N-100", "Cordless Drill");
                Directory southDirectory = tenantIndex("S-200", "Hose Reel");
                Directory catalogDirectory = new RAMDirectory();
                WhitespaceAnalyzer analyzer = new WhitespaceAnalyzer();
                IndexWriter writer = new IndexWriter(catalogDirectory,
                        new IndexWriterConfig(analyzer))) {
            writer.addIndexes(northDirectory, southDirectory);
            writer.commit();

            try (DirectoryReader reader = DirectoryReader.open(catalogDirectory)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                TopDocs results = searcher.search(new MatchAllDocsQuery(), 10);

                assertEquals(2, results.totalHits);
                assertEquals("N-100", searcher.doc(results.scoreDocs[0].doc).get("sku"));
                assertEquals("Cordless Drill", searcher.doc(results.scoreDocs[0].doc).get("name"));
                assertEquals("S-200", searcher.doc(results.scoreDocs[1].doc).get("sku"));
                assertEquals("Hose Reel", searcher.doc(results.scoreDocs[1].doc).get("name"));
            }
        }
    }

    private static Directory tenantIndex(String sku, String name) throws IOException {
        Directory directory = new RAMDirectory();
        WhitespaceAnalyzer analyzer = new WhitespaceAnalyzer();
        try (IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
            writer.addDocument(product(sku, name));
            writer.commit();
        }
        analyzer.close();
        return directory;
    }

    private static Document product(String sku, String name) {
        Document document = new Document();
        document.add(new StringField("sku", sku, Field.Store.YES));
        document.add(new TextField("name", name, Field.Store.YES));
        return document;
    }
}
