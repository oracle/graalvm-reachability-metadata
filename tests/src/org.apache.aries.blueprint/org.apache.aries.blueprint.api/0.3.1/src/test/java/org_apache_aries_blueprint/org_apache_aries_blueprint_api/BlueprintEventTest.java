/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries_blueprint.org_apache_aries_blueprint_api;

import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.service.blueprint.container.BlueprintEvent;
import org.osgi.service.blueprint.container.ComponentDefinitionException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BlueprintEventTest {
    private final Bundle bundle = new EventBundle();
    private final Bundle extender = new EventBundle();

    @Test
    void recordsContainerLifecycleEvents() {
        int[] types = {BlueprintEvent.CREATING, BlueprintEvent.CREATED,
                BlueprintEvent.DESTROYING, BlueprintEvent.DESTROYED};
        for (int type : types) {
            long before = System.currentTimeMillis();
            BlueprintEvent event = new BlueprintEvent(type, bundle, extender);
            long after = System.currentTimeMillis();

            assertEquals(type, event.getType());
            assertSame(bundle, event.getBundle());
            assertSame(extender, event.getExtenderBundle());
            assertTrue(event.getTimestamp() >= before);
            assertTrue(event.getTimestamp() <= after);
            assertNull(event.getDependencies());
            assertNull(event.getCause());
            assertFalse(event.isReplay());
        }
    }

    @Test
    void protectsDependencyFiltersFromMutation() {
        int[] types = {BlueprintEvent.WAITING, BlueprintEvent.GRACE_PERIOD};
        String filter = "(objectClass=example.Inventory)";
        for (int type : types) {
            String[] dependencies = {filter};
            BlueprintEvent event = new BlueprintEvent(type, bundle, extender, dependencies);
            dependencies[0] = "(objectClass=example.Other)";
            assertArrayEquals(new String[]{filter}, event.getDependencies());

            String[] returned = event.getDependencies();
            returned[0] = "(objectClass=example.Changed)";
            assertArrayEquals(new String[]{filter}, event.getDependencies());
            assertEquals(type, event.getType());
        }
    }

    @Test
    void replaysFailureWithoutChangingOriginalEvent() {
        ComponentDefinitionException cause = new ComponentDefinitionException("Inventory unavailable");
        String[] dependencies = {"(objectClass=example.Inventory)"};
        BlueprintEvent original = new BlueprintEvent(BlueprintEvent.FAILURE, bundle, extender, dependencies, cause);
        BlueprintEvent replay = new BlueprintEvent(original, true);

        assertTrue(replay.isReplay());
        assertFalse(original.isReplay());
        assertEquals(original.getType(), replay.getType());
        assertEquals(original.getTimestamp(), replay.getTimestamp());
        assertSame(bundle, replay.getBundle());
        assertSame(extender, replay.getExtenderBundle());
        assertSame(cause, replay.getCause());
        assertArrayEquals(dependencies, replay.getDependencies());
        replay.getDependencies()[0] = "changed";
        assertArrayEquals(dependencies, original.getDependencies());
        assertArrayEquals(dependencies, replay.getDependencies());
        assertFalse(new BlueprintEvent(replay, false).isReplay());
    }

    @Test
    void recordsFailureWithoutMissingDependencies() {
        IllegalArgumentException cause = new IllegalArgumentException("Invalid bean configuration");
        BlueprintEvent event = new BlueprintEvent(BlueprintEvent.FAILURE, bundle, extender, cause);

        assertEquals(BlueprintEvent.FAILURE, event.getType());
        assertSame(cause, event.getCause());
        assertNull(event.getDependencies());
    }

    @Test
    void requiresBundlesAndValidDependencyLists() {
        assertThrows(NullPointerException.class,
                () -> new BlueprintEvent(BlueprintEvent.CREATED, null, extender));
        assertThrows(NullPointerException.class,
                () -> new BlueprintEvent(BlueprintEvent.CREATED, bundle, null));
        for (int type : new int[]{BlueprintEvent.WAITING, BlueprintEvent.GRACE_PERIOD}) {
            assertThrows(NullPointerException.class,
                    () -> new BlueprintEvent(type, bundle, extender));
            assertThrows(IllegalArgumentException.class,
                    () -> new BlueprintEvent(type, bundle, extender, new String[0]));
        }
        assertThrows(IllegalArgumentException.class,
                () -> new BlueprintEvent(BlueprintEvent.FAILURE, bundle, extender, new String[0]));
        assertThrows(IllegalArgumentException.class,
                () -> new BlueprintEvent(BlueprintEvent.CREATED, bundle, extender, new String[]{"(id=inventory)"}));
    }
}
