/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_data.spring_data_commons;

import java.io.StringReader;
import java.util.Collection;

import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.beans.factory.parsing.EmptyReaderEventListener;
import org.springframework.beans.factory.parsing.FailFastProblemReporter;
import org.springframework.beans.factory.parsing.NullSourceExtractor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.DefaultNamespaceHandlerResolver;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.beans.factory.xml.XmlReaderContext;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.data.config.TypeFilterParser;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

final class TypeFilterTestSupport {

    private TypeFilterTestSupport() {
    }

    static Collection<TypeFilter> parse(String type, Class<?> filterClass) throws Exception {
        ClassLoader loader = TypeFilterTestSupport.class.getClassLoader();
        XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(new DefaultListableBeanFactory());
        reader.setBeanClassLoader(loader);
        XmlReaderContext context = new XmlReaderContext(new ByteArrayResource(new byte[0]),
                new FailFastProblemReporter(), new EmptyReaderEventListener(), new NullSourceExtractor(), reader,
                new DefaultNamespaceHandlerResolver(loader));
        DocumentBuilderFactory documents = DocumentBuilderFactory.newInstance();
        documents.setNamespaceAware(true);
        Document document = documents.newDocumentBuilder().parse(new InputSource(new StringReader("""
                <repositories xmlns="http://www.springframework.org/schema/data/repository">
                    <include-filter type="%s" expression="%s" />
                </repositories>
                """.formatted(type, filterClass.getName()))));
        return new TypeFilterParser(context).parseTypeFilters(document.getDocumentElement(),
                TypeFilterParser.Type.INCLUDE);
    }
}
