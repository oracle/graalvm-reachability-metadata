/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.transform.trait.Traits
import org.junit.jupiter.api.Test

import java.lang.reflect.Method

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertTrue

@CompileStatic
public class TraitsTest {
    @Test
    void resolvesHelpersForAPrecompiledStatefulTrait() {
        ClassNode trait = ClassHelper.make(TraitsMessage)

        assertTrue(Traits.isTrait(trait))
        assertEquals(TraitsMessage.name + '$Trait$Helper', Traits.findHelper(trait).name)
        assertEquals(TraitsMessage.name + '$Trait$FieldHelper', Traits.findFieldHelper(trait).name)
        assertEquals(TraitsMessage.name + '$Trait$StaticFieldHelper', Traits.findStaticFieldHelper(trait).name)

        TraitsMessageConsumer consumer = new TraitsMessageConsumer()
        assertEquals('Hello Ada!', consumer.render('Ada'))
        consumer.prefix = 'Welcome '
        assertEquals('Welcome Grace!', consumer.render('Grace'))
    }

    @Test
    void resolvesABridgeToItsDefaultTraitImplementation() {
        Method bridge = TraitsMessageConsumer.getMethod('render', String)
        Method target = Traits.getBridgeMethodTarget(bridge)

        assertTrue(Traits.isBridgeMethod(bridge))
        assertNotNull(target)
        assertEquals(TraitsMessage, target.declaringClass)
        assertEquals('render', target.name)
        assertEquals(String, target.returnType)
        assertEquals([String], target.parameterTypes.toList())
        assertTrue(Traits.hasDefaultImplementation(target))
        assertEquals('Hello Lin!', new TraitsMessageConsumer().render('Lin'))
    }
}

@CompileStatic
trait TraitsMessage {
    String prefix = 'Hello '
    private static String suffix = '!'

    String render(String name) {
        return prefix + name + suffix
    }
}

@CompileStatic
class TraitsMessageConsumer implements TraitsMessage {
}
