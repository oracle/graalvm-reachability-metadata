/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_wiremock.wiremock;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.common.xml.Xml;
import com.github.tomakehurst.wiremock.common.xml.XmlDocument;
import com.github.tomakehurst.wiremock.common.xml.XmlNode;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class XmlNodeTest {
    @Test
    void rendersXmlNodeSelectedByXPathOnWorkerThread() throws Exception {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            Future<String> renderedXml = executorService.submit(() -> {
                XmlDocument document = Xml.parse("""
                        <catalog>
                          <book id="bk101">
                            <title>XML Developer's Guide</title>
                          </book>
                        </catalog>
                        """);

                XmlNode book = document.findNodes("/catalog/book").getFirst();
                return book.toString();
            });

            assertThat(renderedXml.get(10, TimeUnit.SECONDS))
                    .contains("<book")
                    .contains("id=\"bk101\"")
                    .contains("<title>XML Developer's Guide</title>")
                    .contains("</book>");
        } finally {
            executorService.shutdownNow();
            assertThat(executorService.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
