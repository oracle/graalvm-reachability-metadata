/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.lang.Closure;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.MethodClosure;

import java.util.EventListener;
import java.util.EventObject;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MetaClassImplTest {
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
    void createsMultidimensionalArraysThroughAConstructorMethodClosure() {
        MethodClosure constructor = new MethodClosure(String[][].class, "new");
        String[][] matrix = (String[][]) constructor.call(2, 3);
        assertEquals(2, matrix.length);
        assertEquals(3, matrix[0].length);
        matrix[1][2] = "end";
        assertEquals("end", matrix[1][2]);
    }
}
