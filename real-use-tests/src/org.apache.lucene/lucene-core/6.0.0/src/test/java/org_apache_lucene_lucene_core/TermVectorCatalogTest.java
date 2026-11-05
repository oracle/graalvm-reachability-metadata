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
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.BytesRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Exercises Lucene's term-vector subsystem to build related-term information for a catalog record.
public class TermVectorCatalogTest {
    @Test
    @Timeout(60)
    public void readsStoredTermsAndPositionsForCatalogDescription() throws IOException {
        FieldType descriptionType = new FieldType();
        descriptionType.setStored(true);
        descriptionType.setTokenized(true);
        descriptionType.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
        descriptionType.setStoreTermVectors(true);
        descriptionType.setStoreTermVectorPositions(true);
        descriptionType.setStoreTermVectorOffsets(true);
        descriptionType.freeze();

        try (Directory directory = new RAMDirectory();
                WhitespaceAnalyzer analyzer = new WhitespaceAnalyzer();
                IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(analyzer))) {
            Document document = new Document();
            document.add(new Field("description", "red hammer red", descriptionType));
            writer.addDocument(document);
            writer.commit();

            try (DirectoryReader reader = DirectoryReader.open(directory)) {
                Terms terms = reader.getTermVectors(0).terms("description");
                assertNotNull(terms);

                TermsEnum termsEnum = terms.iterator();
                assertEquals(true, termsEnum.seekExact(new BytesRef("red")));
                assertEquals(2L, termsEnum.totalTermFreq());

                PostingsEnum postings = termsEnum.postings(null, PostingsEnum.ALL);
                assertEquals(0, postings.nextDoc());
                assertEquals(2, postings.freq());
                assertEquals(0, postings.nextPosition());
                assertEquals(0, postings.startOffset());
                assertEquals(3, postings.endOffset());
                assertEquals(2, postings.nextPosition());
                assertEquals(11, postings.startOffset());
                assertEquals(14, postings.endOffset());
            }
        }
    }
}
