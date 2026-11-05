/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_inject.guice;

import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.InjectionPoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ObjectArraysTest {
    @Test
    void exportsInjectionDependenciesAndPointsIntoTypedArrays() {
        InjectionPoint constructor = InjectionPoint.forConstructorOf(Client.class);
        Dependency<?>[] dependencies = constructor.getDependencies().toArray(new Dependency<?>[0]);
        assertEquals(2, dependencies.length);
        assertEquals(Key.get(String.class), dependencies[0].getKey());
        assertEquals(Key.get(Integer.class), dependencies[1].getKey());

        InjectionPoint[] points = InjectionPoint.forInstanceMethodsAndFields(Client.class).toArray(new InjectionPoint[0]);
        assertEquals(1, points.length);
        assertEquals("values", points[0].getMember().getName());
    }

    static final class Client {
        @Inject private List<String> values;

        @Inject
        private Client(String name, Integer count) {
        }
    }
}
