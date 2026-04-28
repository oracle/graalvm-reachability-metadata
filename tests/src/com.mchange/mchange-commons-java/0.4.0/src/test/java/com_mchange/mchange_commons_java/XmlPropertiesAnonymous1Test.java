/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import com.mchange.v2.util.XmlProperties;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class XmlPropertiesAnonymous1Test {
    private static final String XML_PROPERTIES = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE xml-properties SYSTEM "http://www.mchange.com/dtd/xml-properties.dtd">
            <xml-properties>
                <property name="untrimmed" trim="false">  spaced value  </property>
                <property name="trimmed" trim="true">
                    compact value
                </property>
            </xml-properties>
            """;

    @Test
    void loadXmlResolvesPackagedDtdThroughAnonymousEntityResolver() throws Exception {
        XmlProperties properties = new XmlProperties();

        properties.loadXml(new ByteArrayInputStream(XML_PROPERTIES.getBytes(StandardCharsets.UTF_8)));

        assertThat(properties.getProperty("untrimmed")).isEqualTo("  spaced value  ");
        assertThat(properties.getProperty("trimmed")).isEqualTo("compact value");
    }
}
