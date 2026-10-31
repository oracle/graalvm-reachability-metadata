/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_xml;

import org.apache.groovy.xml.tools.DomToGroovy;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

public class DomToGroovyTest {
    @Test
    void convertsDomDocumentsIntoBuilderSource() throws Exception {
        Document document = DomToGroovy.parse(new StringReader("""
                <catalog region="EU">
                  <book id="b1">Groovy</book>
                  <!-- second book -->
                  <book id="b2"><title>Native Image</title></book>
                </catalog>
                """));
        StringWriter writer = new StringWriter();

        new DomToGroovy(new PrintWriter(writer)).print(document);

        String source = writer.toString();
        assertThat(source).contains("catalog(region:'EU') {");
        assertThat(source).contains("book(id:'b1', 'Groovy')");
        assertThat(source).contains("/* second book */");
        assertThat(source).contains("book(id:'b2') {");
        assertThat(source).contains("title('Native Image')");
    }
}
