/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_bson;

import org.bson.BSONObject;
import org.bson.BasicBSONDecoder;
import org.bson.BasicBSONEncoder;
import org.bson.BasicBSONObject;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises legacy BSON compatibility for an application's event archive. */
class BsonLegacyCompatibilityTest {

    @Test
    @Timeout(60)
    void restoresAStoredEventForACompatibilityConsumer() {
        ObjectId eventId = new ObjectId("65cbd4000000000000000045");
        BasicBSONObject event = new BasicBSONObject()
                .append("eventId", eventId)
                .append("kind", "shipment.delivered")
                .append("attempt", 2)
                .append("labels", List.of("warehouse", "priority"))
                .append("details", new BasicBSONObject()
                        .append("shipmentNumber", "SHP-2048")
                        .append("parcelCount", 2));

        byte[] encoded = new BasicBSONEncoder().encode(event);
        BSONObject restored = new BasicBSONDecoder().readObject(encoded);
        BasicBSONObject details = (BasicBSONObject) restored.get("details");

        assertThat(encoded).isNotEmpty();
        assertThat(restored.get("eventId")).isEqualTo(eventId);
        assertThat(restored.get("kind")).isEqualTo("shipment.delivered");
        assertThat(restored.get("attempt")).isEqualTo(2);
        assertThat(restored.get("labels")).isEqualTo(List.of("warehouse", "priority"));
        assertThat(details.getString("shipmentNumber")).isEqualTo("SHP-2048");
        assertThat(details.getInt("parcelCount")).isEqualTo(2);
    }
}
