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
import org.apache.lucene.index.KeepOnlyLastCommitDeletionPolicy;
import org.apache.lucene.index.SnapshotDeletionPolicy;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Lucene's snapshot deletion-policy subsystem to keep an application index version available for export.
public class SnapshotCatalogTest {
    @Test
    @Timeout(60)
    public void keepsCatalogSnapshotAvailableWhileTheIndexAdvances() throws IOException {
        SnapshotDeletionPolicy snapshots = new SnapshotDeletionPolicy(
                new KeepOnlyLastCommitDeletionPolicy());
        try (Directory directory = new RAMDirectory();
                WhitespaceAnalyzer analyzer = new WhitespaceAnalyzer();
                IndexWriter writer = new IndexWriter(directory,
                        new IndexWriterConfig(analyzer).setIndexDeletionPolicy(snapshots))) {
            writer.addDocument(product("SKU-100", "Cordless Drill"));
            writer.commit();
            IndexCommit exportCommit = snapshots.snapshot();

            writer.addDocument(product("SKU-200", "Hose Reel"));
            writer.commit();

            assertEquals(1, snapshots.getSnapshotCount());
            assertTrue(exportCommit.getGeneration() < DirectoryReader.listCommits(directory).get(
                    DirectoryReader.listCommits(directory).size() - 1).getGeneration());
            try (DirectoryReader exportReader = DirectoryReader.open(exportCommit)) {
                assertEquals(1, exportReader.numDocs());
                assertEquals("SKU-100", exportReader.document(0).get("sku"));
            }

            snapshots.release(exportCommit);
            assertEquals(0, snapshots.getSnapshotCount());
            try (DirectoryReader currentReader = DirectoryReader.open(directory)) {
                assertEquals(2, currentReader.numDocs());
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
