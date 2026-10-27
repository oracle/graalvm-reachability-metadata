/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_scala_lang.scala3_library_3;

import java.util.List;

import org.junit.jupiter.api.Test;

import scala.collection.immutable.Seq;
import scala.collection.immutable.Seq$;
import scala.jdk.javaapi.CollectionConverters;
import scala.reflect.Selectable;
import scala.reflect.Selectable$;

import static org.assertj.core.api.Assertions.assertThat;

public class SelectableTest {
    @Test
    void selectDynamicReadsPublicField() {
        Selectable selectable = Selectable$.MODULE$.reflectiveSelectable(Integer.valueOf(0));

        Object value = selectable.selectDynamic("MAX_VALUE");

        assertThat(value).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void applyDynamicInvokesPublicMethod() {
        Selectable selectable = Selectable$.MODULE$.reflectiveSelectable("native-image");
        Seq<Class<?>> parameterTypes = Seq$.MODULE$.from(
                CollectionConverters.asScala(List.<Class<?>>of(int.class, int.class)));
        Seq<Object> arguments = Seq$.MODULE$.from(
                CollectionConverters.asScala(List.<Object>of(0, 6)));

        Object value = selectable.applyDynamic("substring", parameterTypes, arguments);

        assertThat(value).isEqualTo("native");
    }
}
