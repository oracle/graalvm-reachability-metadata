/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package netty;

import java.io.File;

import io.netty.util.internal.ResourcesUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ResourcesUtilTest {
    @Test
    void decodesPackagedResourceFileName() {
        File file = ResourcesUtil.getFile(ResourcesUtilTest.class, "ResourcesUtilTest resource.txt");
        Assertions.assertEquals("ResourcesUtilTest resource.txt", file.getName());
    }
}
