package tests;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OrderTests {
    /* tests execution order should be dictated by the @Order annotation */
    protected static String testOrderChecker = "First test";

    @Test
    @Order(2)
    void secondTest() {
        assertTrue(testOrderChecker.equalsIgnoreCase("Second test"));
        testOrderChecker = null;
    }

    @Test
    @Order(3)
    void thirdTest() {
        assertNull(testOrderChecker);
    }

    @Test
    @Order(1)
    void firstTest() {
        assertTrue(testOrderChecker.equalsIgnoreCase("First test"));
        testOrderChecker = "Second test";
    }
}
