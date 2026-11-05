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
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Lucene's similarity subsystem to rank catalog search results by term relevance.
public class SimilarityCatalogTest {
    @Test
    @Timeout(60)
    public void ranksCatalogResultsWithConfiguredTermFrequencySimilarity() throws IOException {
        ClassicSimilarity similarity = new ClassicSimilarity();
        try (Directory directory = new RAMDirectory();
                WhitespaceAnalyzer analyzer = new WhitespaceAnalyzer();
                IndexWriter writer = new IndexWriter(directory,
                        new IndexWriterConfig(analyzer).setSimilarity(similarity))) {
            writer.addDocument(product("SKU-100", "cordless drill hammer"));
            writer.addDocument(product("SKU-200", "drill drill hammer"));
            writer.addDocument(product("SKU-300", "garden hose reel"));
            writer.commit();

            try (DirectoryReader reader = DirectoryReader.open(directory)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                searcher.setSimilarity(similarity);
                TopDocs results = searcher.search(new TermQuery(new Term("description", "drill")), 10);

                assertEquals(2, results.totalHits);
                assertEquals("SKU-200", searcher.doc(results.scoreDocs[0].doc).get("sku"));
                assertEquals("SKU-100", searcher.doc(results.scoreDocs[1].doc).get("sku"));
                assertTrue(results.scoreDocs[0].score > results.scoreDocs[1].score);
            }
        }
    }

    private static Document product(String sku, String description) {
        Document document = new Document();
        document.add(new StringField("sku", sku, Field.Store.YES));
        document.add(new TextField("description", description, Field.Store.YES));
        return document;
    }
}
