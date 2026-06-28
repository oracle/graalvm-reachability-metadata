/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss.jboss_common_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.beans.PropertyEditor;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.xml.namespace.QName;

import org.jboss.util.Base64;
import org.jboss.util.Classes;
import org.jboss.util.LRUCachePolicy;
import org.jboss.util.Primitives;
import org.jboss.util.StringPropertyReplacer;
import org.jboss.util.Strings;
import org.jboss.util.collection.CompoundKey;
import org.jboss.util.collection.LazyList;
import org.jboss.util.collection.ListSet;
import org.jboss.util.collection.SoftValueHashMap;
import org.jboss.util.file.Files;
import org.jboss.util.graph.Edge;
import org.jboss.util.graph.DFSVisitor;
import org.jboss.util.graph.Graph;
import org.jboss.util.graph.Vertex;
import org.jboss.util.graph.Visitor;
import org.jboss.util.id.UID;
import org.jboss.util.property.BoundPropertyAdapter;
import org.jboss.util.property.PropertyAdapter;
import org.jboss.util.property.PropertyEvent;
import org.jboss.util.property.PropertyGroup;
import org.jboss.util.property.PropertyMap;
import org.jboss.util.propertyeditor.DocumentEditor;
import org.jboss.util.propertyeditor.ElementEditor;
import org.jboss.util.propertyeditor.PropertiesEditor;
import org.jboss.util.propertyeditor.PropertyEditors;
import org.jboss.util.state.IllegalTransitionException;
import org.jboss.util.state.State;
import org.jboss.util.state.StateMachine;
import org.jboss.util.state.Transition;
import org.jboss.util.state.xml.StateMachineParser;
import org.jboss.util.xml.DOMUtils;
import org.jboss.util.xml.DOMWriter;
import org.jboss.util.xml.XmlHelper;
import org.jboss.util.xml.catalog.helpers.PublicId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class Jboss_common_coreTest {
    @Test
    void stringUtilitiesSubstituteParseNormalizeAndBuildUris(@TempDir Path tempDir) throws Exception {
        Map<String, String> tokens = new HashMap<String, String>();
        tokens.put("module", "common");
        tokens.put("feature", "core");

        assertThat(Strings.subst("${module}:${feature}:${missing}", tokens, "${", "}"))
                .isEqualTo("common:core:");
        assertThat(Strings.subst("Hello %0, %1", new String[] {"JBoss", "utilities"}))
                .isEqualTo("Hello JBoss, utilities");
        assertThat(Strings.split("a,b,c,d", ",", 3)).containsExactly("a", "b", "c,d");
        assertThat(Strings.join(new Object[] {"a", "b", "c"}, "[", "|", "]"))
                .isEqualTo("[a|b|c]");
        assertThat(Strings.count("banana", "ana")).isEqualTo(2);
        assertThat(Strings.pad("ab", 3)).isEqualTo("ababab");
        assertThat(Strings.nthIndexOf("one/two/three", "/", 2)).isEqualTo(7);
        assertThat(Strings.capitalize("jboss")).isEqualTo("Jboss");
        assertThat(Strings.trim(new String[] {" left", "right ", " both "}))
                .containsExactly("left", "right", "both");
        assertThat(Strings.isJavaKeyword("class")).isTrue();
        assertThat(Strings.isEjbQlIdentifier("select")).isTrue();
        assertThat(Strings.isValidJavaIdentifier("_validName42")).isTrue();
        assertThat(Strings.isValidJavaIdentifier("9invalid")).isFalse();
        assertThat(Strings.removeWhiteSpace(" a\t b\n c ")).isEqualTo("abc");
        assertThat(Strings.parseTimePeriod("2sec")).isEqualTo(2_000L);
        assertThat(Strings.parsePositiveTimePeriod("3min")).isEqualTo(180_000L);

        File base = tempDir.toFile();
        URI uri = Strings.toURI("file:nested/../artifact.txt", base.getAbsolutePath());
        URL url = Strings.toURL("nested/../artifact.txt", base.getAbsolutePath());
        assertThat(new File(uri).getName()).isEqualTo("artifact.txt");
        assertThat(url.getProtocol()).isEqualTo("file");
        assertThat(new File(url.toURI()).getCanonicalFile().getParentFile()).isEqualTo(base.getCanonicalFile());
    }

    @Test
    void propertyReplacementResolvesProvidedValuesDefaultsCompositeKeysAndSeparators() {
        Properties properties = new Properties();
        properties.setProperty("primary", "alpha");
        properties.setProperty("secondary", "beta");
        properties.setProperty("dir", "server");

        String replaced = StringPropertyReplacer.replaceProperties(
                "${primary}:${missing:default}:${absent,secondary}:${dir}${/}conf${:}lib",
                properties);

        assertThat(replaced).isEqualTo(
                "alpha:default:beta:server" + File.separator + "conf" + File.pathSeparator + "lib");
    }

    @Test
    void propertyEditorsConvertScalarsArraysPropertiesAndXmlDocuments() throws Exception {
        PropertyEditor integerEditor = PropertyEditors.getEditor(Integer.class);
        integerEditor.setAsText("42");
        assertThat(integerEditor.getValue()).isEqualTo(42);

        PropertyEditor intArrayEditor = PropertyEditors.getEditor(int[].class);
        intArrayEditor.setAsText("1,0x10\n3");
        assertThat((int[]) intArrayEditor.getValue()).containsExactly(1, 16, 3);
        assertThat(intArrayEditor.getAsText()).isEqualTo("1,16,3");

        PropertyEditor stringArrayEditor = PropertyEditors.getEditor(String[].class);
        stringArrayEditor.setAsText("alpha,beta\\,gamma\rdelta");
        assertThat((String[]) stringArrayEditor.getValue()).containsExactly("alpha", "beta,gamma", "delta");

        PropertyEditor classArrayEditor = PropertyEditors.getEditor(Class[].class);
        classArrayEditor.setAsText("java.lang.String, java.lang.Integer");
        assertThat((Class<?>[]) classArrayEditor.getValue()).containsExactly(String.class, Integer.class);

        PropertiesEditor propertiesEditor = new PropertiesEditor();
        propertiesEditor.setAsText("root=/opt/jboss\nconf=${root}/conf\nmode=standalone");
        Properties converted = (Properties) propertiesEditor.getValue();
        assertThat(converted.getProperty("conf")).isEqualTo("/opt/jboss/conf");
        assertThat(propertiesEditor.getAsText()).contains("mode=standalone");

        DocumentEditor documentEditor = new DocumentEditor();
        documentEditor.setAsText("<root><child enabled='true'>text</child></root>");
        Document document = (Document) documentEditor.getValue();
        assertThat(document.getDocumentElement().getTagName()).isEqualTo("root");
        assertThat(documentEditor.getAsText()).contains("<child enabled='true'>text</child>");

        ElementEditor elementEditor = new ElementEditor();
        elementEditor.setAsText("<element id='one'/>");
        assertThat(((Element) elementEditor.getValue()).getAttribute("id")).isEqualTo("one");

        assertThat(PropertyEditors.convertValue("42", "java.lang.Integer")).isEqualTo(42);
    }

    @Test
    void graphSupportsWeightedEdgesTraversalsLookupSpanningTreesAndCycleDetection() {
        Graph<String> graph = new Graph<String>();
        Vertex<String> root = new Vertex<String>("root", "r");
        Vertex<String> left = new Vertex<String>("left", "l");
        Vertex<String> right = new Vertex<String>("right", "r2");
        Vertex<String> leaf = new Vertex<String>("leaf", "z");

        graph.setRootVertex(root);
        graph.addVertex(left);
        graph.addVertex(right);
        graph.addVertex(leaf);
        assertThat(graph.size()).isEqualTo(4);
        assertThat(graph.addEdge(root, left, 2)).isTrue();
        assertThat(graph.addEdge(root, right, 3)).isTrue();
        assertThat(graph.addEdge(left, leaf, 5)).isTrue();
        assertThat(graph.addEdge(right, leaf, 7)).isTrue();
        assertThat(graph.addEdge(root, left, 2)).isFalse();

        assertThat(root.cost(left)).isEqualTo(2);
        assertThat(root.cost(leaf)).isEqualTo(Integer.MAX_VALUE);
        assertThat(graph.findVertexByName("right")).isSameAs(right);
        assertThat(graph.findVertexByData("z", String::compareTo)).isSameAs(leaf);

        List<String> breadthFirst = new ArrayList<String>();
        graph.breadthFirstSearch(root, (Visitor<String>) (g, v) -> breadthFirst.add(v.getName()));
        assertThat(breadthFirst).containsExactly("root", "left", "right", "leaf");

        graph.clearMark();
        List<String> depthFirst = new ArrayList<String>();
        graph.depthFirstSearch(root, (Visitor<String>) (g, v) -> depthFirst.add(v.getName()));
        assertThat(depthFirst).containsExactly("root", "left", "leaf", "right");

        graph.clearMark();
        List<String> spanningEdges = new ArrayList<String>();
        graph.dfsSpanningTree(root, new DFSVisitor<String>() {
            public void visit(Graph<String> g, Vertex<String> v) {
                spanningEdges.add(v.getName());
            }

            public void visit(Graph<String> g, Vertex<String> v, Edge<String> e) {
                spanningEdges.add(v.getName() + "->" + e.getTo().getName());
            }
        });
        assertThat(spanningEdges).contains("root", "root->left", "left->leaf", "root->right");
        assertThat(graph.getEdges()).filteredOn(Edge::isMarked).hasSize(3);
        graph.clearEdges();
        assertThat(graph.getEdges()).noneMatch(Edge::isMarked);

        graph.addEdge(leaf, root, 11);
        assertThat(graph.findCycles()).extracting(edge -> edge.getFrom().getName() + "->" + edge.getTo().getName())
                .contains("leaf->root");
    }

    @Test
    void stateMachineTransitionsManuallyAndFromXml(@TempDir Path tempDir) throws Exception {
        State open = new State("open");
        State closed = new State("closed");
        open.setData("accepting work");
        open.addTransition(new Transition("close", closed));
        closed.addTransition(new Transition("open", open));
        StateMachine manualMachine = new StateMachine(new HashSet<State>(Arrays.asList(open, closed)), open,
                "manual lifecycle");

        assertThat(manualMachine.getDescription()).isEqualTo("manual lifecycle");
        assertThat(manualMachine.getCurrentState()).isSameAs(open);
        assertThat(manualMachine.nextState("close")).isSameAs(closed);
        assertThat(manualMachine.nextState("open")).isSameAs(open);
        assertThat(manualMachine.reset()).isSameAs(open);
        assertThat(open.getData()).isEqualTo("accepting work");
        assertThatThrownBy(() -> manualMachine.nextState("missing"))
                .isInstanceOf(IllegalTransitionException.class)
                .hasMessageContaining("No transition for action");

        File stateFile = tempDir.resolve("state-machine.xml").toFile();
        writeText(stateFile, """
                <state-machine description="xml lifecycle">
                  <state name="created" isStartState="true">
                    <transition name="start" target="running" />
                  </state>
                  <state name="running">
                    <transition name="stop" target="stopped" />
                  </state>
                  <state name="stopped" />
                </state-machine>
                """);

        StateMachine parsedMachine = new StateMachineParser().parse(stateFile.toURI().toURL());
        assertThat(parsedMachine.getDescription()).isEqualTo("xml lifecycle");
        assertThat(parsedMachine.getStartState().getName()).isEqualTo("created");
        assertThat(parsedMachine.nextState("start").getName()).isEqualTo("running");
        assertThat(parsedMachine.nextState("stop").isAcceptState()).isTrue();
    }

    @Test
    void xmlUtilitiesNavigateAttributesTextNamespacesAndPublicIdentifiers() throws Exception {
        Element root = DOMUtils.parse("""
                <p:root xmlns:p="urn:test" count="2">
                  <p:child enabled="true" ref="p:item">text &amp; more</p:child>
                  <p:child enabled="false">other</p:child>
                </p:root>
                """);

        assertThat(DOMUtils.getElementQName(root)).isEqualTo(new QName("urn:test", "root", "p"));
        assertThat(DOMUtils.getAttributeValueAsInteger(root, "count")).isEqualTo(2);
        assertThat(DOMUtils.hasChildElements(root)).isTrue();

        Element firstChild = DOMUtils.getFirstChildElement(root, new QName("urn:test", "child"));
        assertThat(DOMUtils.getAttributeValueAsBoolean(firstChild, "enabled")).isTrue();
        assertThat(DOMUtils.getAttributeValueAsQName(firstChild, "ref"))
                .isEqualTo(new QName("urn:test", "item", "p"));
        assertThat(DOMUtils.getTextContent(firstChild)).isEqualTo("text & more");
        assertThat(XmlHelper.getOptionalChildContent(firstChild, "missing")).isNull();
        assertThat(XmlHelper.getOptionalChildBooleanContent(firstChild, "missing")).isFalse();

        Element copy = DOMUtils.createElement("copy", "p", "urn:test");
        DOMUtils.copyAttributes(copy, firstChild);
        copy.appendChild(DOMUtils.createTextNode("copied"));
        assertThat(copy.getAttribute("enabled")).isEqualTo("true");
        assertThat(DOMWriter.printNode(copy, false)).contains("<p:copy").contains("copied</p:copy>");
        assertThat(DOMWriter.normalize("<tag attr=\"x\">&\n", true))
                .isEqualTo("&lt;tag attr=&quot;x&quot;&gt;&amp;&#10;");

        String publicId = "JBoss  Common   Core";
        String urn = PublicId.encodeURN(publicId);
        assertThat(urn).startsWith("urn:publicid:");
        assertThat(PublicId.decodeURN(urn)).isEqualTo(PublicId.normalize(publicId));
    }

    @Test
    void fileHelpersAndBase64CopyEncodeDecodeAndPreserveBytes(@TempDir Path tempDir) throws Exception {
        byte[] payload = "JBoss common core utilities".getBytes(StandardCharsets.UTF_8);
        String encoded = Base64.encodeBytes(payload, Base64.DONT_BREAK_LINES);
        assertThat(Base64.decode(encoded)).containsExactly(payload);

        String unsafeName = "module:common/core?name";
        String safeName = Files.encodeFileName(unsafeName);
        assertThat(safeName).doesNotContain(":", "/", "?");
        assertThat(Files.decodeFileName(safeName)).isEqualTo(unsafeName);

        File source = tempDir.resolve("source.txt").toFile();
        File target = tempDir.resolve("target.txt").toFile();
        writeBytes(source, payload);
        Files.copy(source, target);
        assertThat(readBytes(target)).containsExactly(payload);

        File copiedFromUrl = tempDir.resolve("url-copy/target.txt").toFile();
        Files.copy(source.toURI().toURL(), copiedFromUrl);
        assertThat(readBytes(copiedFromUrl)).containsExactly(payload);
    }

    @Test
    void classPrimitiveIdentifierAndCollectionUtilitiesExposeExpectedPublicBehavior() throws Exception {
        assertThat(Classes.stripPackageName(String.class)).isEqualTo("String");
        assertThat(Classes.getPackageName(String.class)).isEqualTo("java.lang");
        assertThat(Classes.getPrimitiveTypeForName("int")).isEqualTo(Integer.TYPE);
        assertThat(Classes.getPrimitiveWrapper(Integer.TYPE)).isEqualTo(Integer.class);
        assertThat(Classes.getPrimitive(Integer.class)).isEqualTo(Integer.TYPE);
        assertThat(Classes.isPrimitiveWrapper(Long.class)).isTrue();
        assertThat(Classes.isPrimitive("boolean")).isTrue();
        assertThat(Classes.loadClass("I")).isEqualTo(Integer.TYPE);
        assertThat(Classes.loadClass("[I")).isEqualTo(int[].class);
        assertThat(Classes.loadClass("[Ljava.lang.String;")).isEqualTo(String[].class);

        assertThat(Primitives.valueOf(true)).isSameAs(Boolean.TRUE);
        assertThat(Primitives.equals(Double.NaN, Double.NaN)).isTrue();
        assertThat(Primitives.equals(new byte[] {1, 2, 3}, 1, new byte[] {0, 2, 3, 4}, 1, 2)).isTrue();

        UID first = new UID();
        UID second = new UID();
        UID firstClone = (UID) first.clone();
        assertThat(first).isNotEqualTo(second);
        assertThat(firstClone).isEqualTo(first).isNotSameAs(first);
        assertThat(UID.asString()).contains("-");

        ListSet listSet = new ListSet();
        assertThat(listSet.add("alpha")).isTrue();
        assertThat(listSet.add("beta")).isTrue();
        assertThat(listSet.add("alpha")).isFalse();
        assertThat(listSet).containsExactly("alpha", "beta");

        LazyList lazyList = new LazyList();
        assertThat(lazyList).isEmpty();
        lazyList.add("first");
        lazyList.add("second");
        lazyList.add(1, "middle");
        assertThat(lazyList).containsExactly("first", "middle", "second");
        assertThat(lazyList.set(1, "updated")).isEqualTo("middle");
        assertThat(lazyList.subList(0, 2)).containsExactly("first", "updated");

        CompoundKey key = new CompoundKey("group", "artifact", "classifier");
        CompoundKey clonedKey = (CompoundKey) key.clone();
        assertThat(clonedKey).isEqualTo(key).hasSameHashCodeAs(key);
        assertThat(key.toString()).contains("[group,artifact,classifier]");

        SoftValueHashMap map = new SoftValueHashMap();
        map.put(key, "metadata");
        assertThat(map.containsKey(key)).isTrue();
        assertThat(map.get(key)).isEqualTo("metadata");
        assertThat(map.size()).isEqualTo(1);
        assertThat(map.entrySet()).hasSize(1);
        map.clear();
        assertThat(map).isEmpty();
    }

    @Test
    void propertyMapPublishesEventsAndProvidesIndexedPropertyViews() {
        PropertyMap map = new PropertyMap();
        List<String> allEvents = new ArrayList<String>();
        PropertyAdapter allListener = new PropertyAdapter() {
            @Override
            public void propertyAdded(PropertyEvent event) {
                allEvents.add("added:" + event.getPropertyName() + "=" + event.getPropertyValue());
            }

            @Override
            public void propertyChanged(PropertyEvent event) {
                allEvents.add("changed:" + event.getPropertyName() + "=" + event.getPropertyValue());
            }

            @Override
            public void propertyRemoved(PropertyEvent event) {
                allEvents.add("removed:" + event.getPropertyName() + "=" + event.getPropertyValue());
            }
        };
        List<String> boundEvents = new ArrayList<String>();
        BoundPropertyAdapter boundListener = new BoundPropertyAdapter() {
            @Override
            public String getPropertyName() {
                return "server.port";
            }

            @Override
            public void propertyBound(PropertyMap propertyMap) {
                boundEvents.add("bound");
            }

            @Override
            public void propertyAdded(PropertyEvent event) {
                boundEvents.add("added:" + event.getPropertyValue());
            }

            @Override
            public void propertyChanged(PropertyEvent event) {
                boundEvents.add("changed:" + event.getPropertyValue());
            }

            @Override
            public void propertyRemoved(PropertyEvent event) {
                boundEvents.add("removed:" + event.getPropertyValue());
            }

            @Override
            public void propertyUnbound(PropertyMap propertyMap) {
                boundEvents.add("unbound");
            }
        };

        map.addPropertyListener(allListener);
        map.addPropertyListener(boundListener);
        map.setProperty("server.host", "localhost");
        map.setProperty("server.port", "8080");
        map.setProperty("server.port", "8181");
        map.removeProperty("server.host");
        map.removeProperty("server.port");
        assertThat(map.removePropertyListener(boundListener)).isTrue();
        map.setProperty("server.port", "8282");
        assertThat(map.removePropertyListener(allListener)).isTrue();
        map.setProperty("server.host", "127.0.0.1");

        assertThat(allEvents).containsExactly(
                "added:server.host=localhost",
                "added:server.port=8080",
                "changed:server.port=8181",
                "removed:server.host=localhost",
                "removed:server.port=8181",
                "added:server.port=8282");
        assertThat(boundEvents).containsExactly("bound", "added:8080", "changed:8181", "removed:8181", "unbound");

        map.setProperty("endpoint.0", "public");
        map.setProperty("endpoint.1", "admin");
        map.setProperty("endpoint.3", "ignored after first gap");
        assertThat(map.getArrayProperty("endpoint")).containsExactly("public", "admin");
        assertThat(map.getArrayProperty("missing", new String[] {"fallback"})).containsExactly("fallback");

        map.setProperty("service.0.name", "http");
        map.setProperty("service.0.enabled", "true");
        PropertyGroup service = map.getPropertyGroup("service", 0);
        assertThat(service.getBaseName()).isEqualTo("service.0");
        assertThat(service.entrySet()).hasSize(2);
        assertThat(service.entrySet()).extracting(entry -> ((Map.Entry<?, ?>) entry).getKey())
                .containsExactlyInAnyOrder("service.0.name", "service.0.enabled");
    }

    @Test
    void lruCachePolicyRefreshesRequestedEntriesWithoutRefreshingPeekedEntries() throws Exception {
        LRUCachePolicy cache = new LRUCachePolicy(2, 3);
        cache.create();
        cache.start();

        cache.insert("one", "first");
        cache.insert("two", "second");
        cache.insert("three", "third");
        assertThat(cache.size()).isEqualTo(3);

        assertThat(cache.get("one")).isEqualTo("first");
        cache.insert("four", "fourth");
        assertThat(cache.peek("two")).isNull();
        assertThat(cache.peek("one")).isEqualTo("first");
        assertThat(cache.peek("three")).isEqualTo("third");
        assertThat(cache.peek("four")).isEqualTo("fourth");

        assertThat(cache.peek("three")).isEqualTo("third");
        cache.insert("five", "fifth");
        assertThat(cache.peek("three")).isNull();
        assertThat(cache.peek("one")).isEqualTo("first");
        assertThat(cache.peek("five")).isEqualTo("fifth");

        cache.remove("one");
        assertThat(cache.peek("one")).isNull();
        assertThat(cache.size()).isEqualTo(2);
        cache.flush();
        assertThat(cache.size()).isZero();
        cache.stop();
        cache.destroy();
    }

    private static void writeText(File file, String text) throws Exception {
        writeBytes(file, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeBytes(File file, byte[] bytes) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            assertThat(parent.mkdirs()).isTrue();
        }
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
        }
    }

    private static byte[] readBytes(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file)) {
            return input.readAllBytes();
        }
    }
}
