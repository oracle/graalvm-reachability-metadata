/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss.jboss_common_core;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readString;
import static java.nio.file.Files.writeString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.beans.PropertyEditor;
import java.io.File;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.jboss.util.Base64;
import org.jboss.util.Counter;
import org.jboss.util.HashCode;
import org.jboss.util.JBossStringBuilder;
import org.jboss.util.LRUCachePolicy;
import org.jboss.util.NestedException;
import org.jboss.util.Primitives;
import org.jboss.util.Semaphore;
import org.jboss.util.StringPropertyReplacer;
import org.jboss.util.Strings;
import org.jboss.util.Throwables;
import org.jboss.util.collection.CompoundKey;
import org.jboss.util.collection.Iterators;
import org.jboss.util.collection.LazyList;
import org.jboss.util.collection.ListSet;
import org.jboss.util.file.Files;
import org.jboss.util.graph.Edge;
import org.jboss.util.graph.Graph;
import org.jboss.util.graph.Vertex;
import org.jboss.util.graph.Visitor;
import org.jboss.util.property.PropertyEvent;
import org.jboss.util.property.PropertyListener;
import org.jboss.util.property.PropertyMap;
import org.jboss.util.propertyeditor.ByteArrayEditor;
import org.jboss.util.propertyeditor.IntegerEditor;
import org.jboss.util.propertyeditor.PropertyEditors;
import org.jboss.util.propertyeditor.StringArrayEditor;
import org.jboss.util.propertyeditor.URIEditor;
import org.jboss.util.propertyeditor.URLEditor;
import org.jboss.util.state.IllegalTransitionException;
import org.jboss.util.state.State;
import org.jboss.util.state.StateMachine;
import org.jboss.util.state.Transition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Jboss_common_coreTest {
    @TempDir
    Path tempDir;

    @Test
    void stringUtilitiesResolvePropertiesValidateIdentifiersAndConvertLocations() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("service.name", "inventory");
        properties.setProperty("service.port", "8080");

        String replaced = StringPropertyReplacer.replaceProperties(
                "${service.name}:${service.port}:${missing:fallback}:${/}", properties);

        assertThat(replaced).isEqualTo("inventory:8080:fallback:" + File.separator);
        assertThat(Strings.join(new Object[] {"alpha", "beta", "gamma"}, "|")).isEqualTo("alpha|beta|gamma");
        assertThat(Strings.split("one,two,three", ",")).containsExactly("one", "two", "three");
        assertThat(Strings.count("banana", 'a')).isEqualTo(3);
        assertThat(Strings.count("banana", "na")).isEqualTo(2);
        assertThat(Strings.pad("ab", 3)).isEqualTo("ababab");
        assertThat(Strings.capitalize("jboss")).isEqualTo("Jboss");
        assertThat(Strings.removeWhiteSpace(" a\tb\n c ")).isEqualTo("abc");
        assertThat(Strings.nthIndexOf("a-b-c-d", "-", 2)).isEqualTo(3);
        assertThat(Strings.isJavaKeyword("class")).isTrue();
        assertThat(Strings.isValidJavaIdentifier("validName_1")).isTrue();
        assertThat(Strings.isValidJavaIdentifier("1invalid")).isFalse();

        URI uri = Strings.toURI("http://example.com/resource?q=1");
        URL url = Strings.toURL("http://example.com/resource?q=1");
        assertThat(uri.getHost()).isEqualTo("example.com");
        assertThat(url.getProtocol()).isEqualTo("http");
    }

    @Test
    void base64PrimitivesHashCodeAndStringBuilderCoverCoreValueUtilities() {
        byte[] payload = "JBoss common core".getBytes(UTF_8);
        String encoded = Base64.encodeBytes(payload, Base64.DONT_BREAK_LINES);

        assertThat(Base64.decode(encoded)).isEqualTo(payload);
        assertThat(Primitives.equals(payload, payload.clone())).isTrue();
        assertThat(Primitives.equals(new byte[] {1, 2, 3}, 0, new byte[] {0, 1, 2, 3}, 1, 3)).isTrue();
        assertThat(Primitives.equals(0.25D, 0.25D)).isTrue();
        assertThat(Primitives.equals(0.5F, 0.5F)).isTrue();

        HashCode hashCode = new HashCode().add(true).add('x').add(42).add("value");
        HashCode sameHashCode = new HashCode().add(true).add('x').add(42).add("value");
        assertThat(hashCode).isEqualTo(sameHashCode);
        assertThat(hashCode.compareTo(sameHashCode)).isZero();
        assertThat(HashCode.generate(new Object[] {"value", 42}))
                .isEqualTo(HashCode.generate(new Object[] {"value", 42}));

        JBossStringBuilder builder = new JBossStringBuilder("core");
        builder.insert(0, "jboss-").append('-').append(205).replace(0, 5, "JBoss");
        assertThat(builder.toString()).isEqualTo("JBoss-core-205");
        assertThat(builder.indexOf("core")).isEqualTo(6);
        assertThat(builder.substring(0, 5)).isEqualTo("JBoss");
        assertThat(builder.reverse().toString()).isEqualTo("502-eroc-ssoBJ");
    }

    @Test
    void collectionsPreserveListSemanticsSetUniquenessAndIteratorAdapters() {
        LazyList lazyList = new LazyList();
        lazyList.add("alpha");
        lazyList.add("gamma");
        lazyList.add(1, "beta");
        assertThat(lazyList).containsExactly("alpha", "beta", "gamma");
        assertThat(lazyList.remove(1)).isEqualTo("beta");
        assertThat(lazyList).containsExactly("alpha", "gamma");

        ListSet set = new ListSet(new ArrayList());
        assertThat(set.add("one")).isTrue();
        assertThat(set.add("one")).isFalse();
        assertThat(set.add("two")).isTrue();
        assertThat(set.getList()).containsExactly("one", "two");
        assertThat(set.clone()).isEqualTo(set);

        CompoundKey first = new CompoundKey("tenant", "service", Integer.valueOf(7));
        CompoundKey second = new CompoundKey(new Object[] {"tenant", "service", Integer.valueOf(7)});
        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
        assertThat(first.clone()).isInstanceOf(CompoundKey.class).isNotSameAs(first);

        Enumeration<String> enumerationFromList = Collections.enumeration(Arrays.asList("a", "b"));
        Iterator iterator = Iterators.forEnumeration(enumerationFromList);
        assertThat(Iterators.toString(iterator, ",")).isEqualTo("a,b");

        Enumeration enumeration = Iterators.toEnumeration(Arrays.asList("x", "y").iterator());
        assertThat(Collections.list(enumeration)).containsExactly("x", "y");

        Iterator union = Iterators.union(new Iterator[] {
                Arrays.asList("left", "middle").iterator(),
                Collections.singletonList("right").iterator()});
        assertThat(iteratorContents(union)).containsExactly("left", "middle", "right");

        Iterator immutable = Iterators.makeImmutable(Collections.singletonList("fixed").iterator());
        assertThat(immutable.next()).isEqualTo("fixed");
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(immutable::remove);
    }

    @Test
    void propertyMapFiresEventsSupportsGroupsAndIndexedArrayProperties() {
        PropertyMap map = new PropertyMap();
        List<String> events = new ArrayList<>();
        PropertyListener listener = new RecordingPropertyListener(events);
        map.addPropertyListener(listener);

        assertThat(map.setProperty("server.host", "localhost")).isNull();
        assertThat(map.setProperty("server.host", "127.0.0.1")).isEqualTo("localhost");
        assertThat(map.setProperty("server.port", "8080")).isNull();
        assertThat(map.removeProperty("server.port")).isEqualTo("8080");

        map.setProperty("servers.0", "alpha");
        map.setProperty("servers.1", "beta");

        assertThat(map.getProperty("server.host")).isEqualTo("127.0.0.1");
        assertThat(map.containsProperty("server.host")).isTrue();
        assertThat(map.getArrayProperty("servers")).containsExactly("alpha", "beta");
        assertThat(map.getPropertyGroup("server").getBaseName()).isEqualTo("server");
        assertThat(iteratorContents(map.names())).contains("server.host", "servers.0", "servers.1");
        assertThat(events).containsExactly(
                "added:server.host=localhost",
                "changed:server.host=127.0.0.1",
                "added:server.port=8080",
                "removed:server.port=8080",
                "added:servers.0=alpha",
                "added:servers.1=beta");

        assertThat(map.removePropertyListener(listener)).isTrue();
    }

    @Test
    void graphUtilitiesTraverseFindEdgesAndDetectCycles() {
        Graph<String> graph = new Graph<>();
        Vertex<String> start = new Vertex<>("start", "A");
        Vertex<String> middle = new Vertex<>("middle", "B");
        Vertex<String> end = new Vertex<>("end", "C");

        assertThat(graph.addVertex(start)).isTrue();
        assertThat(graph.addVertex(middle)).isTrue();
        assertThat(graph.addVertex(end)).isTrue();
        graph.setRootVertex(start);
        assertThat(graph.addEdge(start, middle, 4)).isTrue();
        assertThat(graph.addEdge(middle, end, 6)).isTrue();
        assertThat(graph.addEdge(end, start, 8)).isTrue();

        assertThat(graph.size()).isEqualTo(3);
        assertThat(graph.getRootVertex()).isSameAs(start);
        assertThat(graph.findVertexByName("middle")).isSameAs(middle);
        assertThat(graph.findVertexByData("C", String::compareTo)).isSameAs(end);
        assertThat(start.hasEdge(middle)).isTrue();
        assertThat(start.cost(middle)).isEqualTo(4);
        assertThat(graph.getEdges()).hasSize(3);

        List<String> breadthFirstNames = new ArrayList<>();
        graph.breadthFirstSearch(start,
                (Visitor<String>) (visitedGraph, vertex) -> breadthFirstNames.add(vertex.getName()));
        assertThat(breadthFirstNames).containsExactly("start", "middle", "end");

        graph.clearMark();
        List<String> depthFirstNames = new ArrayList<>();
        graph.depthFirstSearch(start,
                (Visitor<String>) (visitedGraph, vertex) -> depthFirstNames.add(vertex.getName()));
        assertThat(depthFirstNames).containsExactly("start", "middle", "end");

        Edge<String>[] cycleEdges = graph.findCycles();
        assertThat(cycleEdges).isNotEmpty();
        assertThat(Arrays.stream(cycleEdges).map(edge -> edge.getFrom().getName())).contains("end");

        assertThat(graph.removeEdge(end, start)).isTrue();
        assertThat(graph.removeVertex(end)).isTrue();
        assertThat(graph.getVerticies()).containsExactly(start, middle);
    }

    @Test
    void stateTransitionsExposeAcceptStatesAndRejectIllegalActions() throws Exception {
        State ready = new State("ready");
        State running = new State("running");
        State finished = new State("finished");
        Transition start = new Transition("start", running);
        Transition finish = new Transition("finish", finished);

        ready.setData("initial data");
        ready.addTransition(start);
        running.addTransition(finish);

        assertThat(ready.getName()).isEqualTo("ready");
        assertThat(ready.getData()).isEqualTo("initial data");
        assertThat(ready.isAcceptState()).isFalse();
        assertThat(finished.isAcceptState()).isTrue();
        assertThat(ready.getTransition("start")).isSameAs(start);
        assertThat(start.getTarget()).isSameAs(running);
        assertThat(running.getTransition("finish").getTarget()).isSameAs(finished);
        assertThat(ready.getTransitions()).containsKey("start");
        assertThat(ready.toString()).contains("ready", "start", "running");

        assertThatExceptionOfType(IllegalTransitionException.class).isThrownBy(() -> {
            Transition missing = ready.getTransition("missing");
            if (missing == null) {
                throw new IllegalTransitionException("No transition for action: 'missing'");
            }
        });
    }

    @Test
    void stateMachineAdvancesCurrentStateClonesProgressAndResetsToStart() throws Exception {
        State draft = new State("draft");
        State review = new State("review");
        State published = new State("published");
        draft.addTransition(new Transition("submit", review));
        review.addTransition(new Transition("approve", published));
        Set<State> states = new HashSet<>(Arrays.asList(draft, review, published));
        StateMachine workflow = new StateMachine(states, draft, "publishing workflow");

        assertThat(workflow.getDescription()).isEqualTo("publishing workflow");
        assertThat(workflow.getStartState()).isSameAs(draft);
        assertThat(workflow.getCurrentState()).isSameAs(draft);
        assertThat(workflow.getStates()).containsExactlyInAnyOrder(draft, review, published);

        assertThat(workflow.nextState("submit")).isSameAs(review);
        StateMachine snapshot = (StateMachine) workflow.clone();
        assertThat(snapshot).isNotSameAs(workflow);
        assertThat(snapshot.getCurrentState()).isSameAs(review);

        assertThat(workflow.nextState("approve")).isSameAs(published);
        assertThat(snapshot.getCurrentState()).isSameAs(review);
        assertThat(workflow.reset()).isSameAs(draft);
        assertThat(workflow.toString()).contains("CurrentState:draft", "review", "published");
    }

    @Test
    void fileUtilitiesCopyEncodeDecodeAndResolveRelativePaths() throws Exception {
        Path source = tempDir.resolve("source file.txt");
        Path target = tempDir.resolve("target file.txt");
        writeString(source, "copied content", UTF_8);

        Files.copy(source.toFile(), target.toFile());

        assertThat(readString(target, UTF_8)).isEqualTo("copied content");
        String encoded = Files.encodeFileName("space and/slash.txt");
        assertThat(encoded).doesNotContain(" ", "/");
        assertThat(Files.decodeFileName(encoded)).isEqualTo("space and/slash.txt");
        assertThat(Files.findRelativePath(tempDir.toString(), target.toString())).endsWith("target file.txt/");
        assertThat(Files.delete(target.toFile())).isTrue();
        assertThat(target).doesNotExist();
    }

    @Test
    void lruCachePolicyPromotesAccessedEntriesAndEvictsLeastRecentlyUsedOnes() {
        LRUCachePolicy cache = new LRUCachePolicy(2, 3);
        cache.create();
        try {
            cache.insert("alpha", "A");
            cache.insert("bravo", "B");
            cache.insert("charlie", "C");

            assertThat(cache.size()).isEqualTo(3);
            assertThat(cache.get("alpha")).isEqualTo("A");

            cache.insert("delta", "D");

            assertThat(cache.size()).isEqualTo(3);
            assertThat(cache.peek("bravo")).isNull();
            assertThat(cache.peek("alpha")).isEqualTo("A");
            assertThat(cache.peek("charlie")).isEqualTo("C");
            assertThat(cache.peek("delta")).isEqualTo("D");

            cache.peek("charlie");
            cache.insert("echo", "E");

            assertThat(cache.peek("charlie")).isNull();
            assertThat(cache.peek("alpha")).isEqualTo("A");
            assertThat(cache.peek("delta")).isEqualTo("D");
            assertThat(cache.peek("echo")).isEqualTo("E");

            cache.remove("alpha");
            assertThat(cache.size()).isEqualTo(2);
            assertThat(cache.peek("alpha")).isNull();

            cache.flush();
            assertThat(cache.size()).isZero();
        } finally {
            cache.destroy();
        }
    }

    @Test
    void propertyEditorsConvertTextToTypedValues() throws Exception {
        IntegerEditor integerEditor = new IntegerEditor();
        integerEditor.setAsText("42");
        assertThat(integerEditor.getValue()).isEqualTo(Integer.valueOf(42));

        ByteArrayEditor byteArrayEditor = new ByteArrayEditor();
        byteArrayEditor.setAsText("bytes");
        assertThat((byte[]) byteArrayEditor.getValue()).isEqualTo("bytes".getBytes());

        StringArrayEditor stringArrayEditor = new StringArrayEditor();
        stringArrayEditor.setAsText("alpha,beta,\\,");
        assertThat((String[]) stringArrayEditor.getValue()).containsExactly("alpha", "beta", ",");
        assertThat(stringArrayEditor.getAsText()).isEqualTo("alpha,beta,\\,");

        URIEditor uriEditor = new URIEditor();
        uriEditor.setAsText("https://example.com/path");
        assertThat(uriEditor.getValue()).isEqualTo(URI.create("https://example.com/path"));

        URLEditor urlEditor = new URLEditor();
        urlEditor.setAsText("https://example.com/path");
        assertThat(((URL) urlEditor.getValue()).getHost()).isEqualTo("example.com");

        PropertyEditor editor = PropertyEditors.getEditor(Integer.class);
        editor.setAsText("7");
        assertThat(editor.getValue()).isEqualTo(Integer.valueOf(7));
        assertThat(PropertyEditors.isNull("null", true, true)).isTrue();
    }

    @Test
    void countersSynchronizationPrimitivesAndNestedThrowablesOperateWithoutBackgroundWork() throws Exception {
        Counter counter = new Counter(1);
        assertThat(counter.increment()).isEqualTo(2);
        assertThat(counter.decrement()).isEqualTo(1);
        Counter synchronizedCounter = Counter.makeSynchronized(counter);
        assertThat(synchronizedCounter.increment()).isEqualTo(2);
        synchronizedCounter.reset();
        assertThat(synchronizedCounter.getCount()).isZero();

        Semaphore semaphore = new Semaphore(1);
        semaphore.acquire();
        assertThat(semaphore.getUsers()).isEqualTo(1);
        semaphore.release();
        assertThat(semaphore.getUsers()).isZero();

        IllegalArgumentException cause = new IllegalArgumentException("root cause");
        NestedException nestedException = new NestedException("wrapper", cause);
        assertThat(nestedException.getNested()).isSameAs(cause);
        assertThat(nestedException.getCause()).isSameAs(cause);
        assertThat(nestedException.getMessage()).contains("wrapper", "root cause");
        assertThat(Throwables.toString(nestedException)).contains("wrapper", "root cause");
    }

    private static List<Object> iteratorContents(Iterator iterator) {
        List<Object> contents = new ArrayList<>();
        while (iterator.hasNext()) {
            contents.add(iterator.next());
        }
        return contents;
    }

    private static final class RecordingPropertyListener implements PropertyListener {
        private final List<String> events;

        private RecordingPropertyListener(List<String> events) {
            this.events = events;
        }

        @Override
        public void propertyAdded(PropertyEvent event) {
            record("added", event);
        }

        @Override
        public void propertyRemoved(PropertyEvent event) {
            record("removed", event);
        }

        @Override
        public void propertyChanged(PropertyEvent event) {
            record("changed", event);
        }

        private void record(String type, PropertyEvent event) {
            events.add(type + ":" + event.getPropertyName() + "=" + event.getPropertyValue());
        }
    }
}
