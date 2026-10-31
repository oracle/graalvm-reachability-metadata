/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_api;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InvalidObjectException;
import java.util.Set;

import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.util.FilteredObjectInputStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class FilteredObjectInputStreamTest {

    @Test
    void permitsBuiltInLog4jMessages() throws Exception {
        byte[] bytes = SerializationSupport.serialize(new SimpleMessage("Order accepted"));

        try (FilteredObjectInputStream input = new FilteredObjectInputStream(new ByteArrayInputStream(bytes))) {
            SimpleMessage message = (SimpleMessage) input.readObject();

            assertThat(message.getFormattedMessage()).isEqualTo("Order accepted");
        }
    }

    @Test
    void permitsExplicitlyAllowedClasses() throws Exception {
        File attachment = new File("invoice.csv");
        byte[] bytes = SerializationSupport.serialize(attachment);
        Set<String> allowedClasses = Set.of(File.class.getName());

        try (FilteredObjectInputStream input = new FilteredObjectInputStream(
                new ByteArrayInputStream(bytes), allowedClasses)) {
            assertThat(input.readObject()).isEqualTo(attachment);
            assertThat(input.getAllowedClasses()).containsExactly(File.class.getName());
        }
    }

    @Test
    void rejectsClassesOutsideDefaultAndExplicitAllowLists() throws Exception {
        byte[] bytes = SerializationSupport.serialize(new File("invoice.csv"));

        try (FilteredObjectInputStream input = new FilteredObjectInputStream(new ByteArrayInputStream(bytes))) {
            assertThatThrownBy(input::readObject).isInstanceOf(InvalidObjectException.class)
                    .hasMessageContaining("java.io.File");
        }
    }
}
