/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_orbisgis.cts;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.cts.CRSFactory;
import org.cts.crs.CoordinateReferenceSystem;
import org.cts.registry.EPSGRegistry;
import org.junit.jupiter.api.Test;

public class ProjParserTest {
    @Test
    void listsCodesFromTheEpsgRegistry() throws Exception {
        CRSFactory factory = newFactory();

        Set<String> codes = factory.getSupportedCodes("epsg");

        assertThat(codes).contains("4326");
    }

    @Test
    void createsCoordinateReferenceSystemFromEpsgCode() throws Exception {
        CRSFactory factory = newFactory();

        CoordinateReferenceSystem crs = factory.getCRS("epsg:4326");

        assertThat(crs.getCode()).isEqualTo("epsg:4326");
        assertThat(crs.getType()).isEqualTo(CoordinateReferenceSystem.Type.GEOGRAPHIC2D);
    }

    private static CRSFactory newFactory() {
        CRSFactory factory = new CRSFactory();
        factory.getRegistryManager().addRegistry(new EPSGRegistry());
        return factory;
    }
}
