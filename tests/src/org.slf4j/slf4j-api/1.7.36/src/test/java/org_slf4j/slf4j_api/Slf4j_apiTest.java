package org_slf4j.slf4j_api;

import org.junit.jupiter.api.Test;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.slf4j.helpers.NOPLogger;
import org.slf4j.helpers.NOPLoggerFactory;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class Slf4j_apiTest {

    @Test
    void iLoggerFactoryDefaultsToNOPWhenNoBinding() {
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        assertNotNull(factory, "ILoggerFactory should not be null");
        assertTrue(factory instanceof NOPLoggerFactory,
                "Without a binding on the classpath, factory should be NOPLoggerFactory");

        Logger logger = LoggerFactory.getLogger("test");
        assertNotNull(logger);
        assertEquals("NOP", logger.getName());
        assertTrue(logger instanceof NOPLogger, "Logger should be NOPLogger under NOP factory");

        // NOP should report all levels disabled
        assertFalse(logger.isTraceEnabled());
        assertFalse(logger.isDebugEnabled());
        assertFalse(logger.isInfoEnabled());
        assertFalse(logger.isWarnEnabled());
        assertFalse(logger.isErrorEnabled());

        // All logging calls should be safe no-ops
        logger.trace("trace {}", 123);
        logger.debug("debug {}", 123);
        logger.info("info {}", 123);
        logger.warn("warn {}", 123);
        logger.error("error {}", 123);
        logger.error("error with throwable", new RuntimeException("boom"));
    }

    @Test
    void mdcIsNoOpWithoutBinding_andIsThreadLocal() throws Exception {
        // Make sure MDC starts clean
        MDC.clear();

        // With NOP adapter, put/get should be no-ops
        MDC.put("foo", "bar");
        assertNull(MDC.get("foo"), "MDC.get should return null with NOP adapter");
        assertNull(MDC.getCopyOfContextMap(), "MDC.getCopyOfContextMap should be null with NOP adapter");

        Map<String, String> m = new HashMap<>();
        m.put("a", "b");
        MDC.setContextMap(m);
        assertNull(MDC.get("a"), "MDC.setContextMap should be a no-op with NOP adapter");
        assertNull(MDC.getCopyOfContextMap());

        // Verify MDC operations are thread-local (even though they are no-ops)
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                MDC.put("k", "v");
                assertNull(MDC.get("k"), "MDC in a different thread should also be no-op");
                MDC.clear();
                assertNull(MDC.get("k"));
            } catch (Throwable ex) {
                failure.set(ex);
            }
        });
        t.start();
        t.join();
        if (failure.get() != null) {
            throw new AssertionError("Failure in MDC thread", failure.get());
        }

        // Main thread should still be unaffected
        assertNull(MDC.get("k"));
        MDC.clear();
    }

    @Test
    void markerFactoryFallsBackToBasic_andSupportsOperations() {
        // Without a StaticMarkerBinder, MarkerFactory should fall back to BasicMarkerFactory
        Object markerFactory = MarkerFactory.getIMarkerFactory();
        assertEquals("org.slf4j.helpers.BasicMarkerFactory", markerFactory.getClass().getName(),
                "MarkerFactory should use BasicMarkerFactory when no binding is present");

        Marker parent = MarkerFactory.getMarker("PARENT");
        Marker child = MarkerFactory.getMarker("CHILD");

        assertEquals("PARENT", parent.getName());
        assertFalse(parent.hasReferences());
        assertFalse(parent.contains(child));
        assertFalse(parent.contains("CHILD"));

        parent.add(child);
        assertTrue(parent.hasReferences());
        assertTrue(parent.contains(child));
        assertTrue(parent.contains("CHILD"));

        // Remove reference
        assertTrue(parent.remove(child));
        assertFalse(parent.hasReferences());
        assertFalse(parent.contains(child));

        // Detach from factory cache and ensure new instance is provided afterward
        Marker d1 = MarkerFactory.getMarker("DETACH_ME");
        boolean detached = MarkerFactory.getIMarkerFactory().detachMarker("DETACH_ME");
        // Detach may return false if not present; still, next get should provide an instance which may differ.
        Marker d2 = MarkerFactory.getMarker("DETACH_ME");
        // After a detach call, BasicMarkerFactory should return a fresh instance for the name
        assertNotSame(d1, d2, "Detached marker should not be the same instance as the newly created marker");
    }

    @Test
    void markerSerializationRoundTrip_preservesNameAndReferences() throws Exception {
        Marker a = MarkerFactory.getMarker("A");
        Marker b = MarkerFactory.getMarker("B");
        a.add(b);

        Marker a2 = roundTrip(a);
        assertNotNull(a2);
        assertEquals("A", a2.getName());
        // BasicMarker implements contains(String) based on reference names
        assertTrue(a2.contains("B"), "Deserialized marker should preserve references");
    }

    @Test
    void loggingWithMarkersAndParametersDoesNotThrow() {
        Logger logger = LoggerFactory.getLogger(Slf4j_apiTest.class);
        Marker marker = MarkerFactory.getMarker("M");

        logger.trace(marker, "trace {}", 1);
        logger.debug(marker, "debug {} {}", "x", 2);
        logger.info(marker, "info");
        logger.warn(marker, "warn with throwable", new IllegalStateException("warn"));
        logger.error(marker, "error {} and {}", "A", "B");
        logger.error(marker, "error with throwable {}", 3, new RuntimeException("boom"));

        // Also call level-checks with marker (NOP returns false)
        assertFalse(logger.isTraceEnabled(marker));
        assertFalse(logger.isDebugEnabled(marker));
        assertFalse(logger.isInfoEnabled(marker));
        assertFalse(logger.isWarnEnabled(marker));
        assertFalse(logger.isErrorEnabled(marker));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Serializable> T roundTrip(T obj) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(obj);
        }
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
            return (T) ois.readObject();
        }
    }
}
