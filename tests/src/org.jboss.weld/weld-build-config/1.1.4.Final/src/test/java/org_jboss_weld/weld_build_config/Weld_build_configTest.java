/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_weld.weld_build_config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class Weld_build_configTest {
    private static final String CHECKSTYLE_RESOURCE = "weld-checkstyle/checkstyle.xml";
    private static final String POM_PROPERTIES_RESOURCE =
            "META-INF/maven/org.jboss.weld/weld-build-config/pom.properties";
    private static final String POM_RESOURCE = "META-INF/maven/org.jboss.weld/weld-build-config/pom.xml";
    private static final String MANIFEST_RESOURCE = "META-INF/MANIFEST.MF";
    private static final String DEPENDENCIES_RESOURCE = "META-INF/DEPENDENCIES.txt";
    private static final String MAVEN_NAMESPACE = "http://maven.apache.org/POM/4.0.0";

    @Test
    void checkstyleConfigurationIsAvailableAsSingleClasspathResource() throws IOException {
        ClassLoader classLoader = Weld_build_configTest.class.getClassLoader();

        URL resource = classLoader.getResource(CHECKSTYLE_RESOURCE);
        List<URL> allResources = Collections.list(classLoader.getResources(CHECKSTYLE_RESOURCE));
        String xml = readResource(CHECKSTYLE_RESOURCE);

        assertThat(resource).isNotNull();
        assertThat(allResources).containsExactly(resource);
        assertThat(xml)
                .startsWith("<?xml version=\"1.0\"?>")
                .contains("-//Puppy Crawl//DTD Check Configuration 1.2//EN")
                .contains("<module name=\"Checker\">")
                .contains("<module name=\"TreeWalker\">")
                .doesNotContain("\t");
    }

    @Test
    void checkstyleConfigurationDefinesTopLevelChecks() throws Exception {
        Document document = parseXml(readResource(CHECKSTYLE_RESOURCE), false);
        Element checker = document.getDocumentElement();

        assertThat(checker.getTagName()).isEqualTo("module");
        assertThat(checker.getAttribute("name")).isEqualTo("Checker");
        assertThat(moduleNames(directChildModules(checker)))
                .containsExactly("FileTabCharacter", "RegexpSingleline", "TreeWalker");
        assertThat(propertyValue(moduleNamed(checker, "FileTabCharacter"), "eachLine")).isEqualTo("true");

        Element trailingSpacesCheck = moduleNamed(checker, "RegexpSingleline");
        assertThat(propertyValue(trailingSpacesCheck, "format")).isEqualTo("\\s+$");
        assertThat(propertyValue(trailingSpacesCheck, "message")).isEqualTo("Line has trailing spaces.");
    }

    @Test
    void treeWalkerContainsExpectedWeldStyleChecks() throws Exception {
        Document document = parseXml(readResource(CHECKSTYLE_RESOURCE), false);
        Element treeWalker = moduleNamed(document.getDocumentElement(), "TreeWalker");

        assertThat(propertyValue(treeWalker, "cacheFile")).isEqualTo("${checkstyle.cache.file}");
        assertThat(moduleNames(directChildModules(treeWalker)))
                .containsExactly(
                        "AvoidStarImport",
                        "RedundantImport",
                        "ModifierOrder",
                        "RedundantModifier",
                        "LeftCurly",
                        "EmptyStatement",
                        "EqualsHashCode",
                        "IllegalInstantiation",
                        "RedundantThrows",
                        "UpperEll",
                        "PackageAnnotation",
                        "CovariantEquals",
                        "ArrayTypeStyle");
        assertThat(propertyValue(moduleNamed(treeWalker, "RedundantThrows"), "allowUnchecked")).isEqualTo("true");
    }

    @Test
    void mavenDescriptorIdentifiesPublishedBuildConfigurationArtifact() throws Exception {
        Properties properties = new Properties();
        try (InputStream inputStream = resourceStream(POM_PROPERTIES_RESOURCE)) {
            properties.load(inputStream);
        }

        assertThat(properties)
                .containsEntry("groupId", "org.jboss.weld")
                .containsEntry("artifactId", "weld-build-config")
                .containsEntry("version", "1.1.4.Final");

        Document pom = parseXml(readResource(POM_RESOURCE), true);
        Element project = pom.getDocumentElement();
        Element parent = directChildElement(project, "parent");
        Element build = directChildElement(project, "build");
        Element extensions = directChildElement(build, "extensions");
        Element extension = directChildElement(extensions, "extension");

        assertThat(project.getLocalName()).isEqualTo("project");
        assertThat(directChildText(project, "groupId")).isEqualTo("org.jboss.weld");
        assertThat(directChildText(project, "artifactId")).isEqualTo("weld-build-config");
        assertThat(directChildText(project, "version")).isEqualTo("1.1.4.Final");
        assertThat(directChildText(project, "name")).isEqualTo("Weld: Build Configuration");
        assertThat(directChildText(parent, "artifactId")).isEqualTo("weld-parent");
        assertThat(directChildText(extension, "artifactId")).isEqualTo("wagon-webdav-jackrabbit");
    }

    @Test
    void manifestPublishesSpecificationAndBuildMetadata() throws Exception {
        Properties properties = new Properties();
        try (InputStream inputStream = resourceStream(POM_PROPERTIES_RESOURCE)) {
            properties.load(inputStream);
        }
        Element project = parseXml(readResource(POM_RESOURCE), true).getDocumentElement();
        String projectName = directChildText(project, "name");

        Manifest manifest = manifestWithSpecificationTitle(projectName);
        Attributes mainAttributes = manifest.getMainAttributes();
        Attributes buildInformation = manifest.getAttributes("Build-Information");

        assertThat(mainAttributes.getValue("Specification-Title")).isEqualTo(projectName);
        assertThat(mainAttributes.getValue("Specification-Version")).isEqualTo(properties.getProperty("version"));
        assertThat(mainAttributes.getValue("Specification-Vendor")).isEqualTo("Seam Framework");
        assertThat(mainAttributes.getValue("Implementation-Title")).isEqualTo(projectName);
        assertThat(mainAttributes.getValue("Implementation-URL"))
                .startsWith("http://")
                .contains(properties.getProperty("artifactId"));
        assertThat(buildInformation).isNotNull();
        assertThat(buildInformation.getValue("Maven-Version")).isNotBlank();
        assertThat(buildInformation.getValue("Build-Time")).isEqualTo(mainAttributes.getValue("Implementation-Version"));
        assertThat(buildInformation.getValue("SCM")).isNotBlank();
    }

    @Test
    void dependencyReportDocumentsThatBuildConfigurationAddsNoTransitiveDependencies() throws Exception {
        Element project = parseXml(readResource(POM_RESOURCE), true).getDocumentElement();
        String projectName = directChildText(project, "name");
        String dependencyReport = readResourceContaining(DEPENDENCIES_RESOURCE, projectName);

        assertThat(project.getElementsByTagNameNS(MAVEN_NAMESPACE, "dependencies").getLength()).isZero();
        assertThat(dependencyReport)
                .contains("Transitive dependencies of this project")
                .contains("maven pom organized by organization");
        assertThat(significantDependencyReportLines(dependencyReport)).containsExactly(projectName);
    }

    private static String readResource(String resourceName) throws IOException {
        try (InputStream inputStream = resourceStream(resourceName)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String readResourceContaining(String resourceName, String content) throws IOException {
        Enumeration<URL> resources = Weld_build_configTest.class.getClassLoader().getResources(resourceName);
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            String resourceContent = readUrl(resource);
            if (resourceContent.contains(content)) {
                return resourceContent;
            }
        }
        throw new AssertionError("No resource named " + resourceName + " containing " + content);
    }

    private static String readUrl(URL resource) throws IOException {
        try (InputStream inputStream = resource.openStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static InputStream resourceStream(String resourceName) {
        InputStream inputStream = Weld_build_configTest.class.getClassLoader().getResourceAsStream(resourceName);
        assertThat(inputStream).as(resourceName).isNotNull();
        return inputStream;
    }

    private static Document parseXml(String xml, boolean namespaceAware)
            throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(namespaceAware);
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    private static Manifest manifestWithSpecificationTitle(String specificationTitle) throws IOException {
        Enumeration<URL> resources = Weld_build_configTest.class.getClassLoader().getResources(MANIFEST_RESOURCE);
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            try (InputStream inputStream = resource.openStream()) {
                Manifest manifest = new Manifest(inputStream);
                if (specificationTitle.equals(manifest.getMainAttributes().getValue("Specification-Title"))) {
                    return manifest;
                }
            }
        }
        throw new AssertionError("No manifest with specification title " + specificationTitle);
    }

    private static Element moduleNamed(Element parent, String name) {
        return directChildModules(parent).stream()
                .filter(module -> name.equals(module.getAttribute("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No direct module named " + name));
    }

    private static List<Element> directChildModules(Element parent) {
        List<Element> modules = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element && "module".equals(((Element) child).getTagName())) {
                modules.add((Element) child);
            }
        }
        return modules;
    }

    private static List<String> moduleNames(List<Element> modules) {
        List<String> names = new ArrayList<>();
        for (Element module : modules) {
            names.add(module.getAttribute("name"));
        }
        return names;
    }

    private static String propertyValue(Element module, String propertyName) {
        NodeList children = module.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                Element property = (Element) child;
                if ("property".equals(property.getTagName()) && propertyName.equals(property.getAttribute("name"))) {
                    return property.getAttribute("value");
                }
            }
        }
        throw new AssertionError("No property named " + propertyName + " in " + module.getAttribute("name"));
    }

    private static List<String> significantDependencyReportLines(String dependencyReport) {
        List<String> lines = new ArrayList<>();
        String[] reportLines = dependencyReport.split("\\R");
        for (String line : reportLines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("//")) {
                lines.add(trimmed);
            }
        }
        return lines;
    }

    private static Element directChildElement(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element && localName.equals(((Element) child).getLocalName())) {
                return (Element) child;
            }
        }
        throw new AssertionError("No direct child element named " + localName);
    }

    private static String directChildText(Element parent, String localName) {
        return directChildElement(parent, localName).getTextContent().trim();
    }
}
