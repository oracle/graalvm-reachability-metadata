/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib;

import net.sf.cglib.core.Signature;
import net.sf.cglib.reflect.FastClass;
import net.sf.cglib.reflect.FastConstructor;
import net.sf.cglib.reflect.FastMethod;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class FastClassTest {
    @Test
    void resolvesConstructorsAndMethodsWithAPrecompiledAccessor() throws Exception {
        FastClass accessor = accessor();
        FastConstructor constructor = accessor.getConstructor(new Class<?>[]{int.class});
        Counter counter = (Counter) constructor.newInstance(new Object[]{7});
        FastMethod increment = accessor.getMethod("add", new Class<?>[]{int.class});

        assertThat(increment.invoke(counter, new Object[]{5})).isEqualTo(12);
        assertThat(counter.value()).isEqualTo(12);
        assertThat(increment.getReturnType()).isEqualTo(int.class);
        assertThat(increment.getParameterTypes()).containsExactly(int.class);
        assertThat(increment.getDeclaringClass()).isSameAs(Counter.class);
        assertThat(constructor.getParameterTypes()).containsExactly(int.class);
        assertThat(accessor.getJavaClass()).isSameAs(Counter.class);
        assertThat(accessor.getName()).isEqualTo(Counter.class.getName());
        assertThat(accessor()).isEqualTo(accessor);
    }

    @Test
    void invokesByNameAndConstructsUsingParameterTypes() throws Exception {
        FastClass accessor = accessor();
        Counter counter = (Counter) accessor.newInstance(new Class<?>[]{int.class}, new Object[]{3});

        assertThat(accessor.invoke("add", new Class<?>[]{int.class}, counter, new Object[]{4}))
                .isEqualTo(7);
        assertThat(accessor.invoke("value", new Class<?>[0], counter, new Object[0])).isEqualTo(7);
        assertThat(((Counter) accessor.newInstance()).value()).isZero();
        assertThat(accessor.getConstructor(new Class<?>[0]).newInstance()).isInstanceOf(Counter.class);
    }

    @Test
    void reportsMissingMembersThroughTheFastClassApi() {
        FastClass accessor = accessor();

        assertThatExceptionOfType(NoSuchMethodError.class)
                .isThrownBy(() -> accessor.getMethod("missing", new Class<?>[0]));
        assertThatExceptionOfType(NoSuchMethodError.class)
                .isThrownBy(() -> accessor.getConstructor(new Class<?>[]{String.class}));
    }

    private FastClass accessor() {
        FastClass.Generator generator = new FastClass.Generator();
        generator.setType(Counter.class);
        generator.setClassLoader(Counter.class.getClassLoader());
        generator.setAttemptLoad(true);
        generator.setNamingPolicy((prefix, source, key, names) -> CounterAccessor.class.getName());
        generator.setStrategy(ignored -> {
            throw new AssertionError("The precompiled accessor must be reused");
        });
        return generator.create();
    }

    public static class Counter {
        private int value;

        public Counter() {
            this(0);
        }

        public Counter(int value) {
            this.value = value;
        }

        public int add(int amount) {
            value += amount;
            return value;
        }

        public int value() {
            return value;
        }
    }

    /// A statically compiled accessor selected through FastClass's attempt-load API.
    public static class CounterAccessor extends FastClass {
        public CounterAccessor(Class type) {
            super(type);
        }

        @Override
        public int getIndex(String name, Class[] parameterTypes) {
            if (name.equals("add") && parameterTypes.length == 1 && parameterTypes[0] == int.class) {
                return 0;
            }
            if (name.equals("value") && parameterTypes.length == 0) {
                return 1;
            }
            return -1;
        }

        @Override
        public int getIndex(Class[] parameterTypes) {
            if (parameterTypes.length == 0) {
                return 0;
            }
            return parameterTypes.length == 1 && parameterTypes[0] == int.class ? 1 : -1;
        }

        @Override
        public int getIndex(Signature signature) {
            return switch (signature.toString()) {
                case "add(I)I" -> 0;
                case "value()I" -> 1;
                default -> -1;
            };
        }

        @Override
        public Object invoke(int index, Object target, Object[] arguments) {
            Counter counter = (Counter) target;
            return switch (index) {
                case 0 -> counter.add((Integer) arguments[0]);
                case 1 -> counter.value();
                default -> throw new IllegalArgumentException("Unknown method index: " + index);
            };
        }

        @Override
        public Object newInstance(int index, Object[] arguments) {
            return switch (index) {
                case 0 -> new Counter();
                case 1 -> new Counter((Integer) arguments[0]);
                default -> throw new IllegalArgumentException("Unknown constructor index: " + index);
            };
        }

        @Override
        public int getMaxIndex() {
            return 1;
        }
    }
}
