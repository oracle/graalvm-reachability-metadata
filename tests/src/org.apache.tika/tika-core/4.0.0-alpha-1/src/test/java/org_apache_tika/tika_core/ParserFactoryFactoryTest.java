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

import org.apache.tika.fork.ParserFactoryFactory;
import org.apache.tika.parser.AutoDetectParserFactory;
import org.apache.tika.parser.ParserFactory;

public class ParserFactoryFactoryTest {

    @Test
    public void buildCreatesParserFactoryFromConfiguredClassName() throws Exception {
        Map<String, String> arguments = new HashMap<>();

        ParserFactory parserFactory = new ParserFactoryFactory(
                AutoDetectParserFactory.class.getName(), arguments).build();

        assertThat(parserFactory).isInstanceOf(AutoDetectParserFactory.class);
    }
}
