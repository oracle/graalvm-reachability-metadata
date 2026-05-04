/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy

import groovy.lang.Binding
import groovy.lang.Closure
import groovy.lang.GString
import groovy.lang.GroovyShell
import groovy.lang.IntRange
import groovy.lang.ListWithDefault
import groovy.lang.MapWithDefault
import groovy.lang.MissingPropertyException
import groovy.lang.ObjectRange
import groovy.lang.Tuple
import groovy.lang.Tuple2
import groovy.util.ConfigObject
import groovy.util.ConfigSlurper
import groovy.util.Expando
import groovy.util.GroovyCollections
import groovy.util.Node
import groovy.util.NodeBuilder
import groovy.util.NodeList
import groovy.util.ObservableList
import org.graalvm.internal.tck.NativeImageSupport
import org.junit.jupiter.api.Test

import java.beans.PropertyChangeEvent

import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.assertThatThrownBy

public class GroovyTest {
    @Test
    void bindingStoresScriptVariablesAndReportsMissingEntries() {
        Binding binding = new Binding([answer: 42])

        binding.setVariable('language', 'Groovy')
        binding.setProperty('active', true)

        assertThat(binding.hasVariable('answer')).isTrue()
        assertThat(binding.getVariable('answer')).isEqualTo(42)
        assertThat(binding.getVariable('language')).isEqualTo('Groovy')
        assertThat(binding.getVariable('active')).isEqualTo(true)
        assertThat(binding.variables).containsEntry('answer', 42)

        binding.removeVariable('language')
        assertThat(binding.hasVariable('language')).isFalse()
        assertThatThrownBy { binding.getVariable('language') }
                .isInstanceOf(MissingPropertyException)
    }

    @Test
    void gStringExposesSegmentsValuesWritableAndPatternConversion() {
        String subject = 'Groovy'
        Integer version = 4
        GString message = "${subject} ${version}.x"
        StringWriter writer = new StringWriter()

        message.writeTo(writer)
        GString extended = message.plus(' runtime')

        assertThat(message.valueCount).isEqualTo(2)
        assertThat(message.getValue(0)).isEqualTo('Groovy')
        assertThat(message.values).containsExactly('Groovy', 4)
        assertThat(message.strings).containsExactly('', ' ', '.x')
        assertThat(message.charAt(0)).isEqualTo('G' as char)
        assertThat(message.subSequence(0, 6).toString()).isEqualTo('Groovy')
        assertThat(writer.toString()).isEqualTo('Groovy 4.x')
        assertThat(extended.toString()).isEqualTo('Groovy 4.x runtime')
        assertThat(('Groovy' ==~ message.negate())).isFalse()
        assertThat(('Groovy 4.x' ==~ message.negate())).isTrue()
    }

    @Test
    void closuresSupportDelegationCurryingCompositionAndMemoization() {
        Map<String, Integer> delegate = [factor: 7]
        Closure<Integer> delegated = { int value -> value * factor }
        delegated.delegate = delegate
        delegated.resolveStrategy = Closure.DELEGATE_FIRST

        Closure<Integer> add = { int left, int right -> left + right }
        Closure<Integer> addTen = add.curry(10) as Closure<Integer>
        Closure<Integer> doubleValue = { int value -> value * 2 }
        Closure<Integer> doubledAfterAddingTen = addTen >> doubleValue
        Closure<Integer> memoized = { int value -> value * value }.memoize() as Closure<Integer>

        assertThat(delegated.call(3)).isEqualTo(21)
        assertThat(addTen.call(5)).isEqualTo(15)
        assertThat(doubledAfterAddingTen.call(4)).isEqualTo(28)
        assertThat(memoized.call(9)).isEqualTo(81)
        assertThat(memoized.call(9)).isEqualTo(81)
    }

    @Test
    void trampolineClosureComputesRecursiveResultsWithoutGrowingStack() {
        Closure<Long> factorial = null
        factorial = { long value, long accumulator = 1L ->
            value <= 1L ? accumulator : factorial.trampoline(value - 1L, accumulator * value)
        }.trampoline() as Closure<Long>

        assertThat(factorial.call(8L)).isEqualTo(40_320L)
    }

