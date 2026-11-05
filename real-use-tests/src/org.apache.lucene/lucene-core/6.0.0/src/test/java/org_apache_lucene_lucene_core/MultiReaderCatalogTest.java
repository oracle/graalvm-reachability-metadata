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
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Lucene's composite-reader subsystem to search an application catalog split across tenant indexes.
public class MultiReaderCatalogTest {
    @Test
    @Timeout(60)
    public void searchesProductsAcrossTenantIndexes() throws IOException {
        try (Directory northDirectory = new RAMDirectory();
                Directory southDirectory = new RAMDirectory();
                WhitespaceAnalyzer analyzer = new WhitespaceAnalyzer();
                IndexWriter northWriter = new IndexWriter(
                        northDirectory, new IndexWriterConfig(analyzer));
                IndexWriter southWriter = new IndexWriter(
                        southDirectory, new IndexWriterConfig(analyzer))) {
            northWriter.addDocument(product("N-100", "Cordless drill"));
            northWriter.commit();
            southWriter.addDocument(product("S-200", "Cordless drill"));
            southWriter.commit();

            try (DirectoryReader northReader = DirectoryReader.open(northDirectory);
                    DirectoryReader southReader = DirectoryReader.open(southDirectory);
                    MultiReader catalogReader = new MultiReader(northReader, southReader)) {
                IndexSearcher searcher = new IndexSearcher(catalogReader);
                TopDocs results = searcher.search(
                        new TermQuery(new Term("description", "drill")), 10);

                assertEquals(2, results.totalHits);
                assertEquals("N-100", searcher.doc(results.scoreDocs[0].doc).get("sku"));
                assertEquals("S-200", searcher.doc(results.scoreDocs[1].doc).get("sku"));
            }
        }
    }

    private static Document product(String sku, String name) {
        Document document = new Document();
        document.add(new StringField("sku", sku, Field.Store.YES));
        document.add(new TextField("description", name, Field.Store.YES));
        return document;
    }
}
