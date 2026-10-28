/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.apache.ibatis.io.Resources;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClassLoaderWrapperTest {
    @Test
    void resolvesBundledMapperDtdAsUrl() throws Exception {
        URL url = Resources.getResourceURL("org/apache/ibatis/builder/xml/mybatis-3-mapper.dtd");
        try (InputStream stream = url.openStream()) {
            assertTrue(new String(stream.readAllBytes(), StandardCharsets.UTF_8).contains("<!ELEMENT mapper"));
        }
    }
}
