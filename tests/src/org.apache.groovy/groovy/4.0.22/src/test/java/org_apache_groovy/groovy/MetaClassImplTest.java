/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.MetaClassImpl;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.MethodClosure;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.util.EventListener;
import java.util.EventObject;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MetaClassImplTest {
    public static Lookup $getLookup() {
        return MethodHandles.lookup();
    }

    @Test
    void adaptsAClosureToAJavaBeanEventListener() {
        Publisher publisher = new Publisher();
        AtomicReference<String> received = new AtomicReference<>();
        Closure<Void> listener = new Closure<Void>(this) {
            @Override
            public Void call(Object... arguments) {
                received.set(((PublicationEvent) arguments[0]).reference);
                return null;
            }
        };
        InvokerHelper.setProperty(publisher, "published", listener);
        publisher.publish("order-17");
        assertEquals("order-17", received.get());
    }

    @Test
    void invokesAnInheritedPrivateMethodOnAGroovyObject() {
        PrivateChild target = new PrivateChild();
        MetaClassImpl metaClass = new MetaClassImpl(PrivateChild.class);
        metaClass.initialize();
        assertEquals("parent:delivery", metaClass.invokeMethod(PrivateChild.class, target,
                "reference", new Object[]{"delivery"}, false, true));
    }

    @Test
    void invokesAnInheritedPrivateMethodThroughSuperDispatch() {
        PrivateChild target = new PrivateChild();
        MetaClassImpl metaClass = new MetaClassImpl(PrivateChild.class);
        metaClass.initialize();
        assertEquals("parent:shipment", metaClass.invokeMethod(PrivateChild.class, target,
                "reference", new Object[]{"shipment"}, true, true));
    }

    public static class PrivateParent extends GroovyObjectSupport {
        private String reference(String value) {
            return "parent:" + value;
        }
    }

    public static class PrivateChild extends PrivateParent {
        public static Lookup $getLookup() {
            return MethodHandles.lookup();
        }
    }

    public interface PublicationListener extends EventListener {
        void published(PublicationEvent event);
    }

    public static class PublicationEvent extends EventObject {
        public final String reference;

        public PublicationEvent(Object source, String reference) {
            super(source);
            this.reference = reference;
        }
    }

    public static class Publisher {
        private PublicationListener listener;

        public void addPublicationListener(PublicationListener listener) {
            this.listener = listener;
        }

        public void removePublicationListener(PublicationListener listener) {
            if (this.listener == listener) {
                this.listener = null;
            }
        }

        public void publish(String reference) {
            listener.published(new PublicationEvent(this, reference));
        }
    }

    @Test
    void recoversTheSourceAstForAClasspathGroovyObject() {
        MetaClassImpl metaClass = new MetaClassImpl(AstViewModel.class);
        metaClass.initialize();
        ClassNode model = metaClass.getClassNode();
        assertNotNull(model);
        assertEquals(AstViewModel.class.getName(), model.getName());
        assertEquals("java.lang.String", model.getProperty("reference").getType().getName());
    }

    @Test
    void createsMultidimensionalArraysThroughAConstructorMethodClosure() {
        MethodClosure constructor = new MethodClosure(String[][].class, "new");
        String[][] matrix = (String[][]) constructor.call(2, 3);
        assertEquals(2, matrix.length);
        assertEquals(3, matrix[0].length);
        matrix[1][2] = "end";
        assertEquals("end", matrix[1][2]);
    }
}

class AstViewModel extends GroovyObjectSupport {
    public String reference;
}
