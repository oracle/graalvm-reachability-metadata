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
import groovy.lang.Script
import groovy.lang.Tuple
import groovy.lang.Tuple2
import groovy.util.ConfigObject
import groovy.util.Expando
import groovy.util.ObservableList
import groovy.util.ObservableMap
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.runtime.InvokerHelper
import org.graalvm.internal.tck.NativeImageSupport
import org.junit.jupiter.api.Test

import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.io.StringWriter
import java.math.BigInteger
import java.util.Locale
import java.util.Properties
import java.util.concurrent.atomic.AtomicInteger

import static org.junit.jupiter.api.Assertions.assertArrayEquals
import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertSame
import static org.junit.jupiter.api.Assertions.assertTrue

public class GroovyTest {
    @Test
    void gStringSupportsLazyInterpolationAndWritableOutput() {
        AtomicInteger counter = new AtomicInteger(7)
        GString message = "counter=${-> counter.get()} initial=${counter.get()}"

        assertArrayEquals(['counter=', ' initial=', ''] as String[], message.strings)
        assertEquals('counter=7 initial=7', message.toString())

        counter.set(11)
        StringWriter writer = new StringWriter()
        message.writeTo(writer)

        assertEquals('counter=11 initial=7', writer.toString())
        assertEquals(2, message.values.length)
        assertTrue(message.values[0] instanceof Closure)
        assertEquals(7, message.values[1])
    }

    @Test
    void closuresSupportCurryingCompositionMemoizationAndTrampolining() {
        Closure<Integer> sumThree = { int first, int second, int third -> first + second + third }
        Closure<Integer> curriedFirst = sumThree.curry(2)
        Closure<Integer> curriedLast = sumThree.rcurry(5)
        Closure<Integer> curriedMiddle = sumThree.ncurry(1, 4)

        assertEquals(10, curriedFirst(3, 5))
        assertEquals(10, curriedLast(2, 3))
        assertEquals(10, curriedMiddle(1, 5))

        Closure<String> emphasize = ({ String value -> value.reverse() } >> { String value -> value.toUpperCase(Locale.ROOT) })
        assertEquals('YVOORG', emphasize('groovy'))

        AtomicInteger invocationCount = new AtomicInteger()
        Closure<Integer> square = { int value ->
            invocationCount.incrementAndGet()
            value * value
        }.memoize()

        assertEquals(81, square(9))
        assertEquals(81, square(9))
        assertEquals(1, invocationCount.get())

        Closure<BigInteger> factorial
        factorial = { BigInteger value, BigInteger accumulator = 1G ->
            value <= 1G ? accumulator : factorial.trampoline(value - 1G, accumulator * value)
        }.trampoline()

        assertEquals(3_628_800G, factorial(10G))
    }

    @Test
    void rangesAndCollectionExtensionsProvideDeterministicTransformations() {
        IntRange inclusiveRange = 1..10
        List<Integer> evenNumbers = inclusiveRange.findAll { int value -> value % 2 == 0 } as List<Integer>
        List<Integer> squares = (1..5).collect { int value -> value * value } as List<Integer>
        Map<String, List<Integer>> groupedByParity = (1..6).groupBy { int value -> value % 2 == 0 ? 'even' : 'odd' }
        Integer total = inclusiveRange.inject(0) { int accumulator, int value -> accumulator + value }
        List<String> flattened = ['groovy', 'java'].collectMany { String language -> [language, language.capitalize()] } as List<String>

        assertEquals([2, 4, 6, 8, 10], evenNumbers)
        assertEquals([1, 4, 9, 16, 25], squares)
        assertEquals([1, 3, 5], groupedByParity['odd'])
        assertEquals([2, 4, 6], groupedByParity['even'])
        assertEquals(55, total)
        assertEquals(['groovy', 'Groovy', 'java', 'Java'], flattened)
        assertTrue(inclusiveRange.containsWithinBounds(10))
        assertFalse(inclusiveRange.containsWithinBounds(11))
    }

    @Test
    void tupleAndExpandoExposeTypedAndDynamicState() {
        Tuple2<String, Integer> coordinate = Tuple.tuple('groovy', 4)
        Tuple<Object> release = new Tuple<Object>('apache', coordinate, 22)
        Expando module = new Expando(name: 'Groovy', major: 4)
        module.describe = { -> "${module.name} ${module.major}.0" }
        module.minor = 22

        assertEquals('groovy', coordinate.first)
        assertEquals(4, coordinate.second)
        assertEquals(['apache', coordinate], release.subList(0, 2))
        assertEquals('Groovy 4.0', module.describe().toString())
        assertEquals(22, module.properties['minor'])
        assertFalse(module.properties.containsKey('unknown'))
        assertEquals(null, module.unknown)
    }

