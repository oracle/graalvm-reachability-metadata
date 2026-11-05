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
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Lucene's query parsing subsystem to turn an application's search text into catalog results.
public class QueryParserCatalogTest {
    @Test
    @Timeout(60)
    public void parsesCatalogSearchTextAndReturnsMatchingProducts() throws Exception {
        try (Directory directory = new RAMDirectory();
                WhitespaceAnalyzer analyzer = new WhitespaceAnalyzer();
                IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
            writer.addDocument(product("SKU-100", "tools", "Cordless Drill"));
            writer.addDocument(product("SKU-200", "garden", "Hose Reel"));
            writer.addDocument(product("SKU-300", "tools", "Bench Vise"));
            writer.commit();

            QueryParser parser = new QueryParser("description", analyzer);
            Query query = parser.parse("description:\"Cordless Drill\" AND category:tools");
            try (DirectoryReader reader = DirectoryReader.open(directory)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                TopDocs results = searcher.search(query, 10);

                assertEquals(1, results.totalHits);
                assertEquals("SKU-100", searcher.doc(results.scoreDocs[0].doc).get("sku"));
            }
        }
    }

    private static Document product(String sku, String category, String description) {
        Document document = new Document();
        document.add(new StringField("sku", sku, Field.Store.YES));
        document.add(new StringField("category", category, Field.Store.NO));
        document.add(new TextField("description", description, Field.Store.YES));
        return document;
    }
}
