/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.models.GeoPoint;
import com.azure.core.util.serializer.JacksonAdapter;
import com.azure.core.util.serializer.SerializerEncoding;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class JsonSerializableDeserializerTest {
    @Test
    void deserializesNestedJsonSerializableModel() throws Exception {
        GeoEnvelope decoded = new JacksonAdapter().deserialize(
                "{\"point\":{\"type\":\"Point\",\"coordinates\":[12.5,-7.25]}}",
                GeoEnvelope.class,
                SerializerEncoding.JSON);

        assertThat(decoded.point.getCoordinates().getLongitude()).isEqualTo(12.5);
        assertThat(decoded.point.getCoordinates().getLatitude()).isEqualTo(-7.25);
    }

    public static final class GeoEnvelope {
        @JsonProperty("point")
        private GeoPoint point;

        public GeoPoint getPoint() {
            return point;
        }

        public void setPoint(GeoPoint point) {
            this.point = point;
        }
    }
}