    @Test
    void observableCollectionsPublishMutationEvents() {
        ObservableMap settings = new ObservableMap([language: 'Groovy'])
        List<PropertyChangeEvent> mapEvents = []
        PropertyChangeListener mapListener = {
            PropertyChangeEvent event -> mapEvents << event
        } as PropertyChangeListener
        settings.addPropertyChangeListener(mapListener)

        assertEquals('Groovy', settings.put('language', 'Apache Groovy'))
        settings.put('runtime', 'native')
        settings.remove('runtime')

        assertEquals('Apache Groovy', settings['language'])
        assertEquals([
                'language',
                'runtime',
                ObservableMap.SIZE_PROPERTY,
                'runtime',
                ObservableMap.SIZE_PROPERTY
        ], mapEvents*.propertyName)
        assertTrue(mapEvents[0] instanceof ObservableMap.PropertyUpdatedEvent)
        assertEquals('Groovy', mapEvents[0].oldValue)
        assertEquals('Apache Groovy', mapEvents[0].newValue)
        assertTrue(mapEvents[1] instanceof ObservableMap.PropertyAddedEvent)
        assertTrue(mapEvents[3] instanceof ObservableMap.PropertyRemovedEvent)

        ObservableList phases = new ObservableList(['parse'])
        List<PropertyChangeEvent> listEvents = []
        PropertyChangeListener listListener = {
            PropertyChangeEvent event -> listEvents << event
        } as PropertyChangeListener
        phases.addPropertyChangeListener(listListener)

        assertTrue(phases.add('compile'))
        assertEquals('parse', phases.set(0, 'analyze'))
        assertTrue(phases.remove('compile'))

        assertEquals(['analyze'], phases.content)
        assertEquals([
                ObservableList.CONTENT_PROPERTY,
                ObservableList.SIZE_PROPERTY,
                ObservableList.CONTENT_PROPERTY,
                ObservableList.CONTENT_PROPERTY,
                ObservableList.SIZE_PROPERTY
        ], listEvents*.propertyName)
        assertTrue(listEvents[0] instanceof ObservableList.ElementAddedEvent)
        assertTrue(listEvents[2] instanceof ObservableList.ElementUpdatedEvent)
        assertEquals('parse', listEvents[2].oldValue)
        assertEquals('analyze', listEvents[2].newValue)
        assertTrue(listEvents[3] instanceof ObservableList.ElementRemovedEvent)
    }

    @Test
    void configObjectFlattensMergesAndRendersNestedConfiguration() {
        ConfigObject config = new ConfigObject()
        config.server.host = 'localhost'
        config.server.port = 8080
        config.features.enabled = true

        Map<String, Object> flattened = config.flatten() as Map<String, Object>
        Properties properties = config.toProperties()

        assertEquals('localhost', flattened['server.host'])
        assertEquals(8080, flattened['server.port'])
        assertEquals('true', properties.getProperty('features.enabled'))

        ConfigObject override = new ConfigObject()
        override.server.port = 9090
        override.features.name = 'metadata'

        assertSame(config, config.merge(override))
        assertEquals(9090, config.server.port)
        assertEquals('metadata', config.features.name)

        String rendered = config.prettyPrint()
        assertTrue(rendered.contains('server'))
        assertTrue(rendered.contains('localhost'))
    }

    @Test
    void invokerHelperCreatesScriptsWithBindings() {
        Binding binding = new Binding([name: 'Groovy', numbers: [1, 2, 3]])
        Script script = InvokerHelper.createScript(GreetingScript, binding)

        assertEquals('Hello Groovy #6', script.run().toString())

        script.setProperty('name', 'Apache Groovy')
        script.setProperty('numbers', [4, 5])

        assertEquals('Hello Apache Groovy #9', script.run().toString())
    }

    @Test
    void groovyShellEvaluatesRuntimeScriptsOrFailsForNativeDynamicClassLoading() {
        Binding binding = new Binding([limit: 5])
        CompilerConfiguration configuration = new CompilerConfiguration()
        configuration.debug = false
        configuration.verbose = false
        GroovyShell shell = new GroovyShell(binding, configuration)

        try {
            Object result = shell.evaluate('''
                (1..limit)
                    .collect { int value -> value * 2 }
                    .findAll { int value -> value > 4 }
                    .sum()
            ''')

            assertEquals(24, result)
        } catch (Error error) {
            rethrowUnlessUnsupportedDynamicClassLoading(error)
        }
    }

    private static void rethrowUnlessUnsupportedDynamicClassLoading(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error
        }
    }

    public static class GreetingScript extends Script {
        @Override
        Object run() {
            String name = getBinding().getVariable('name') as String
            List<Integer> numbers = getBinding().getVariable('numbers') as List<Integer>
            Integer sum = numbers.sum() as Integer

            "Hello ${name} #${sum}"
        }
    }
}
