/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_lucene_lucene_core;

import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DocumentStoredFieldVisitor;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.StoredField;
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
import static org.junit.jupiter.api.Assertions.assertNull;

/// Exercises Lucene's stored-field visitor subsystem to load only the catalog fields an application needs.
public class StoredFieldProjectionCatalogTest {
    @Test
    @Timeout(60)
    public void loadsOnlyRequestedCatalogFieldsForSearchResultProjection() throws IOException {
        try (Directory directory = new RAMDirectory();
                WhitespaceAnalyzer analyzer = new WhitespaceAnalyzer();
                IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
            writer.addDocument(product("SKU-100", "Cordless Drill", 129L,
                    "warehouse aisle north"));
            writer.addDocument(product("SKU-200", "Hose Reel", 79L, "warehouse aisle south"));
            writer.commit();

            try (DirectoryReader reader = DirectoryReader.open(directory)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                TopDocs results = searcher.search(new TermQuery(new Term("sku", "SKU-100")), 10);
                assertEquals(1, results.totalHits);

                DocumentStoredFieldVisitor projection = new DocumentStoredFieldVisitor("sku", "price");
                reader.document(results.scoreDocs[0].doc, projection);
                Document projected = projection.getDocument();

                assertEquals("SKU-100", projected.get("sku"));
                assertEquals(129L, projected.getField("price").numericValue().longValue());
                assertNull(projected.get("name"));
                assertNull(projected.get("internalNotes"));
            }
        }
    }

    private static Document product(String sku, String name, long price, String internalNotes) {
        Document document = new Document();
        document.add(new StringField("sku", sku, Field.Store.YES));
        document.add(new TextField("name", name, Field.Store.YES));
        document.add(new StoredField("price", price));
        document.add(new StoredField("internalNotes", internalNotes));
        return document;
    }
}
