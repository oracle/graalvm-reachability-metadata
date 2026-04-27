/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_common;

import java.io.File;

import io.netty.util.internal.ResourcesUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ResourcesUtilTest {
    private static final String RESOURCE_NAME = "ResourcesUtilTest resource.txt";

    @Test
    void getFileDecodesResourceUrlIntoAFileName() {
        File file = ResourcesUtil.getFile(ResourcesUtilTest.class, RESOURCE_NAME);

        Assertions.assertEquals(RESOURCE_NAME, file.getName());
    }
}
