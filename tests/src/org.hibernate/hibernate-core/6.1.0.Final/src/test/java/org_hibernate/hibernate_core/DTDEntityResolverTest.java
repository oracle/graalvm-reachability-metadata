/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.internal.util.xml.DTDEntityResolver;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import static org.assertj.core.api.Assertions.assertThat;

public class DTDEntityResolverTest {

    @Test
    public void resolvesHibernateDtdsFromTheLibraryJar() throws Exception {
        InputSource source = new DTDEntityResolver().resolveEntity(
                null,
                "http://www.hibernate.org/dtd/hibernate-mapping-3.0.dtd"
        );

        assertThat(source).isNotNull();
        assertThat(source.getByteStream().read()).isNotNegative();
        source.getByteStream().close();
    }
}
