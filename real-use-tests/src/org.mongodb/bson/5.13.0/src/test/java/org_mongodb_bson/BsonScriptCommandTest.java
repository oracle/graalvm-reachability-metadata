/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_bson;

import org.bson.BsonDocument;
import org.bson.BsonJavaScript;
import org.bson.BsonJavaScriptWithScope;
import org.bson.BsonString;
import org.bson.BsonValue;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.io.BasicOutputBuffer;
import org.bson.BsonBinaryReader;
import org.bson.BsonBinaryWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises BSON JavaScript values for an application's stored migration command. */
public class BsonScriptCommandTest {

    @Test
    @Timeout(60)
    void roundTripsMigrationCodeAndItsScopedArguments() {
        BsonDocument command = new BsonDocument()
                .append("name", new BsonJavaScript("return 'migration-ready';"))
                .append("migration", new BsonJavaScriptWithScope(
                        "function () { return tenant + ':' + region; }",
                        new BsonDocument()
                                .append("tenant", new BsonString("warehouse"))
                                .append("region", new BsonString("west"))));
        BsonDocumentCodec codec = new BsonDocumentCodec();

        BsonDocument restored = decode(codec, encode(codec, command));

        BsonValue restoredName = restored.get("name");
        BsonValue restoredMigration = restored.get("migration");
        assertThat(restoredName).isInstanceOf(BsonJavaScript.class);
        assertThat(((BsonJavaScript) restoredName).getCode())
                .isEqualTo("return 'migration-ready';");
        assertThat(restoredMigration).isInstanceOf(BsonJavaScriptWithScope.class);
        BsonJavaScriptWithScope scopedCode = (BsonJavaScriptWithScope) restoredMigration;
        assertThat(scopedCode.getCode()).isEqualTo("function () { return tenant + ':' + region; }");
        assertThat(scopedCode.getScope().getString("tenant").getValue()).isEqualTo("warehouse");
        assertThat(scopedCode.getScope().getString("region").getValue()).isEqualTo("west");
    }

    private static byte[] encode(BsonDocumentCodec codec, BsonDocument document) {
        try (BasicOutputBuffer output = new BasicOutputBuffer()) {
            BsonBinaryWriter writer = new BsonBinaryWriter(output);
            codec.encode(writer, document, EncoderContext.builder().build());
            writer.close();
            return output.toByteArray();
        }
    }

    private static BsonDocument decode(BsonDocumentCodec codec, byte[] encoded) {
        try (BsonBinaryReader reader = new BsonBinaryReader(ByteBuffer.wrap(encoded))) {
            return codec.decode(reader, DecoderContext.builder().build());
        }
    }
}
