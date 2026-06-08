/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_webmvc;

import java.io.StringReader;
import java.nio.file.Path;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamSource;

final class XsltViewNativeImageSupport {
    static final String TRANSLET_NAME = "StaticStylesheetTranslet";
    static final String TRANSLET_PACKAGE_NAME = "org_springframework.spring_webmvc.xslt";
    static final String TRANSLET_CLASS_FILE = "org_springframework/spring_webmvc/xslt/StaticStylesheetTranslet.class";

    private static final String DESTINATION_DIRECTORY_ATTRIBUTE = "destination-directory";
    private static final String GENERATE_TRANSLET_ATTRIBUTE = "generate-translet";
    private static final String PACKAGE_NAME_ATTRIBUTE = "package-name";
    private static final String TRANSLET_NAME_ATTRIBUTE = "translet-name";
    private static final String USE_CLASSPATH_ATTRIBUTE = "use-classpath";
    private static final String STYLESHEET_SYSTEM_ID = "test-stylesheet";
    private static final String STYLESHEET = String.join("",
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
            "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">",
            "<xsl:output method=\"text\" encoding=\"UTF-8\" media-type=\"text/plain\"/>",
            "<xsl:param name=\"greeting\"/>",
            "<xsl:template match=\"/\"><xsl:value-of select=\"$greeting\"/><xsl:text> </xsl:text>",
            "<xsl:value-of select=\"/message/@name\"/></xsl:template>",
            "</xsl:stylesheet>");

    private XsltViewNativeImageSupport() {
    }

    static void configureTransletGeneration(TransformerFactory transformerFactory, Path outputDirectory) {
        transformerFactory.setAttribute(DESTINATION_DIRECTORY_ATTRIBUTE, outputDirectory.toString());
        transformerFactory.setAttribute(PACKAGE_NAME_ATTRIBUTE, TRANSLET_PACKAGE_NAME);
        transformerFactory.setAttribute(TRANSLET_NAME_ATTRIBUTE, TRANSLET_NAME);
        transformerFactory.setAttribute(GENERATE_TRANSLET_ATTRIBUTE, Boolean.TRUE);
    }

    static void configureUseClasspath(TransformerFactory transformerFactory) {
        transformerFactory.setAttribute(PACKAGE_NAME_ATTRIBUTE, TRANSLET_PACKAGE_NAME);
        transformerFactory.setAttribute(TRANSLET_NAME_ATTRIBUTE, TRANSLET_NAME);
        transformerFactory.setAttribute(USE_CLASSPATH_ATTRIBUTE, Boolean.TRUE);
    }

    static Source newStylesheetSource() {
        return new StreamSource(new StringReader(STYLESHEET), STYLESHEET_SYSTEM_ID);
    }
}
