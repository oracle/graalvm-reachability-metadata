/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala_library;

import org.junit.jupiter.api.Test;

import scala.collection.mutable.ArrayBuffer;
import scala.runtime.ScalaRunTime$;

import static org.assertj.core.api.Assertions.assertThat;

public class ScalaRunTimeTest {

    @Test
    void derivesArrayClassesAndFormatsScalaCollections() {
        ScalaRunTime$ runtime = ScalaRunTime$.MODULE$;
        ArrayBuffer<String> values = new ArrayBuffer<>();
        values.$plus$eq("alpha");
        values.$plus$eq("beta");

        assertThat(runtime.arrayClass(String.class)).isEqualTo(String[].class);
        assertThat(runtime.stringOf(values)).isEqualTo("ArrayBuffer(alpha, beta)");
    }
}
