/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_w3c_css.sac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.css.sac.ConditionFactory;
import org.w3c.css.sac.CSSException;
import org.w3c.css.sac.DocumentHandler;
import org.w3c.css.sac.ErrorHandler;
import org.w3c.css.sac.InputSource;
import org.w3c.css.sac.LexicalUnit;
import org.w3c.css.sac.Parser;
import org.w3c.css.sac.SelectorFactory;
import org.w3c.css.sac.SelectorList;
import org.w3c.css.sac.helpers.ParserFactory;

public class ParserFactoryTest {
    private static final String PARSER_PROPERTY = "org.w3c.css.sac.parser";

    private String previousParserProperty;

    @BeforeEach
    void rememberParserProperty() {
        previousParserProperty = System.getProperty(PARSER_PROPERTY);
    }

    @AfterEach
    void restoreParserProperty() {
        if (previousParserProperty == null) {
            System.clearProperty(PARSER_PROPERTY);
        } else {
            System.setProperty(PARSER_PROPERTY, previousParserProperty);
        }
    }

    @Test
    void makeParserInstantiatesConfiguredParserClass() throws Exception {
        System.setProperty(PARSER_PROPERTY, TestParser.class.getName());

        Parser parser = new ParserFactory().makeParser();

        assertThat(parser).isInstanceOf(TestParser.class);
        assertThat(parser.getParserVersion()).isEqualTo("test-parser");
    }

    @Test
    void makeParserReportsMissingParserProperty() {
        System.clearProperty(PARSER_PROPERTY);

        assertThatThrownBy(() -> new ParserFactory().makeParser())
                .isInstanceOf(NullPointerException.class)
                .hasMessage("No value for sac.parser property");
    }

    public static class TestParser implements Parser {
        @Override
        public void setLocale(Locale locale) throws CSSException {
        }

        @Override
        public void setDocumentHandler(DocumentHandler handler) {
        }

        @Override
        public void setSelectorFactory(SelectorFactory selectorFactory) {
        }

        @Override
        public void setConditionFactory(ConditionFactory conditionFactory) {
        }

        @Override
        public void setErrorHandler(ErrorHandler handler) {
        }

        @Override
        public void parseStyleSheet(InputSource source) throws CSSException, IOException {
        }

        @Override
        public void parseStyleSheet(String uri) throws CSSException, IOException {
        }

        @Override
        public void parseStyleDeclaration(InputSource source) throws CSSException, IOException {
        }

        @Override
        public void parseRule(InputSource source) throws CSSException, IOException {
        }

        @Override
        public String getParserVersion() {
            return "test-parser";
        }

        @Override
        public SelectorList parseSelectors(InputSource source) throws CSSException, IOException {
            return null;
        }

        @Override
        public LexicalUnit parsePropertyValue(InputSource source) throws CSSException, IOException {
            return null;
        }

        @Override
        public boolean parsePriority(InputSource source) throws CSSException, IOException {
            return false;
        }
    }
}
