/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import org.junit.jupiter.api.Test;

public class ReflectionHelperInnerRecordSupportedHelperTest {
    @Test
    void mapsRecordComponentsAndCanonicalConstructor() {
        Gson gson = new Gson();

        Shipment shipment = gson.fromJson("""
                {"tracking_id":"parcel-42","pieces":3,"insured":true}
                """, Shipment.class);

        assertThat(shipment).isEqualTo(new Shipment("parcel-42", 3, true));
        JsonObject json = JsonParser.parseString(gson.toJson(shipment)).getAsJsonObject();
        assertThat(json.size()).isEqualTo(3);
        assertThat(json.get("tracking_id").getAsString()).isEqualTo("parcel-42");
        assertThat(json.get("pieces").getAsInt()).isEqualTo(3);
        assertThat(json.get("insured").getAsBoolean()).isTrue();
    }

    public record Shipment(@SerializedName("tracking_id") String trackingId, int pieces, boolean insured) {
    }
}
