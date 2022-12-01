package org.graalvm.internal.tck;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Coordinates}.
 *
 * @author Moritz Halbritter
 */
class CoordinatesTests {
    @Test
    void parse() {
        Coordinates coordinates = Coordinates.parse("g:a:1");
        assertThat(coordinates.group()).isEqualTo("g");
        assertThat(coordinates.artifact()).isEqualTo("a");
        assertThat(coordinates.version()).isEqualTo("1");
    }

    @Test
    void sanitize() {
        Coordinates coordinates = new Coordinates("com.example", "some-artifact", "1.0.0.FINAL");
        assertThat(coordinates.sanitizedGroup()).isEqualTo("com_example");
        assertThat(coordinates.sanitizedArtifact()).isEqualTo("some_artifact");
    }

    @Test
    void capitalizedSanitizedArtifact() {
        Coordinates coordinates = new Coordinates("com.example", "some-artifact", "1.0.0.FINAL");
        assertThat(coordinates.capitalizedSanitizedArtifact()).isEqualTo("Some_artifact");
    }

    @Test
    void testToString() {
        Coordinates coordinates = new Coordinates("com.example", "some-artifact", "1.0.0.FINAL");
        assertThat(coordinates.toString()).isEqualTo("com.example:some-artifact:1.0.0.FINAL");
    }
}
