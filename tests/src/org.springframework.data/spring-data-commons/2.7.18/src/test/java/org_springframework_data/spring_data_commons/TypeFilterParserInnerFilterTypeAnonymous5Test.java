/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_commons;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.beans.factory.xml.XmlReaderContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.data.config.TypeFilterParser;
import org.w3c.dom.Document;

public class TypeFilterParserInnerFilterTypeAnonymous5Test {

    @Test
    void parsesCustomIncludeFilterWithConfiguredClassLoader() throws Exception {
        ClassLoader classLoader = TypeFilterParserInnerFilterTypeAnonymous5Test.class.getClassLoader();
        TypeFilterParser parser = new TypeFilterParser(createXmlReaderContext(classLoader));
        Document document = parseDocument("""
                <repository:repositories xmlns:repository="http://www.springframework.org/schema/data/repository">
                    <repository:include-filter type="custom" expression="%s" />
                </repository:repositories>
                """.formatted(RepositoryNameTypeFilter.class.getName()));

        Collection<TypeFilter> filters = parser.parseTypeFilters(document.getDocumentElement(),
                TypeFilterParser.Type.INCLUDE);

        assertThat(filters).hasSize(1);
        TypeFilter filter = filters.iterator().next();
        MetadataReaderFactory metadataReaderFactory = new SimpleMetadataReaderFactory(classLoader);
        MetadataReader matchingType = metadataReaderFactory.getMetadataReader(SampleRepository.class.getName());
        MetadataReader nonMatchingType = metadataReaderFactory.getMetadataReader(SampleService.class.getName());
        assertThat(filter.match(matchingType, metadataReaderFactory)).isTrue();
        assertThat(filter.match(nonMatchingType, metadataReaderFactory)).isFalse();
    }

    private static XmlReaderContext createXmlReaderContext(ClassLoader classLoader) {
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();
        XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(registry);
        reader.setBeanClassLoader(classLoader);
        reader.setValidationMode(XmlBeanDefinitionReader.VALIDATION_NONE);
        reader.setNamespaceAware(true);
        ByteArrayResource resource = new ByteArrayResource(new byte[0], "type filter parser test resource");
        return reader.createReaderContext(resource);
    }

    private static Document parseDocument(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    public static final class RepositoryNameTypeFilter implements TypeFilter {

        @Override
        public boolean match(MetadataReader metadataReader, MetadataReaderFactory metadataReaderFactory)
                throws IOException {
            return metadataReader.getClassMetadata().getClassName().endsWith("Repository");
        }
    }

    static final class SampleRepository {
    }

    static final class SampleService {
    }
}
