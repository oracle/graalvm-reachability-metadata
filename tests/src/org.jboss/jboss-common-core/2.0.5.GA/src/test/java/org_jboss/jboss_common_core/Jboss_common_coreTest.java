/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss.jboss_common_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.beans.PropertyEditor;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.jboss.util.Base64;
import org.jboss.util.Counter;
import org.jboss.util.LongCounter;
import org.jboss.util.LRUCachePolicy;
import org.jboss.util.Objects;
import org.jboss.util.StringPropertyReplacer;
import org.jboss.util.Strings;
import org.jboss.util.collection.CompoundKey;
import org.jboss.util.collection.Iterators;
import org.jboss.util.collection.LazyList;
import org.jboss.util.collection.LazyMap;
import org.jboss.util.collection.ListSet;
import org.jboss.util.file.FilePrefixFilter;
import org.jboss.util.file.FileSuffixFilter;
import org.jboss.util.file.FilenamePrefixFilter;
import org.jboss.util.file.FilenameSuffixFilter;
import org.jboss.util.file.Files;
import org.jboss.util.graph.Edge;
import org.jboss.util.graph.Graph;
import org.jboss.util.graph.Vertex;
import org.jboss.util.graph.Visitor;
import org.jboss.util.property.PropertyAdapter;
import org.jboss.util.property.PropertyEvent;
import org.jboss.util.property.PropertyMap;
import org.jboss.util.propertyeditor.BooleanEditor;
import org.jboss.util.propertyeditor.IntArrayEditor;
import org.jboss.util.propertyeditor.IntegerEditor;
import org.jboss.util.propertyeditor.PropertyEditors;
import org.jboss.util.propertyeditor.StringArrayEditor;
import org.jboss.util.state.IllegalTransitionException;
import org.jboss.util.state.State;
import org.jboss.util.state.StateMachine;
import org.jboss.util.state.Transition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Jboss_common_coreTest {
    @Test
    void base64EncodesWholeArraysAndSlicesWithoutLineBreaks() {
        byte[] original = "alpha beta gamma delta".getBytes(StandardCharsets.UTF_8);

        String encoded = Base64.encodeBytes(original, Base64.DONT_BREAK_LINES);
        byte[] decoded = Base64.decode(encoded);
        String encodedSlice = Base64.encodeBytes(original, 11, 5, Base64.DONT_BREAK_LINES);

        assertThat(decoded).isEqualTo(original);
        assertThat(new String(Base64.decode(encodedSlice), StandardCharsets.UTF_8)).isEqualTo("gamma");
        assertThat(encoded).doesNotContain("\n");
    }

    @Test
    void stringUtilitiesHandleJoiningCountingIdentifiersAndWhitespace() {
        assertThat(Strings.join(new Object[] {"alpha", 7, "omega"}, "|"))
                .isEqualTo("alpha|7|omega");
        assertThat(Strings.join(new Object[] {"left", "right"}, "[", ",", "]"))
                .isEqualTo("[left,right]");
        assertThat(Strings.count("bananana", "ana")).isEqualTo(3);
        assertThat(Strings.pad("ab", 3)).isEqualTo("ababab");
        assertThat(Strings.compare("same", "same")).isTrue();
        assertThat(Strings.compare(null, "value")).isFalse();
        assertThat(Strings.capitalize("jboss")).isEqualTo("Jboss");
        assertThat(Strings.removeWhiteSpace(" a\n b\t c ")).isEqualTo("abc");
        assertThat(Strings.isJavaKeyword("class")).isTrue();
        assertThat(Strings.isValidJavaIdentifier("validName1")).isTrue();
        assertThat(Strings.isValidJavaIdentifier("1invalid")).isFalse();
    }

    @Test
    void stringPropertyReplacerResolvesProvidedPropertiesDefaultsAndSeparators() {
        Properties properties = new Properties();
        properties.setProperty("base", "opt");
        properties.setProperty("name", "jboss");

        String replaced = StringPropertyReplacer.replaceProperties(
                "${base}${/}${name}${:}${missing:fallback}", properties);

        assertThat(replaced).isEqualTo("opt" + File.separator + "jboss" + File.pathSeparator + "fallback");
        assertThat(StringPropertyReplacer.replaceProperties("no placeholders", properties))
                .isEqualTo("no placeholders");
    }

    @Test
    void lazyCollectionsBehaveLikeStandardCollectionsAfterMutation() {
        LazyList list = new LazyList();
        assertThat(list).isEmpty();

        list.add("first");
        list.add("third");
        list.add(1, "second");
        Object previous = list.set(2, "last");

        assertThat(previous).isEqualTo("third");
        assertThat(list).containsExactly("first", "second", "last");
        assertThat(list.remove(1)).isEqualTo("second");
        assertThat(list).containsExactly("first", "last");

        LazyMap map = new LazyMap();
        assertThat(map).isEmpty();
        assertThat(map.put("one", 1)).isNull();
        map.putAll(Collections.singletonMap("two", 2));

        assertThat(map).containsEntry("one", 1).containsEntry("two", 2);
        assertThat(map.remove("one")).isEqualTo(1);
        assertThat(map.keySet()).containsExactly("two");
    }

    @Test
    void listSetPreservesInsertionOrderAndRejectsDuplicates() {
        ListSet set = new ListSet();

        assertThat(set.add("one")).isTrue();
        assertThat(set.add("two")).isTrue();
        assertThat(set.add("one")).isFalse();

        assertThat(set).containsExactly("one", "two");
        assertThat(set.getList()).containsExactly("one", "two");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new ListSet(Arrays.asList("dup", "dup")));
    }

    @Test
    void iteratorAdaptersBridgeIteratorsAndEnumerations() {
        java.util.Iterator<?> union = Iterators.union(new java.util.Iterator[] {
                Arrays.asList("a", "b").iterator(),
                Collections.singletonList("c").iterator()
        });

        assertThat(Iterators.toString(union, ",")).isEqualTo("a,b,c");
        assertThat(Collections.list(Iterators.toEnumeration(Arrays.asList("x", "y").iterator())))
                .containsExactly("x", "y");
        java.util.Iterator<?> iterator = Iterators.forEnumeration(
                Collections.enumeration(Collections.singletonList("z")));
        List<Object> values = new ArrayList<>();
        iterator.forEachRemaining(values::add);
        assertThat(values).containsExactly("z");
    }

    @Test
    void compoundKeysAndArrayHelpersUseValueEquality() {
        CompoundKey first = new CompoundKey("group", "artifact", 5);
        CompoundKey sameValues = new CompoundKey(new Object[] {"group", "artifact", 5});
        CompoundKey different = new CompoundKey("group", "other", 5);

        Object[] nested = new Object[] {"same"};

        assertThat(first).isEqualTo(first);
        assertThat(first).isNotEqualTo(sameValues);
        assertThat(first).isNotEqualTo(different);
        assertThat(first.toString()).contains("group", "artifact", "5");
        assertThat(Objects.equals(new Object[] {nested}, new Object[] {nested})).isTrue();
        assertThat(Objects.equals(new Object[] {"same"}, new Object[] {"same"})).isFalse();
    }

    @Test
    void countersSupportDirectionalAndSynchronizedWrappers() {
        Counter counter = new Counter(10);
        assertThat(counter.increment()).isEqualTo(11);
        assertThat(counter.decrement()).isEqualTo(10);
        counter.reset();
        assertThat(counter.getCount()).isZero();

        Counter incrementOnly = Counter.makeDirectional(counter, true);
        Counter decrementOnly = Counter.makeDirectional(counter, false);
        assertThat(incrementOnly.increment()).isEqualTo(1);
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(incrementOnly::decrement);
        assertThat(decrementOnly.decrement()).isZero();
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(decrementOnly::increment);

        LongCounter synchronizedLongCounter = LongCounter.makeSynchronized(new LongCounter(4L));
        assertThat(synchronizedLongCounter.increment()).isEqualTo(5L);
        assertThat(synchronizedLongCounter.decrement()).isEqualTo(4L);
    }

    @Test
    void lruCachePolicyEvictsLeastRecentlyUsedEntriesAndSupportsExplicitRemoval() throws Exception {
        LRUCachePolicy cache = new LRUCachePolicy(2, 3);
        cache.create();
        cache.start();
        try {
            cache.insert("one", "first");
            cache.insert("two", "second");
            cache.insert("three", "third");

            assertThat(cache.size()).isEqualTo(3);
            assertThat(cache.get("one")).isEqualTo("first");
            assertThat(cache.peek("two")).isEqualTo("second");

            cache.insert("four", "fourth");
            assertThat(cache.peek("two")).isNull();
            assertThat(cache.peek("one")).isEqualTo("first");
            assertThat(cache.peek("three")).isEqualTo("third");
            assertThat(cache.peek("four")).isEqualTo("fourth");

            assertThat(cache.get("three")).isEqualTo("third");
            cache.insert("five", "fifth");
            assertThat(cache.peek("one")).isNull();
            assertThat(cache.peek("three")).isEqualTo("third");
            assertThat(cache.peek("five")).isEqualTo("fifth");

            cache.remove("three");
            assertThat(cache.peek("three")).isNull();
            assertThat(cache.size()).isEqualTo(2);
        } finally {
            cache.destroy();
        }
    }

    @Test
    void stateMachineFollowsConfiguredTransitionsAndRejectsUnknownActions() throws Exception {
        State created = new State("created");
        State running = new State("running");
        State stopped = new State("stopped");
        created.setData("initial data");
        created.addTransition(new Transition("start", running));
        running.addTransition(new Transition("stop", stopped));
        StateMachine stateMachine = new StateMachine(Set.of(created, running, stopped), created, "service lifecycle");

        assertThat(stateMachine.getDescription()).isEqualTo("service lifecycle");
        assertThat(stateMachine.getStartState()).isSameAs(created);
        assertThat(created.getData()).isEqualTo("initial data");
        assertThat(created.isAcceptState()).isFalse();
        assertThat(stopped.isAcceptState()).isTrue();
        assertThat(stateMachine.nextState("start")).isSameAs(running);
        assertThat(stateMachine.nextState("stop")).isSameAs(stopped);
        assertThat(stateMachine.reset()).isSameAs(created);
        assertThatExceptionOfType(IllegalTransitionException.class)
                .isThrownBy(() -> stateMachine.nextState("stop"))
                .withMessageContaining("No transition for action");
    }

    @Test
    void graphAddsEdgesFindsVerticesAndTraversesInDepthAndBreadthOrder() {
        Graph<String> graph = new Graph<>();
        Vertex<String> start = new Vertex<>("start", "A");
        Vertex<String> second = new Vertex<>("second", "B");
        Vertex<String> third = new Vertex<>("third", "C");
        Vertex<String> fourth = new Vertex<>("fourth", "D");
        graph.setRootVertex(start);
        graph.addVertex(second);
        graph.addVertex(third);
        graph.addVertex(fourth);

        assertThat(graph.addEdge(start, second, 2)).isTrue();
        assertThat(graph.addEdge(start, third, 3)).isTrue();
        assertThat(graph.addEdge(second, fourth, 4)).isTrue();
        assertThat(graph.addEdge(start, second, 2)).isFalse();

        Edge<String> startToSecond = start.findEdge(second);
        assertThat(graph.size()).isEqualTo(4);
        assertThat(graph.getRootVertex()).isSameAs(start);
        assertThat(graph.getEdges()).hasSize(3);
        assertThat(startToSecond.getCost()).isEqualTo(2);
        assertThat(second.getIncomingEdge(0)).isSameAs(startToSecond);
        assertThat(start.cost(fourth)).isEqualTo(Integer.MAX_VALUE);
        assertThat(graph.findVertexByName("third")).isSameAs(third);
        assertThat(graph.findVertexByData("D", String::compareTo)).isSameAs(fourth);

        List<String> depthFirst = new ArrayList<>();
        Visitor<String> depthFirstVisitor = (g, vertex) -> depthFirst.add(vertex.getName());
        graph.depthFirstSearch(start, depthFirstVisitor);
        assertThat(depthFirst).containsExactly("start", "second", "fourth", "third");

        graph.clearMark();
        List<String> breadthFirst = new ArrayList<>();
        Visitor<String> breadthFirstVisitor = (g, vertex) -> breadthFirst.add(vertex.getName());
        graph.breadthFirstSearch(start, breadthFirstVisitor);
        assertThat(breadthFirst).containsExactly("start", "second", "third", "fourth");
    }

    @Test
    void propertyMapStoresArraysGroupsAndNotifiesListeners() {
        PropertyMap properties = new PropertyMap();
        List<String> events = new ArrayList<>();
        properties.addPropertyListener(new PropertyAdapter() {
            @Override
            public void propertyAdded(PropertyEvent event) {
                events.add("added:" + event.getPropertyName() + "=" + event.getPropertyValue());
            }

            @Override
            public void propertyChanged(PropertyEvent event) {
                events.add("changed:" + event.getPropertyName() + "=" + event.getPropertyValue());
            }

            @Override
            public void propertyRemoved(PropertyEvent event) {
                events.add("removed:" + event.getPropertyName() + "=" + event.getPropertyValue());
            }
        });

        properties.setProperty("server.host", "localhost");
        properties.setProperty("server.port", "8080");
        properties.setProperty("items.0", "alpha");
        properties.setProperty("items.1", "beta");
        properties.setProperty("server.port", "9090");
        String removed = properties.removeProperty("server.host");

        assertThat(removed).isEqualTo("localhost");
        assertThat(properties.getArrayProperty("items")).containsExactly("alpha", "beta");
        assertThat(properties.containsProperty("server.port")).isTrue();
        assertThat(properties.getPropertyGroup("server").getBaseName()).isEqualTo("server");
        assertThat(events).contains(
                "added:server.host=localhost",
                "added:server.port=8080",
                "changed:server.port=9090",
                "removed:server.host=localhost");
    }

    @Test
    void propertyEditorsConvertPrimitiveArraysAndNullLikeText() throws Exception {
        BooleanEditor booleanEditor = new BooleanEditor();
        booleanEditor.setAsText("true");
        assertThat(booleanEditor.getValue()).isEqualTo(Boolean.TRUE);
        assertThat(booleanEditor.getTags()).containsExactly("true", "false");

        IntegerEditor integerEditor = new IntegerEditor();
        integerEditor.setAsText("0x10");
        assertThat(integerEditor.getValue()).isEqualTo(16);

        IntArrayEditor intArrayEditor = new IntArrayEditor();
        intArrayEditor.setAsText("1,0x2,3");
        assertThat((int[]) intArrayEditor.getValue()).containsExactly(1, 2, 3);
        assertThat(intArrayEditor.getAsText()).isEqualTo("1,2,3");

        StringArrayEditor stringArrayEditor = new StringArrayEditor();
        stringArrayEditor.setAsText("alpha,\\,,omega");
        assertThat((String[]) stringArrayEditor.getValue()).containsExactly("alpha", ",", "omega");

        PropertyEditor registeredStringArrayEditor = PropertyEditors.getEditor(String[].class);
        registeredStringArrayEditor.setAsText("left,right");
        assertThat((String[]) registeredStringArrayEditor.getValue()).containsExactly("left", "right");
        assertThat(PropertyEditors.convertValue("17", "int")).isEqualTo(17);
        assertThat(PropertyEditors.isNull(" null ")).isTrue();
    }

    @Test
    void propertyMapLoadsPlainMapsWithOptionalPrefixes() throws Exception {
        PropertyMap properties = new PropertyMap();
        properties.load(Map.of("host", "127.0.0.1", "port", 8080, "datasource.name", "primary"));

        assertThat(properties.getProperty("host")).isEqualTo("127.0.0.1");
        assertThat(properties.getProperty("port")).isEqualTo("8080");
        assertThat(properties.getProperty("datasource.name")).isEqualTo("primary");
        List<Object> names = new ArrayList<>();
        properties.names().forEachRemaining(names::add);
        assertThat(names).contains("host", "port", "datasource.name");
    }

    @Test
    void fileUtilitiesCopyDeleteEncodeNamesAndFilterByName(@TempDir File tempDir) throws Exception {
        File source = new File(tempDir, "config-source.txt");
        File target = new File(tempDir, "config-copy.LOG");
        byte[] payload = "copied configuration".getBytes(StandardCharsets.UTF_8);
        try (OutputStream outputStream = new FileOutputStream(source)) {
            outputStream.write(payload);
        }

        Files.copy(source, target, 4);

        try (InputStream inputStream = new FileInputStream(target)) {
            assertThat(inputStream.readAllBytes()).isEqualTo(payload);
        }
        String unsafeName = "domain:server/name@node";
        String encodedName = Files.encodeFileName(unsafeName);
        assertThat(encodedName).isNotEqualTo(unsafeName).doesNotContain(":", "/");
        assertThat(Files.decodeFileName(encodedName)).isEqualTo(unsafeName);

        assertThat(new FilePrefixFilter("CONFIG", true).accept(target)).isTrue();
        assertThat(new FilePrefixFilter("CONFIG", false).accept(target)).isFalse();
        assertThat(new FileSuffixFilter(new String[] {".txt", ".log"}, true).accept(target)).isTrue();
        assertThat(new FilenamePrefixFilter("config").accept(tempDir, target.getName())).isTrue();
        assertThat(new FilenameSuffixFilter(".log", true).accept(tempDir, target.getName())).isTrue();

        File nestedDir = new File(tempDir, "nested");
        assertThat(nestedDir.mkdir()).isTrue();
        File nestedFile = new File(nestedDir, "child.txt");
        try (OutputStream outputStream = new FileOutputStream(nestedFile)) {
            outputStream.write(payload);
        }

        assertThat(Files.delete(nestedDir)).isTrue();
        assertThat(nestedDir.exists()).isFalse();
    }
}
