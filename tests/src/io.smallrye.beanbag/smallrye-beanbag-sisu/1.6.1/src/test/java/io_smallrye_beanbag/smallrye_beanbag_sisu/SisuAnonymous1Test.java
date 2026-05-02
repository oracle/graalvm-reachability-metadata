/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_beanbag.smallrye_beanbag_sisu;

import static org.assertj.core.api.Assertions.assertThat;

import javax.inject.Named;
import javax.inject.Provider;

import org.junit.jupiter.api.Test;

import io.smallrye.beanbag.BeanBag;
import io.smallrye.beanbag.DependencyFilter;
import io.smallrye.beanbag.sisu.Sisu;

public class SisuAnonymous1Test {
    @Test
    void registersProvidedGenericArrayType() {
        BeanBag.Builder builder = BeanBag.builder();
        Sisu sisu = Sisu.createFor(builder);

        sisu.addClass(GenericArrayProvider.class, DependencyFilter.ACCEPT);

        GenericArrayElement[] elements = builder.build().requireBean(GenericArrayElement[].class, "generic-array-provider");

        assertThat(elements).extracting(GenericArrayElement::name).containsExactly("provided");
    }
}

@Named("generic-array-provider")
class GenericArrayProvider<T extends GenericArrayElement> implements Provider<T[]> {
    @Override
    @SuppressWarnings("unchecked")
    public T[] get() {
        return (T[]) new GenericArrayElement[] {new GenericArrayElement("provided")};
    }
}

class GenericArrayElement {
    private final String name;

    GenericArrayElement(String name) {
        this.name = name;
    }

    String name() {
        return name;
    }
}
