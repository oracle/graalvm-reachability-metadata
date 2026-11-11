/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;

import org.junit.jupiter.api.Test;

import io.grpc.netty.shaded.io.netty.util.internal.ResourcesUtil;

public class ResourcesUtilTest {
    @Test
    void decodesSpacesInThePathOfABundledResource() {
        File resource = ResourcesUtil.getFile(ResourcesUtilTest.class, "resource lookup.txt");
        assertEquals("resource lookup.txt", resource.getName());
    }
}
