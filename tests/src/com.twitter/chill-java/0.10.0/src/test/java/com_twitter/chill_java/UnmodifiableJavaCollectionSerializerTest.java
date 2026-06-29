/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_twitter.chill_java;

import static org.assertj.core.api.Assertions.assertThat;

import com.twitter.chill.KryoInstantiator;
import com.twitter.chill.KryoPool;
import com.twitter.chill.java.PackageRegistrar;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class UnmodifiableJavaCollectionSerializerTest {
    @Test
    void serializesUnmodifiableListThroughPackageRegistrar() {
        KryoPool pool = newPool();
        List<String> mutable = new ArrayList<String>();
        mutable.add("alpha");
        mutable.add("beta");
        List<String> original = Collections.unmodifiableList(mutable);

        assertThat(pool.hasRegistration(original.getClass())).isTrue();

        byte[] serialized = pool.toBytesWithClass(original);
        assertThat(serialized).isNotEmpty();
    }

    @Test
    void serializesUnmodifiableMapThroughPackageRegistrar() {
        KryoPool pool = newPool();
        Map<String, Integer> mutable = new LinkedHashMap<String, Integer>();
        mutable.put("one", 1);
        mutable.put("two", 2);
        Map<String, Integer> original = Collections.unmodifiableMap(mutable);

        assertThat(pool.hasRegistration(original.getClass())).isTrue();

        byte[] serialized = pool.toBytesWithClass(original);
        assertThat(serialized).isNotEmpty();
    }

    @Test
    void serializesUnmodifiableCollectionThroughPackageRegistrar() {
        KryoPool pool = newPool();
        List<String> mutable = new ArrayList<String>();
        mutable.add("red");
        mutable.add("blue");
        Collection<String> original = Collections.unmodifiableCollection(mutable);

        assertThat(pool.hasRegistration(original.getClass())).isTrue();

        byte[] serialized = pool.toBytesWithClass(original);
        assertThat(serialized).isNotEmpty();
    }

    private KryoPool newPool() {
        KryoInstantiator instantiator = new KryoInstantiator()
                .withRegistrar(PackageRegistrar.all());
        return KryoPool.withByteArrayOutputStream(2, instantiator);
    }
}
