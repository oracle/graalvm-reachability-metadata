/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import com.thoughtworks.xstream.core.ClassLoaderReference;
import com.thoughtworks.xstream.mapper.CGLIBMapper;
import com.thoughtworks.xstream.mapper.DefaultMapper;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CGLIBMapperTest {
    private static final String CGLIB_PROXY_ALIAS = "sample-cglib-proxy";
    private static final String MARKER_TYPE_NAME = "com.thoughtworks.xstream.mapper."
        + "CGLIBMapper$Marker";

    @Test
    void resolvesCglibProxyAliasToMarkerType() {
        CGLIBMapper mapper = new CGLIBMapper(new DefaultMapper(new ClassLoaderReference(
            CGLIBMapperTest.class.getClassLoader())), CGLIB_PROXY_ALIAS);

        Class<?> markerType = mapper.realClass(CGLIB_PROXY_ALIAS);

        assertThat(markerType.getName()).isEqualTo(MARKER_TYPE_NAME);
    }
}
