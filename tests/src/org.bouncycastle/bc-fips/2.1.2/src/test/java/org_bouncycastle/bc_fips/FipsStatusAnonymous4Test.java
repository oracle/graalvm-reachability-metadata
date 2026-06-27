/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bc_fips;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.bouncycastle.crypto.fips.FipsStatus;
import org.junit.jupiter.api.Test;

public class FipsStatusAnonymous4Test {
    private static final String MARKER_RESOURCE = "org/bouncycastle/MARKER";

    @Test
    void bootstrapSourceClassUsesSystemMarkerResourceLookup() throws Throwable {
        MethodHandle getMarker = MethodHandles.privateLookupIn(FipsStatus.class, MethodHandles.lookup()).findStatic(
            FipsStatus.class,
            "getMarker",
            MethodType.methodType(String.class, Class.class, String.class));

        String marker = (String)getMarker.invoke(String.class, MARKER_RESOURCE);

        assertNotNull(marker);
        assertTrue(marker.contains(MARKER_RESOURCE), marker);
    }
}
