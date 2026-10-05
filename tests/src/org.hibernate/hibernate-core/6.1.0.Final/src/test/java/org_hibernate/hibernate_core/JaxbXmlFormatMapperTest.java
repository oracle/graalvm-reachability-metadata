/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.type.JaxbXmlFormatMapper;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.spi.TypeConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JaxbXmlFormatMapperTest {

    @Test
    public void roundTripsAnArrayThroughThePublicFormatMapper() {
        TypeConfiguration typeConfiguration = new TypeConfiguration();
        JavaType<String[]> arrayType = typeConfiguration.getJavaTypeRegistry().getDescriptor(String[].class);
        JaxbXmlFormatMapper mapper = JaxbXmlFormatMapper.INSTANCE;

        String xml = mapper.toString(new String[]{"first", "second"}, arrayType, null);
        String[] result = mapper.fromString(xml, arrayType, null);

        assertThat(result).containsExactly("first", "second");
    }

    @Test
    public void createsMissingArrayElementsThroughTheirDefaultConstructor() {
        TypeConfiguration typeConfiguration = new TypeConfiguration();
        JavaType<DefaultConstructibleValue[]> arrayType = typeConfiguration.getJavaTypeRegistry()
                .getDescriptor(DefaultConstructibleValue[].class);
        JaxbXmlFormatMapper mapper = JaxbXmlFormatMapper.INSTANCE;

        String xml = mapper.toString(new DefaultConstructibleValue[0], arrayType, null);
        DefaultConstructibleValue[] result = mapper.fromString(xml, arrayType, null);

        assertThat(result).isEmpty();
    }

    public static class DefaultConstructibleValue {
        public DefaultConstructibleValue() {
        }
    }
}
