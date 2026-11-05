/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_bson;

import org.bson.Document;
import org.bson.RawBsonDocument;
import org.bson.codecs.DocumentCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises raw BSON documents for inspecting and forwarding an event payload. */
class BsonRawDocumentPipelineTest {

    @Test
    @Timeout(60)
    void inspectsAnEventBeforeDecodingItsRawPayload() {
        RawBsonDocument rawEvent = RawBsonDocument.parse("""
                {
                  "eventId": "evt-2048",
                  "kind": "shipment.created",
                  "payload": {"shipmentNumber": "SHP-2048", "parcelCount": 2}
                }
                """);

        assertThat(rawEvent.getByteLength()).isGreaterThan(0);
        assertThat(rawEvent.getString("kind").getValue()).isEqualTo("shipment.created");
        assertThat(rawEvent.getDocument("payload").getInt32("parcelCount").getValue())
                .isEqualTo(2);

        Document decoded = rawEvent.decode(new DocumentCodec());

        assertThat(decoded.getString("eventId")).isEqualTo("evt-2048");
        assertThat(decoded.getString("kind")).isEqualTo("shipment.created");
        assertThat(decoded.getEmbedded(
                List.of("payload", "shipmentNumber"), String.class))
                .isEqualTo("SHP-2048");
    }
}
