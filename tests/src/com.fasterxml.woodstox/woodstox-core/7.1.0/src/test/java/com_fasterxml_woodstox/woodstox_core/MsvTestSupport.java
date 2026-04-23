/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_woodstox.woodstox_core;

import com.ctc.wstx.shaded.msv_core.reader.GrammarReaderController;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;

final class MsvTestSupport {
    private MsvTestSupport() {
    }

    static SAXParserFactory namespaceAwareParserFactory() {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory;
    }

    static RecordingController recordingController() {
        return new RecordingController();
    }

    static final class RecordingController implements GrammarReaderController {
        private final List<String> warnings = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();

        @Override
        public void warning(Locator[] locations, String message) {
            warnings.add(message);
        }

        @Override
        public void error(Locator[] locations, String message, Exception nestedException) {
            errors.add(message);
        }

        @Override
        public InputSource resolveEntity(String publicId, String systemId) {
            return null;
        }

        List<String> warnings() {
            return warnings;
        }

        List<String> errors() {
            return errors;
        }
    }
}
