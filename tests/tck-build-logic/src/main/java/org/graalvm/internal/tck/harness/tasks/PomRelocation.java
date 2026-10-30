/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.util.Optional;

/// Reads where a Maven POM relocates its artifact, so compatibility discovery can drop versions
/// that only point elsewhere (§FS-library-version-update-automation.1).
final class PomRelocation {

    private PomRelocation() {
    }

    /// Returns the coordinates `pom` relocates `group:artifact:version` to, when they differ from its
    /// own. Only `project/distributionManagement/relocation` moves the artifact (a shade plugin's
    /// `relocation` does not), and an omitted part keeps the artifact's own. A POM that does not
    /// parse keeps its version a candidate, so the tests decide.
    static Optional<String> target(String pom, String group, String artifact, String version) {
        Element project;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            // Fails on a malformed POM without printing it to stderr.
            builder.setErrorHandler(new DefaultHandler());
            project = builder.parse(new InputSource(new StringReader(pom))).getDocumentElement();
        } catch (ParserConfigurationException | SAXException | IOException e) {
            return Optional.empty();
        }
        Optional<Element> relocation = childElement(project, "distributionManagement")
                .flatMap(distributionManagement -> childElement(distributionManagement, "relocation"));
        if (relocation.isEmpty()) {
            return Optional.empty();
        }
        String target = childText(relocation.get(), "groupId", group) + ":"
                + childText(relocation.get(), "artifactId", artifact) + ":"
                + childText(relocation.get(), "version", version);
        return target.equals(group + ":" + artifact + ":" + version) ? Optional.empty() : Optional.of(target);
    }

    private static Optional<Element> childElement(Element parent, String localName) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && localName.equals(element.getLocalName())) {
                return Optional.of(element);
            }
        }
        return Optional.empty();
    }

    private static String childText(Element parent, String localName, String fallback) {
        return childElement(parent, localName)
                .map(element -> element.getTextContent().trim())
                .filter(text -> !text.isEmpty())
                .orElse(fallback);
    }
}
