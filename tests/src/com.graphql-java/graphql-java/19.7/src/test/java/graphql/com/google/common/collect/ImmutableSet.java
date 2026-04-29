/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package graphql.com.google.common.collect;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public final class ImmutableSet<E> extends AbstractSet<E> {

  private final Set<E> delegate;

  private ImmutableSet(Collection<? extends E> values) {
    this.delegate = Collections.unmodifiableSet(new LinkedHashSet<>(values));
  }

  @SafeVarargs
  private ImmutableSet(E... values) {
    Set<E> set = new LinkedHashSet<>(values.length);
    Collections.addAll(set, values);
    this.delegate = Collections.unmodifiableSet(set);
  }

  public static <E> ImmutableSet<E> of(E element) {
    return new ImmutableSet<>(element);
  }

  public static <E> ImmutableSet<E> of(E first, E second, E third) {
    return new ImmutableSet<>(first, second, third);
  }

  public static <E> ImmutableSet<E> of(E first, E second, E third, E fourth) {
    return new ImmutableSet<>(first, second, third, fourth);
  }

  public static <E> ImmutableSet<E> of(E first, E second, E third, E fourth, E fifth) {
    return new ImmutableSet<>(first, second, third, fourth, fifth);
  }

  public static <E> ImmutableSet<E> copyOf(Collection<? extends E> values) {
    return new ImmutableSet<>(values);
  }

  public static <E> Builder<E> builder() {
    return new Builder<>();
  }

  public static <E> Builder<E> builderWithExpectedSize(int expectedSize) {
    return new Builder<>(expectedSize);
  }

  public static <E> Collector<E, ?, ImmutableSet<E>> toImmutableSet() {
    return Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), ImmutableSet::copyOf);
  }

  @Override
  public UnmodifiableIterator<E> iterator() {
    return UnmodifiableIterator.copyOf(delegate.iterator());
  }

  @Override
  public int size() {
    return delegate.size();
  }

  @Override
  public boolean contains(Object object) {
    return delegate.contains(object);
  }

  public static final class Builder<E> {

    private final Set<E> values;

    public Builder() {
      this.values = new LinkedHashSet<>();
    }

    public Builder(int expectedSize) {
      this.values = new LinkedHashSet<>(expectedSize);
    }

    public Builder<E> add(E element) {
      values.add(element);
      return this;
    }

    public Builder<E> addAll(Iterable<? extends E> elements) {
      for (E element : elements) {
        values.add(element);
      }
      return this;
    }

    public ImmutableSet<E> build() {
      return ImmutableSet.copyOf(values);
    }
  }
}
