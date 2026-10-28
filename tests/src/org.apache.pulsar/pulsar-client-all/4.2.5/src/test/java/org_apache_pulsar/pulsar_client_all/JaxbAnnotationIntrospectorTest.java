/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;
import org.apache.pulsar.shade.javax.xml.bind.annotation.XmlEnumValue;
import org.junit.jupiter.api.Test;

public class JaxbAnnotationIntrospectorTest {
    @Test
    void discoversXmlEnumValues() {
        JaxbAnnotationIntrospector introspector = new JaxbAnnotationIntrospector();
        Status[] values = Status.values();
        String[] names = new String[values.length];

        introspector.findEnumValues(Status.class, values, names);

        assertThat(names).containsExactly("in-progress", null);
    }

    public enum Status {
        @XmlEnumValue("in-progress")
        IN_PROGRESS,
        DONE
    }
}
