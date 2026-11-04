/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_bson;

import org.bson.Document;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.DocumentCodec;
import org.bson.codecs.EncoderContext;
import org.bson.json.JsonMode;
import org.bson.json.JsonReader;
import org.bson.json.JsonWriter;
import org.bson.json.JsonWriterSettings;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises Extended JSON streaming for an application's audit export. */
class BsonJsonExportTest {

    @Test
    @Timeout(60)
    void exportsAndImportsAnAuditRecordWithBsonTypes() {
        ObjectId recordId = new ObjectId("65cbd4000000000000000044");
        Document auditRecord = new Document("_id", recordId)
                .append("action", "shipment.created")
                .append("amount", new Decimal128(new BigDecimal("19.95")))
                .append("createdAt", new Date(1741946400000L))
                .append("labels", List.of("warehouse", "priority"));
        DocumentCodec codec = new DocumentCodec();
        JsonWriterSettings settings = JsonWriterSettings.builder()
                .outputMode(JsonMode.EXTENDED)
                .indent(true)
                .build();

        StringWriter jsonOutput = new StringWriter();
        try (JsonWriter writer = new JsonWriter(jsonOutput, settings)) {
            codec.encode(writer, auditRecord, EncoderContext.builder().build());
        }
        String exportedJson = jsonOutput.toString();

        Document imported;
        try (JsonReader reader = new JsonReader(exportedJson)) {
            imported = codec.decode(reader, DecoderContext.builder().build());
        }

        assertThat(exportedJson).contains("\"$oid\"", "\"$numberDecimal\"");
        assertThat(imported.getObjectId("_id")).isEqualTo(recordId);
        assertThat(imported.getString("action")).isEqualTo("shipment.created");
        assertThat(imported.get("amount")).isEqualTo(new Decimal128(new BigDecimal("19.95")));
        assertThat(imported.getDate("createdAt")).isEqualTo(new Date(1741946400000L));
        assertThat(imported.getList("labels", String.class))
                .containsExactly("warehouse", "priority");
    }
}