    @Test
    void expandoStoresDynamicPropertiesAndInvokesClosureBackedMethods() {
        Expando person = new Expando(first: 'Ada', last: 'Lovelace')
        person.fullName = { String separator -> "${person.first}${separator}${person.last}" }
        person.initials = { -> "${person.first[0]}${person.last[0]}" }

        assertThat(person.getProperty('first')).isEqualTo('Ada')
        assertThat(person.invokeMethod('fullName', [' '] as Object[]).toString()).isEqualTo('Ada Lovelace')
        assertThat(person.initials().toString()).isEqualTo('AL')
        assertThat(person.properties)
                .containsEntry('first', 'Ada')
                .containsKey('fullName')
    }

    @Test
    void rangesExposeBoundsIterationSteppingAndContainment() {
        IntRange ascending = new IntRange(2, 6)
        IntRange descending = new IntRange(5, 1)
        ObjectRange letters = new ObjectRange('a', 'd')

        assertThat(ascending.from).isEqualTo(2)
        assertThat(ascending.to).isEqualTo(6)
        assertThat(ascending).containsExactly(2, 3, 4, 5, 6)
        assertThat(ascending.step(2)).containsExactly(2, 4, 6)
        assertThat(ascending.subList(1, 4)).containsExactly(3, 4, 5)
        assertThat(descending.reverse).isTrue()
        assertThat(descending).containsExactly(5, 4, 3, 2, 1)
        assertThat(letters.containsWithinBounds('c')).isTrue()
        assertThat(letters.step(2)).containsExactly('a', 'c')
    }

    @Test
    void tuplesProvideTypedAccessorsComparableOrderingAndImmutableViews() {
        Tuple2<String, Integer> endpoint = Tuple.tuple('port', 8080)
        Tuple<String> words = new Tuple<String>('alpha', 'beta', 'gamma')
        Tuple<String> tail = words.subTuple(1, 3)

        assertThat(endpoint.first).isEqualTo('port')
        assertThat(endpoint.second).isEqualTo(8080)
        assertThat(endpoint.v1).isEqualTo('port')
        assertThat(endpoint.v2).isEqualTo(8080)
        assertThat(new ArrayList<String>(words)).containsExactly('alpha', 'beta', 'gamma')
        assertThat(tail).isEqualTo(new Tuple<String>('beta', 'gamma'))
        assertThat(Tuple.tuple('alpha', 1).compareTo(Tuple.tuple('alpha', 2))).isLessThan(0)
        assertThatThrownBy { endpoint.set(0, 'host') }
                .isInstanceOf(UnsupportedOperationException)
    }

    @Test
    void nodeBuilderCreatesNavigableNodeTrees() {
        NodeBuilder builder = new NodeBuilder()

        Node catalog = builder.catalog(region: 'EU') {
            book(id: 'b1') {
                title('Groovy in Action')
                author('Dierk Koenig')
            }
            book(id: 'b2', 'Groovy Reference')
        } as Node

        NodeList books = catalog.get('book') as NodeList
        Node firstBook = books[0] as Node
        Node secondBook = books[1] as Node
        List<Object> depthFirstNames = catalog.depthFirst()
                .findAll { Object item -> item instanceof Node }
                .collect { Node node -> node.name() }

        assertThat(catalog.name()).isEqualTo('catalog')
        assertThat(catalog.attribute('region')).isEqualTo('EU')
        assertThat(books).hasSize(2)
        assertThat(firstBook.attribute('id')).isEqualTo('b1')
        assertThat(firstBook.text()).contains('Groovy in Action', 'Dierk Koenig')
        assertThat(secondBook.text()).isEqualTo('Groovy Reference')
        assertThat(depthFirstNames).containsExactly('catalog', 'book', 'title', 'author', 'book')
    }

    @Test
    void configObjectFlattensMergesAndWritesNestedConfiguration() {
        ConfigObject base = new ConfigObject()
        ConfigObject server = new ConfigObject()
        server.put('host', 'localhost')
        server.put('port', 8080)
        base.put('server', server)

        ConfigObject override = new ConfigObject()
        ConfigObject overrideServer = new ConfigObject()
        overrideServer.put('port', 8443)
        override.put('server', overrideServer)
        override.put('featureEnabled', true)

        ConfigObject merged = base.merge(override)
        StringWriter writer = new StringWriter()
        merged.writeTo(writer)

        assertThat(merged.flatten())
                .containsEntry('server.host', 'localhost')
                .containsEntry('server.port', 8443)
                .containsEntry('featureEnabled', true)
        assertThat(merged.toProperties())
                .containsEntry('server.host', 'localhost')
                .containsEntry('server.port', '8443')
                .containsEntry('featureEnabled', 'true')
        assertThat(writer.toString()).contains('server').contains('featureEnabled')
    }

