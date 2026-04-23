/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_woodstox.woodstox_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctc.wstx.shaded.msv.org_isorelax.dispatcher.Dispatcher;
import com.ctc.wstx.shaded.msv.org_isorelax.dispatcher.Dispatcher.NotationDecl;
import com.ctc.wstx.shaded.msv.org_isorelax.dispatcher.Dispatcher.UnparsedEntityDecl;
import com.ctc.wstx.shaded.msv.org_isorelax.dispatcher.IslandSchema;
import com.ctc.wstx.shaded.msv.org_isorelax.dispatcher.IslandVerifier;
import com.ctc.wstx.shaded.msv.org_isorelax.dispatcher.SchemaProvider;
import com.ctc.wstx.shaded.msv_core.relaxns.verifier.AnyOtherElementVerifier;
import java.util.Collections;
import java.util.Iterator;
import org.junit.jupiter.api.Test;
import org.xml.sax.Attributes;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.LocatorImpl;

public class RelaxnsVerifierLocalizerDynamicAccessTest {
    @Test
    void localizesUnexpectedNamespaceErrors() throws Exception {
        CollectingErrorHandler errorHandler = new CollectingErrorHandler();
        AnyOtherElementVerifier verifier = new AnyOtherElementVerifier(new com.ctc.wstx.shaded.msv_core.relaxns.grammar.relax.AnyOtherElementExp[0]);
        verifier.setDispatcher(new DummyDispatcher(errorHandler));
        verifier.setDocumentLocator(new LocatorImpl());

        verifier.startElement("urn:unexpected", "node", "node", new AttributesImpl());

        assertThat(errorHandler.lastMessage)
                .contains("elements from")
                .contains("urn:unexpected");
    }

    private static final class DummyDispatcher implements Dispatcher {
        private final ErrorHandler errorHandler;

        private DummyDispatcher(ErrorHandler errorHandler) {
            this.errorHandler = errorHandler;
        }

        @Override
        public void attachXMLReader(org.xml.sax.XMLReader reader) {
        }

        @Override
        public void switchVerifier(IslandVerifier verifier) {
        }

        @Override
        public void setErrorHandler(ErrorHandler handler) {
        }

        @Override
        public ErrorHandler getErrorHandler() {
            return errorHandler;
        }

        @Override
        public SchemaProvider getSchemaProvider() {
            return new SchemaProvider() {
                @Override
                public IslandVerifier createTopLevelVerifier() {
                    return null;
                }

                @Override
                public IslandSchema getSchemaByNamespace(String namespaceURI) {
                    return null;
                }

                @Override
                public Iterator iterateNamespace() {
                    return Collections.emptyIterator();
                }

                @Override
                public IslandSchema[] getSchemata() {
                    return new IslandSchema[0];
                }
            };
        }

        @Override
        public int countNotationDecls() {
            return 0;
        }

        @Override
        public NotationDecl getNotationDecl(int index) {
            return null;
        }

        @Override
        public int countUnparsedEntityDecls() {
            return 0;
        }

        @Override
        public UnparsedEntityDecl getUnparsedEntityDecl(int index) {
            return null;
        }
    }

    private static final class CollectingErrorHandler implements ErrorHandler {
        private String lastMessage;

        @Override
        public void warning(SAXParseException exception) {
        }

        @Override
        public void error(SAXParseException exception) {
            lastMessage = exception.getMessage();
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            lastMessage = exception.getMessage();
            throw exception;
        }
    }
}
