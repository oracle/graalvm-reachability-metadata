/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package graphql.com.google.common.collect;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Spliterator;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Minimal relocated Guava set API required by graphql-java 19.7.
 */
public final class ImmutableSet<E> extends ImmutableCollection<E> implements Set<E> {

  private final Set<E> set;

  private ImmutableSet(Set<E> set) {
    super(set);
    this.set = set;
  }

  public static <E> ImmutableSet<E> of() {
    return new ImmutableSet<>(Set.of());
  }

  public static <E> ImmutableSet<E> of(E element) {
    LinkedHashSet<E> values = new LinkedHashSet<>();
    values.add(element);
    return copyOf(values);
  }

  public static <E> ImmutableSet<E> of(E first, E second) {
    return copyOf(List.of(first, second));
  }

  public static <E> ImmutableSet<E> of(E first, E second, E third) {
    return copyOf(List.of(first, second, third));
  }

  public static <E> ImmutableSet<E> of(E first, E second, E third, E fourth) {
    return copyOf(List.of(first, second, third, fourth));
  }

  public static <E> ImmutableSet<E> of(E first, E second, E third, E fourth, E fifth) {
    return copyOf(List.of(first, second, third, fourth, fifth));
  }

  @SafeVarargs
  public static <E> ImmutableSet<E> of(E first, E second, E third, E fourth, E fifth, E sixth, E... rest) {
    LinkedHashSet<E> values = new LinkedHashSet<>(rest.length + 6);
    Collections.addAll(values, first, second, third, fourth, fifth, sixth);
    Collections.addAll(values, rest);
    return copyOf(values);
  }

  public static <E> ImmutableSet<E> copyOf(java.util.Collection<? extends E> elements) {
    return new ImmutableSet<>(Collections.unmodifiableSet(new LinkedHashSet<>(elements)));
  }

  public static <E> ImmutableSet<E> copyOf(Iterable<? extends E> elements) {
    LinkedHashSet<E> values = new LinkedHashSet<>();
    for (E element : elements) {
      values.add(element);
    }
    return new ImmutableSet<>(Collections.unmodifiableSet(values));
  }

  public static <E> ImmutableSet<E> copyOf(Iterator<? extends E> elements) {
    LinkedHashSet<E> values = new LinkedHashSet<>();
    elements.forEachRemaining(values::add);
    return new ImmutableSet<>(Collections.unmodifiableSet(values));
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
  public boolean contains(Object element) {
    return set.contains(element);
  }

  @Override
  public int size() {
    return set.size();
  }

  @Override
  public Object[] toArray() {
    return set.toArray();
  }

  @Override
  public <T> T[] toArray(T[] array) {
    return set.toArray(array);
  }

  @Override
  public Spliterator<E> spliterator() {
    return set.spliterator();
  }

  @Override
  public boolean add(E element) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean addAll(java.util.Collection<? extends E> elements) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean remove(Object element) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean removeAll(java.util.Collection<?> elements) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean retainAll(java.util.Collection<?> elements) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clear() {
    throw new UnsupportedOperationException();
  }

  public static final class Builder<E> {

    private final LinkedHashSet<E> values;

    public Builder() {
      this.values = new LinkedHashSet<>();
    }

    private Builder(int expectedSize) {
      this.values = new LinkedHashSet<>(expectedSize);
    }

    public Builder<E> add(E element) {
      values.add(element);
      return this;
    }

    @SafeVarargs
    public final Builder<E> add(E... elements) {
      Collections.addAll(values, elements);
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
