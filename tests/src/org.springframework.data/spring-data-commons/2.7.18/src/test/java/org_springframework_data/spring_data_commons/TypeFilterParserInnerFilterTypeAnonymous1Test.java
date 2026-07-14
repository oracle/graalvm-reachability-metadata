/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_commons;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.util.Collection;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.parsing.EmptyReaderEventListener;
import org.springframework.beans.factory.parsing.FailFastProblemReporter;
import org.springframework.beans.factory.parsing.NullSourceExtractor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.DefaultNamespaceHandlerResolver;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.beans.factory.xml.XmlReaderContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.data.config.TypeFilterParser;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

public class TypeFilterParserInnerFilterTypeAnonymous1Test {

    @Test
    void parsesAnnotationIncludeFilter() throws Exception {
        TypeFilterParser parser = new TypeFilterParser(xmlReaderContext());
        Document document = parseDocument("""
                <repositories xmlns="http://www.springframework.org/schema/data/repository">
                    <include-filter type="annotation" expression="%s" />
                </repositories>
                """.formatted(RepositoryCandidate.class.getName()));

        Collection<TypeFilter> filters = parser.parseTypeFilters(document.getDocumentElement(),
                TypeFilterParser.Type.INCLUDE);

        assertThat(filters)
                .hasSize(1)
                .allSatisfy(filter -> assertThat(filter).isInstanceOf(AnnotationTypeFilter.class));
    }

    private static XmlReaderContext xmlReaderContext() {
        ClassLoader classLoader = TypeFilterParserInnerFilterTypeAnonymous1Test.class.getClassLoader();
        DefaultResourceLoader resourceLoader = new DefaultResourceLoader(classLoader);
        XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(new DefaultListableBeanFactory());
        reader.setResourceLoader(resourceLoader);

        return new XmlReaderContext(new ByteArrayResource(new byte[0]), new FailFastProblemReporter(),
                new EmptyReaderEventListener(), new NullSourceExtractor(), reader,
                new DefaultNamespaceHandlerResolver(classLoader));
    }

    private static Document parseDocument(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    @interface RepositoryCandidate {
    }
}
