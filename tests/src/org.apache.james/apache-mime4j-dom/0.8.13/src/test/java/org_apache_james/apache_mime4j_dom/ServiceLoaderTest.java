/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_james.apache_mime4j_dom;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.mime4j.dom.MessageBuilder;
import org.apache.james.mime4j.dom.MessageServiceFactory;
import org.apache.james.mime4j.dom.MessageWriter;
import org.junit.jupiter.api.Test;

public class ServiceLoaderTest {
    @Test
    public void loadsBundledMessageServiceFactoryProvider() throws Exception {
        MessageServiceFactory factory = MessageServiceFactory.newInstance();

        assertThat(factory).isNotNull();

        MessageBuilder builder = factory.newMessageBuilder();
        MessageWriter writer = factory.newMessageWriter();

        assertThat(builder).isNotNull();
        assertThat(writer).isNotNull();
    }
}
