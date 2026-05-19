/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_expression;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.support.ReflectiveIndexAccessor;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectiveIndexAccessorTest {
    @Test
    void readsIndexedValueThroughConfiguredReadMethod() {
        ReflectiveIndexAccessor accessor = new ReflectiveIndexAccessor(FruitMap.class, Color.class, "getFruit");
        StandardEvaluationContext context = new StandardEvaluationContext();
        FruitMap fruitMap = new FruitMap();

        assertThat(accessor.canRead(context, fruitMap, Color.RED))
                .isTrue();
        TypedValue value = accessor.read(context, fruitMap, Color.RED);

        assertThat(value.getValue())
                .isEqualTo("cherry");
        assertThat(accessor.canWrite(context, fruitMap, Color.RED))
                .isFalse();
    }

    @Test
    void writesIndexedValueThroughConfiguredWriteMethod() {
        ReflectiveIndexAccessor accessor = new ReflectiveIndexAccessor(
                FruitMap.class, Color.class, "getFruit", "setFruit");
        StandardEvaluationContext context = new StandardEvaluationContext();
        FruitMap fruitMap = new FruitMap();

        assertThat(accessor.canRead(context, fruitMap, Color.RED))
                .isTrue();
        assertThat(accessor.canWrite(context, fruitMap, Color.RED))
                .isTrue();
        accessor.write(context, fruitMap, Color.RED, "strawberry");

        assertThat(fruitMap.getFruit(Color.RED))
                .isEqualTo("strawberry");
        assertThat(accessor.read(context, fruitMap, Color.RED).getValue())
                .isEqualTo("strawberry");
    }

    public enum Color {
        RED,
        ORANGE,
        YELLOW
    }

    public static final class FruitMap {
        private final Map<Color, String> fruitByColor = new EnumMap<>(Color.class);

        public FruitMap() {
            this.fruitByColor.put(Color.RED, "cherry");
            this.fruitByColor.put(Color.ORANGE, "orange");
            this.fruitByColor.put(Color.YELLOW, "banana");
        }

        public String getFruit(Color color) {
            return this.fruitByColor.get(color);
        }

        public void setFruit(Color color, String fruit) {
            this.fruitByColor.put(color, fruit);
        }
    }
}
