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
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexCommit;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Lucene's commit metadata subsystem to version an application catalog index.
public class CommitMetadataCatalogTest {
    @Test
    @Timeout(60)
    public void reopensCatalogWithCommitMetadataForApplicationVersioning() throws IOException {
        try (Directory directory = new RAMDirectory();
                WhitespaceAnalyzer analyzer = new WhitespaceAnalyzer();
                IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
            writer.addDocument(product("SKU-100", "Cordless Drill"));

            Map<String, String> commitData = new LinkedHashMap<>();
            commitData.put("catalog-schema", "2024-01");
            commitData.put("import-batch", "batch-007");
            writer.setCommitData(commitData);
            writer.commit();

            try (DirectoryReader reader = DirectoryReader.open(directory)) {
                IndexCommit commit = reader.getIndexCommit();
                Map<String, String> reopenedCommitData = commit.getUserData();

                assertTrue(DirectoryReader.indexExists(directory));
                assertEquals("2024-01", reopenedCommitData.get("catalog-schema"));
                assertEquals("batch-007", reopenedCommitData.get("import-batch"));
                assertEquals(1, reader.numDocs());
            }
        }
    }

    private static Document product(String sku, String name) {
        Document document = new Document();
        document.add(new StringField("sku", sku, Field.Store.YES));
        document.add(new StringField("name", name, Field.Store.YES));
        return document;
    }
}
