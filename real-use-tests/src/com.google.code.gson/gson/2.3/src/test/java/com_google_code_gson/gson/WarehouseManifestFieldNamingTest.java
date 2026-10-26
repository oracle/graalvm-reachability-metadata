/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Gson's field-naming subsystem to exchange snake-case warehouse manifests with vendor-specific keys.
public class WarehouseManifestFieldNamingTest {

    @Test
    @Timeout(60)
    public void importsWarehouseNamesAndExportsACompletedPickingManifest() {
        Gson gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create();
        Manifest manifest = gson.fromJson("""
                {
                  "manifest-id": "M-207",
                  "destination_code": "shop-west",
                  "ready_for_dispatch": false,
                  "line_items": [
                    {"SKU": "notebook", "requested_units": 3, "picked_units": 0},
                    {"SKU": "pencil", "requested_units": 2, "picked_units": 0}
                  ]
                }
                """, Manifest.class);

        assertEquals("M-207", manifest.manifestId);
        assertEquals("shop-west", manifest.destinationCode);
        assertFalse(manifest.readyForDispatch);
        assertEquals(2, manifest.lineItems.size());
        assertEquals("notebook", manifest.lineItems.get(0).productCode);
        assertEquals(3, manifest.lineItems.get(0).requestedUnits);
        assertEquals("pencil", manifest.lineItems.get(1).productCode);
        assertEquals(2, manifest.lineItems.get(1).requestedUnits);

        int pickedTotal = 0;
        for (ManifestLine line : manifest.lineItems) {
            assertEquals(0, line.pickedUnits);
            line.pickedUnits = line.requestedUnits;
            pickedTotal += line.pickedUnits;
        }
        assertEquals(5, pickedTotal);
        manifest.readyForDispatch = true;

        String acknowledgement = gson.toJson(manifest);
        JsonObject exported = new JsonParser().parse(acknowledgement).getAsJsonObject();
        assertEquals(new JsonParser().parse("""
                {
                  "manifest-id": "M-207",
                  "destination_code": "shop-west",
                  "ready_for_dispatch": true,
                  "line_items": [
                    {"SKU": "notebook", "requested_units": 3, "picked_units": 3},
                    {"SKU": "pencil", "requested_units": 2, "picked_units": 2}
                  ]
                }
                """), exported);
        assertFalse(exported.has("manifest_id"));
        assertFalse(exported.getAsJsonArray("line_items").get(0).getAsJsonObject().has("product_code"));

        Manifest restored = gson.fromJson(acknowledgement, Manifest.class);
        assertEquals("M-207", restored.manifestId);
        assertTrue(restored.readyForDispatch);
        assertEquals(3, restored.lineItems.get(0).pickedUnits);
        assertEquals(2, restored.lineItems.get(1).pickedUnits);
    }

    public static class Manifest {
        @SerializedName("manifest-id")
        private String manifestId;
        private String destinationCode;
        private boolean readyForDispatch;
        private List<ManifestLine> lineItems;
    }

    public static class ManifestLine {
        @SerializedName("SKU")
        private String productCode;
        private int requestedUnits;
        private int pickedUnits;
    }
}
