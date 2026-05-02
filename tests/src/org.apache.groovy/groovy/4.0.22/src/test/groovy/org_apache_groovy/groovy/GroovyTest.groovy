/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy

import groovy.transform.CompileStatic
import groovy.util.ObservableList
import groovy.util.ObservableMap
import org.junit.jupiter.api.Test

import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.util.Arrays

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertTrue

public class GroovyTest {
    @Test
    @CompileStatic
    void stringExtensionsNormalizePaddingAndTokens() {
        String normalized = '''
                |alpha
                |  beta
                |gamma
                '''.stripMargin().trim()
        String centered = 'groovy'.center(10, '.')
        List<String> tokens = 'core,runtime;native'.tokenize(',;')

        assertEquals('alpha\n  beta\ngamma', normalized)
        assertEquals('..groovy..', centered)
        assertEquals(Arrays.asList('core', 'runtime', 'native'), tokens)
        assertEquals('meta', 'metadata'.take(4))
        assertEquals('metadata', 'metadata'.take(20))
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
}
