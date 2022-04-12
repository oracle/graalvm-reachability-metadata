package org.example.library;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class ExampleTest {
    @Test
    void trueAssumption() {
        assumeTrue(1 > 0);
        assertEquals(1 + 2, 3);
    }
}
