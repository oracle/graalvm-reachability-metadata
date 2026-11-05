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
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Exercises Lucene's near-real-time reader refresh subsystem for an application catalog.
public class NearRealTimeCatalogTest {
    @Test
    @Timeout(60)
    public void refreshesSearchViewWithoutCommittingEveryCatalogChange() throws IOException {
        try (Directory directory = new RAMDirectory();
                WhitespaceAnalyzer analyzer = new WhitespaceAnalyzer();
                IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
            writer.addDocument(product("SKU-100", "Cordless Drill"));

            DirectoryReader currentReader = DirectoryReader.open(writer);
            try {
                IndexSearcher initialSearcher = new IndexSearcher(currentReader);
                assertEquals(1,
                        initialSearcher.search(new TermQuery(new Term("sku", "SKU-100")), 10).totalHits);

                writer.addDocument(product("SKU-200", "Hose Reel"));
                DirectoryReader refreshedReader = DirectoryReader.openIfChanged(currentReader, writer);
                assertNotNull(refreshedReader);
                currentReader.close();
                currentReader = refreshedReader;

                IndexSearcher refreshedSearcher = new IndexSearcher(currentReader);
                TopDocs results = refreshedSearcher.search(
                        new TermQuery(new Term("sku", "SKU-200")), 10);
                assertEquals(1, results.totalHits);
                Document product = refreshedSearcher.doc(results.scoreDocs[0].doc);
                assertEquals("SKU-200", product.get("sku"));
                assertEquals("Hose Reel", product.get("name"));
            } finally {
                currentReader.close();
            }
        }
    }

    private static Document product(String sku, String name) {
        Document document = new Document();
        document.add(new StringField("sku", sku, Field.Store.YES));
        document.add(new TextField("name", name, Field.Store.YES));
        return document;
    }
}
