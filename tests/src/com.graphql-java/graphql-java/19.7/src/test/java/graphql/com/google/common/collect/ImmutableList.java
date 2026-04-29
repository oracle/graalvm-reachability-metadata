/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package graphql.com.google.common.collect;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.RandomAccess;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public final class ImmutableList<E> extends AbstractList<E> implements RandomAccess {

  private final List<E> delegate;

  private ImmutableList(Collection<? extends E> values) {
    this.delegate = Collections.unmodifiableList(new ArrayList<>(values));
  }

  @SafeVarargs
  private ImmutableList(E... values) {
    List<E> list = new ArrayList<>(values.length);
    Collections.addAll(list, values);
    this.delegate = Collections.unmodifiableList(list);
  }

  public static <E> ImmutableList<E> of() {
    return new ImmutableList<>(Collections.emptyList());
  }

  public static <E> ImmutableList<E> of(E element) {
    return new ImmutableList<>(element);
  }

  public static <E> ImmutableList<E> of(E first, E second, E third, E fourth, E fifth) {
    return new ImmutableList<>(first, second, third, fourth, fifth);
  }

  public static <E> ImmutableList<E> copyOf(Iterable<? extends E> values) {
    if (values instanceof Collection) {
      return copyOf((Collection<? extends E>) values);
    }
    Builder<E> builder = builder();
    builder.addAll(values);
    return builder.build();
  }

  public static <E> ImmutableList<E> copyOf(Collection<? extends E> values) {
    return new ImmutableList<>(values);
  }

  public static <E> ImmutableList<E> copyOf(Iterator<? extends E> values) {
    Builder<E> builder = builder();
    while (values.hasNext()) {
      builder.add(values.next());
    }
    return builder.build();
  }

  public static <E> Builder<E> builder() {
    return new Builder<>();
  }

  public static <E> Builder<E> builderWithExpectedSize(int expectedSize) {
    return new Builder<>(expectedSize);
  }

  public static <E> Collector<E, ?, ImmutableList<E>> toImmutableList() {
    return Collectors.collectingAndThen(Collectors.toList(), ImmutableList::copyOf);
  }

  @Override
  public E get(int index) {
    return delegate.get(index);
  }

  @Override
  public int size() {
    return delegate.size();
  }

  @Override
  public UnmodifiableIterator<E> iterator() {
    return UnmodifiableIterator.copyOf(delegate.iterator());
  }

  @Override
  public boolean contains(Object object) {
    return delegate.contains(object);
  }

  @Override
  public int indexOf(Object object) {
    return delegate.indexOf(object);
  }

  public static final class Builder<E> {

    private final List<E> values;

    public Builder() {
      this.values = new ArrayList<>();
    }

    public Builder(int expectedSize) {
      this.values = new ArrayList<>(expectedSize);
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

    public ImmutableList<E> build() {
      return ImmutableList.copyOf(values);
    }
  }
}