    @Test
    void defaultedCollectionsCreateValuesFromClosures() {
        Map<String, Integer> lengths = MapWithDefault.newInstance([:]) { String key -> key.length() }
        List<Integer> squares = ListWithDefault.newInstance([], false) { Integer index -> index * index }

        assertThat(lengths.get('groovy')).isEqualTo(6)
        assertThat(lengths).containsEntry('groovy', 6)
        assertThat(squares.get(4)).isEqualTo(16)
        assertThat(squares).hasSize(5)
        assertThat(squares.subList(2, 5)).containsExactly(4, 9, 16)
    }

    @Test
    void groovyCollectionsCalculateCombinationsTransposesSubsequencesAndUnions() {
        List<List<String>> combinations = GroovyCollections.combinations([['small', 'large'], ['red', 'blue']]) as List<List<String>>
        List<List<Object>> transposed = GroovyCollections.transpose([[1, 2, 3], ['a', 'b', 'c']]) as List<List<Object>>
        Set<List<String>> subsequences = GroovyCollections.subsequences(['a', 'b', 'c']) as Set<List<String>>
        List<String> union = GroovyCollections.union(['a', 'b'], ['b', 'c'], ['c', 'd']) as List<String>

        assertThat(combinations).containsExactlyInAnyOrder(
                ['small', 'red'],
                ['large', 'red'],
                ['small', 'blue'],
                ['large', 'blue'])
        assertThat(transposed).containsExactly([1, 'a'], [2, 'b'], [3, 'c'])
        assertThat(subsequences).contains(['a'], ['b'], ['c'], ['a', 'b'], ['a', 'c'], ['b', 'c'], ['a', 'b', 'c'])
        assertThat(union).containsExactly('a', 'b', 'c', 'd')
    }

    @Test
    void observableListPublishesElementAndSizeChangeEvents() {
        ObservableList tasks = new ObservableList(['draft'])
        List<PropertyChangeEvent> events = []
        tasks.addPropertyChangeListener { PropertyChangeEvent event ->
            events << event
        }

        tasks.add('review')
        String previous = tasks.set(0, 'plan') as String
        String removed = tasks.remove(1) as String

        assertThat(previous).isEqualTo('draft')
        assertThat(removed).isEqualTo('review')
        assertThat(tasks.content).containsExactly('plan')
        assertThat(tasks.size).isEqualTo(1)
        assertThat(events.collect { PropertyChangeEvent event -> event.propertyName })
                .containsExactly('content', 'size', 'content', 'content', 'size')
        assertThat(events.collect { PropertyChangeEvent event -> event.oldValue })
                .containsExactly(null, 1, 'draft', 'review', 2)
        assertThat(events.collect { PropertyChangeEvent event -> event.newValue })
                .containsExactly('review', 2, 'plan', null, 1)
    }

    @Test
    void groovyShellEvaluatesScriptsWithBindingWhenDynamicCompilationIsAvailable() {
        try {
            Binding binding = new Binding([base: 4])
            GroovyShell shell = new GroovyShell(binding)

            Object result = shell.evaluate('multiplier = 3\nbase * multiplier', 'CalculatorScript.groovy')

            assertThat(result).isEqualTo(12)
            assertThat(binding.getVariable('multiplier')).isEqualTo(3)
        } catch (Error error) {
            rethrowIfNotUnsupportedDynamicCompilation(error)
        }
    }

    @Test
    void configSlurperParsesEnvironmentAwareScriptsWhenDynamicCompilationIsAvailable() {
        try {
            ConfigSlurper slurper = new ConfigSlurper('production')
            slurper.setBinding([externalHost: 'example.org'])

            ConfigObject config = slurper.parse('''
                server.host = 'localhost'
                server.port = 8080
                environments {
                    production {
                        server.host = externalHost
                        server.secure = true
                    }
                }
            ''')

            assertThat(config.server.host).isEqualTo('example.org')
            assertThat(config.server.port).isEqualTo(8080)
            assertThat(config.server.secure).isEqualTo(true)
        } catch (Error error) {
            rethrowIfNotUnsupportedDynamicCompilation(error)
        }
    }

    private static void rethrowIfNotUnsupportedDynamicCompilation(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error
        }
    }
}
