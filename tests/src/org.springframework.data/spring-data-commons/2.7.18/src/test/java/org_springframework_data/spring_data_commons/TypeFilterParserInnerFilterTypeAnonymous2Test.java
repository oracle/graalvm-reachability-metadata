/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_commons;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
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

public class TypeFilterParserInnerFilterTypeAnonymous2Test {

    @Test
    void parsesAssignableIncludeFilterWithConfiguredClassLoader() throws Exception {
        ClassLoader classLoader = TypeFilterParserInnerFilterTypeAnonymous2Test.class.getClassLoader();
        TypeFilterParser parser = new TypeFilterParser(createXmlReaderContext(classLoader));
        Document document = parseDocument("""
                <repository:repositories xmlns:repository="http://www.springframework.org/schema/data/repository">
                    <repository:include-filter type="assignable" expression="%s" />
                </repository:repositories>
                """.formatted(BaseRepositoryFragment.class.getName()));

        Collection<TypeFilter> filters = parser.parseTypeFilters(document.getDocumentElement(),
                TypeFilterParser.Type.INCLUDE);

        assertThat(filters).hasSize(1);
        TypeFilter filter = filters.iterator().next();
        MetadataReaderFactory metadataReaderFactory = new SimpleMetadataReaderFactory(classLoader);
        MetadataReader assignableType = metadataReaderFactory.getMetadataReader(ConcreteRepositoryFragment.class.getName());
        MetadataReader unrelatedType = metadataReaderFactory.getMetadataReader(UnrelatedRepositoryFragment.class.getName());
        assertThat(filter.match(assignableType, metadataReaderFactory)).isTrue();
        assertThat(filter.match(unrelatedType, metadataReaderFactory)).isFalse();
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

    interface BaseRepositoryFragment {
    }

    static final class ConcreteRepositoryFragment implements BaseRepositoryFragment {
    }

    static final class UnrelatedRepositoryFragment {
    }
}
