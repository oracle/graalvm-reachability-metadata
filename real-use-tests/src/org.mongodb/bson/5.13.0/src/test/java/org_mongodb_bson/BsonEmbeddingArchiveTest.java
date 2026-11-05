/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_bson;

import org.bson.BinaryVector;
import org.bson.BsonBinary;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.BsonBinaryReader;
import org.bson.BsonBinaryWriter;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.io.BasicOutputBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises BSON binary vector values for storing and reading product embeddings. */
class BsonEmbeddingArchiveTest {

    @Test
    @Timeout(60)
    void storesAndReadsAnEmbeddingWithoutLosingItsVectorType() {
        BinaryVector embedding = BinaryVector.floatVector(new float[] {0.25f, -1.5f, 3.75f});
        BsonDocument product = new BsonDocument()
                .append("sku", new BsonString("tea-21"))
                .append("embedding", new BsonBinary(embedding));

        byte[] stored = encode(product);
        BsonDocument restored = decode(stored);
        BsonValue restoredEmbedding = restored.get("embedding");

        assertThat(stored).isNotEmpty();
        assertThat(restored.getString("sku").getValue()).isEqualTo("tea-21");
        assertThat(restoredEmbedding).isInstanceOf(BsonBinary.class);
        assertThat(((BsonBinary) restoredEmbedding).asVector().getDataType())
                .isEqualTo(BinaryVector.DataType.FLOAT32);
        assertThat(((BsonBinary) restoredEmbedding).asVector().asFloat32Vector().getData())
                .containsExactly(0.25f, -1.5f, 3.75f);
    }

    private static byte[] encode(BsonDocument document) {
        BsonDocumentCodec codec = new BsonDocumentCodec();
        try (BasicOutputBuffer output = new BasicOutputBuffer()) {
            BsonBinaryWriter writer = new BsonBinaryWriter(output);
            codec.encode(writer, document, EncoderContext.builder().build());
            writer.close();
            return output.toByteArray();
        }
    }

    private static BsonDocument decode(byte[] stored) {
        BsonDocumentCodec codec = new BsonDocumentCodec();
        try (BsonBinaryReader reader = new BsonBinaryReader(ByteBuffer.wrap(stored))) {
            return codec.decode(reader, DecoderContext.builder().build());
        }
    }
}
