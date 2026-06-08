/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_commons;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.activemq.artemis.commons.shaded.johnzon.core.BufferStrategy;
import org.apache.activemq.artemis.commons.shaded.johnzon.core.BufferStrategy.BufferProvider;
import org.apache.activemq.artemis.commons.shaded.johnzon.core.BufferStrategyFactory;
import org.junit.jupiter.api.Test;

public class BufferStrategyFactoryTest {
    @Test
    public void valueOfInstantiatesCustomBufferStrategyClass() {
        BufferStrategy strategy = BufferStrategyFactory.valueOf(CustomBufferStrategy.class.getName());

        assertThat(strategy).isInstanceOf(CustomBufferStrategy.class);
        BufferProvider<char[]> provider = strategy.newCharProvider(4);
        char[] buffer = provider.newBuffer();
        assertThat(buffer).containsExactly('j', 's', 'o', 'n');
        provider.release(buffer);
    }

    public static class CustomBufferStrategy implements BufferStrategy {
        @Override
        public BufferProvider<char[]> newCharProvider(int size) {
            return new CustomBufferProvider(size);
        }
    }

    private static final class CustomBufferProvider implements BufferProvider<char[]> {
        private static final long serialVersionUID = 1L;

        private final int size;

        private CustomBufferProvider(int size) {
            this.size = size;
        }

        @Override
        public char[] newBuffer() {
            char[] buffer = new char[size];
            char[] source = "json".toCharArray();
            System.arraycopy(source, 0, buffer, 0, Math.min(source.length, buffer.length));
            return buffer;
        }

        @Override
        public void release(char[] value) {
            assertThat(value).hasSize(size);
        }
    }
}
