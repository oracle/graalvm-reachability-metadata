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
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Lucene's highlighting subsystem to mark matching terms in an application's search results.
public class HighlightedCatalogTest {
    @Test
    @Timeout(60)
    public void highlightsMatchingCatalogTermsForResultPresentation() throws Exception {
        try (Directory directory = new RAMDirectory();
                WhitespaceAnalyzer analyzer = new WhitespaceAnalyzer();
                IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
            writer.addDocument(product("SKU-100", "Cordless drill for workshop repairs"));
            writer.addDocument(product("SKU-200", "Garden hose reel"));
            writer.commit();

            try (DirectoryReader reader = DirectoryReader.open(directory)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                Query query = new QueryParser("description", analyzer).parse("drill");
                TopDocs results = searcher.search(query, 10);
                String description = searcher.doc(results.scoreDocs[0].doc).get("description");

                Highlighter highlighter = new Highlighter(new SimpleHTMLFormatter("<mark>", "</mark>"),
                        new QueryScorer(query));
                String highlighted = highlighter.getBestFragment(analyzer, "description", description);

                assertEquals(1, results.totalHits);
                assertEquals("Cordless <mark>drill</mark> for workshop repairs", highlighted);
            }
        }
    }

    private static Document product(String sku, String description) throws IOException {
        Document document = new Document();
        document.add(new TextField("sku", sku, Field.Store.YES));
        document.add(new TextField("description", description, Field.Store.YES));
        return document;
    }
}
