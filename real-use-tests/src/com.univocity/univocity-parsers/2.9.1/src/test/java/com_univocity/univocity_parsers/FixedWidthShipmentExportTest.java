/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_univocity.univocity_parsers;

import com.univocity.parsers.fixed.FieldAlignment;
import com.univocity.parsers.fixed.FixedWidthFields;
import com.univocity.parsers.fixed.FixedWidthWriter;
import com.univocity.parsers.fixed.FixedWidthWriterSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the fixed-width writing subsystem to export a shipment manifest.
 */
public class FixedWidthShipmentExportTest {
    @Test
    @Timeout(60)
    public void exportsAlignedAndPaddedShipmentRows() {
        FixedWidthFields fields = new FixedWidthFields(
                new String[]{"shipment_id", "carrier", "units"},
                new int[]{10, 12, 5});
        fields.setAlignment(FieldAlignment.LEFT, "shipment_id", "carrier");
        fields.setAlignment(FieldAlignment.RIGHT, "units");
        fields.setPadding('0', "units");

        FixedWidthWriterSettings settings = new FixedWidthWriterSettings(fields);
        settings.setWriteLineSeparatorAfterRecord(true);
        StringWriter output = new StringWriter();
        FixedWidthWriter writer = new FixedWidthWriter(output, settings);
        writer.writeHeaders();
        writer.writeRow("SHP-1042", "ParcelPost", 18);
        writer.writeRow("SHP-1043", "AirExpress", 7);
        writer.close();

        assertEquals(
                "shipment_icarrier     units\n"
                        + "SHP-1042  ParcelPost  00018\n"
                        + "SHP-1043  AirExpress  00007\n",
                output.toString());
    }
}
