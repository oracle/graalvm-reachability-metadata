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
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
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

/// Exercises Lucene's indexing and searching subsystem to maintain a catalog and query available products.
public class ProductCatalogIndexTest {
    @Test
    @Timeout(60)
    public void indexesUpdatesAndSearchesCatalogProducts() throws IOException {
        try (Directory directory = new RAMDirectory();
                WhitespaceAnalyzer analyzer = new WhitespaceAnalyzer();
                IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
            writer.addDocument(product("SKU-100", "north", "tools", "Cordless Drill", 129L, 8));
            writer.addDocument(product("SKU-200", "north", "garden", "Hose Reel", 79L, 4));
            writer.addDocument(product("SKU-300", "south", "tools", "Bench Vise", 149L, 2));
            writer.updateDocument(new Term("sku", "SKU-100"),
                    product("SKU-100", "north", "tools", "Cordless Drill", 129L, 12));
            writer.deleteDocuments(new Term("sku", "SKU-200"));
            writer.commit();

            try (DirectoryReader reader = DirectoryReader.open(directory)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                BooleanQuery.Builder query = new BooleanQuery.Builder();
                query.add(new TermQuery(new Term("tenant", "north")), BooleanClause.Occur.MUST);
                query.add(new TermQuery(new Term("category", "tools")), BooleanClause.Occur.MUST);
                query.add(new TermQuery(new Term("description", "drill")), BooleanClause.Occur.MUST);
                query.add(LongPoint.newRangeQuery("price", 100L, 200L), BooleanClause.Occur.MUST);

                TopDocs results = searcher.search(query.build(), 10);
                assertEquals(1, results.totalHits);
                Document product = searcher.doc(results.scoreDocs[0].doc);
                assertEquals("SKU-100", product.get("sku"));
                assertEquals("Cordless Drill", product.get("name"));
                assertEquals(129L, product.getField("price").numericValue().longValue());
                assertEquals(12L, product.getField("stock").numericValue().longValue());
                assertNotNull(product.getField("description"));
                assertEquals(2, reader.numDocs());
            }
        }
    }

    private static Document product(String sku, String tenant, String category, String name, long price, long stock) {
        Document document = new Document();
        document.add(new StringField("sku", sku, Field.Store.YES));
        document.add(new StringField("tenant", tenant, Field.Store.NO));
        document.add(new StringField("category", category, Field.Store.NO));
        document.add(new TextField("description", name + " durable drill", Field.Store.YES));
        document.add(new LongPoint("price", price));
        document.add(new StoredField("price", price));
        document.add(new StoredField("stock", stock));
        document.add(new TextField("name", name, Field.Store.YES));
        return document;
    }
}
