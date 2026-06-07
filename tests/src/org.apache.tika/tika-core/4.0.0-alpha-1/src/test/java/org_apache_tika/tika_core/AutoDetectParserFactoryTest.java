/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tika.tika_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.AutoDetectParserFactory;
import org.apache.tika.parser.Parser;

public class AutoDetectParserFactoryTest {

    private static final String CLASSPATH_TIKA_CONFIG =
            "/org_apache_tika/tika_core/autodetect-parser-factory-tika-config.xml";

    @Test
    public void buildLoadsTikaConfigFromClasspathResource() throws Exception {
        Map<String, String> arguments = new HashMap<>();
        arguments.put(AutoDetectParserFactory.TIKA_CONFIG_PATH, CLASSPATH_TIKA_CONFIG);

        Parser parser = new AutoDetectParserFactory(arguments).build();

        assertThat(parser).isInstanceOf(AutoDetectParser.class);
        AutoDetectParser autoDetectParser = (AutoDetectParser) parser;
        assertThat(autoDetectParser.getAutoDetectParserConfig().getThrowOnZeroBytes()).isFalse();
        assertThat(arguments).doesNotContainKey(AutoDetectParserFactory.TIKA_CONFIG_PATH);
    }
}
