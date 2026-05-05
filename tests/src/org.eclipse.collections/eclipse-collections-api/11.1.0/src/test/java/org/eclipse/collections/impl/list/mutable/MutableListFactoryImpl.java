/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.eclipse.collections.impl.list.mutable;

import java.util.stream.Stream;

import org.eclipse.collections.api.block.function.Function0;
import org.eclipse.collections.api.factory.list.MutableListFactory;
import org.eclipse.collections.api.list.MutableList;

public class MutableListFactoryImpl implements MutableListFactory {
    @Override
    public <T> MutableList<T> empty() {
        return null;
    }

    @Override
    public <T> MutableList<T> with(T... items) {
        return null;
    }

    @Override
    public <T> MutableList<T> withInitialCapacity(int capacity) {
        return null;
    }

    @Override
    public <T> MutableList<T> withAll(Iterable<? extends T> iterable) {
        return null;
    }

    @Override
    public <T> MutableList<T> fromStream(Stream<? extends T> stream) {
        return null;
    }

    @Override
    public <T> MutableList<T> withNValues(int size, Function0<? extends T> factory) {
        return null;
    }
}
