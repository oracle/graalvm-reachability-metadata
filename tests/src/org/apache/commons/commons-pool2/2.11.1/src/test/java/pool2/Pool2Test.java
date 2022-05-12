/*
 * Licensed under Public Domain (CC0)
 *
 * To the extent possible under law, the person who associated CC0 with
 * this code has waived all copyright and related or neighboring
 * rights to this code.
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package pool2;

import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author Moritz Halbritter
 */
class Pool2Test {
    @Test
    void test() throws Exception {
        GenericObjectPoolConfig<MyPoolObject> config = new GenericObjectPoolConfig<>();
        config.setMaxTotal(10);

        GenericObjectPool<MyPoolObject> pool = new GenericObjectPool<>(new MyPooledObjectFactory(), config);
        MyPoolObject first = pool.borrowObject();
        assertThat(first.getId()).isEqualTo(1);
        assertThat(pool.borrowObject().getId()).isEqualTo(2);
        assertThat(pool.borrowObject().getId()).isEqualTo(3);
        assertThat(pool.borrowObject().getId()).isEqualTo(4);
        assertThat(pool.borrowObject().getId()).isEqualTo(5);
        assertThat(pool.borrowObject().getId()).isEqualTo(6);
        assertThat(pool.borrowObject().getId()).isEqualTo(7);
        assertThat(pool.borrowObject().getId()).isEqualTo(8);
        assertThat(pool.borrowObject().getId()).isEqualTo(9);
        assertThat(pool.borrowObject().getId()).isEqualTo(10);

        assertThatThrownBy(() -> pool.borrowObject(Duration.ofMillis(10))).isInstanceOf(NoSuchElementException.class);

        pool.returnObject(first);
        assertThat(pool.borrowObject().getId()).isEqualTo(1);
    }

    public static class MyPoolObject {
        private final int id;

        MyPoolObject(int id) {
            this.id = id;
        }

        int getId() {
            return id;
        }
    }

    private static class MyPooledObjectFactory extends BasePooledObjectFactory<MyPoolObject> {

        private final AtomicInteger instanceCounter = new AtomicInteger();

        @Override
        public MyPoolObject create() throws Exception {
            return new MyPoolObject(instanceCounter.incrementAndGet());
        }

        @Override
        public PooledObject<MyPoolObject> wrap(MyPoolObject obj) {
            return new DefaultPooledObject<>(obj);
        }
    }
}
