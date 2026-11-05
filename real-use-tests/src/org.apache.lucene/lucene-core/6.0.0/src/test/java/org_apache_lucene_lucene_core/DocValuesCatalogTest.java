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
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Lucene's doc-values subsystem to rank an application catalog by a stored numeric value.
public class DocValuesCatalogTest {
    @Test
    @Timeout(60)
    public void sortsCatalogResultsByNumericDocValues() throws IOException {
        try (Directory directory = new RAMDirectory();
                WhitespaceAnalyzer analyzer = new WhitespaceAnalyzer();
                IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
            writer.addDocument(product("SKU-100", 42));
            writer.addDocument(product("SKU-200", 97));
            writer.addDocument(product("SKU-300", 68));
            writer.commit();

            try (DirectoryReader reader = DirectoryReader.open(directory)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                Sort popularityDescending = new Sort(
                        new SortField("popularity", SortField.Type.LONG, true));
                TopFieldDocs results = searcher.search(
                        new MatchAllDocsQuery(), 10, popularityDescending);

                assertEquals(3, results.totalHits);
                assertEquals("SKU-200", searcher.doc(results.scoreDocs[0].doc).get("sku"));
                assertEquals("SKU-300", searcher.doc(results.scoreDocs[1].doc).get("sku"));
                assertEquals("SKU-100", searcher.doc(results.scoreDocs[2].doc).get("sku"));
            }
        }
    }

    private static Document product(String sku, long popularity) {
        Document document = new Document();
        document.add(new StringField("sku", sku, Field.Store.YES));
        document.add(new NumericDocValuesField("popularity", popularity));
        return document;
    }
}
