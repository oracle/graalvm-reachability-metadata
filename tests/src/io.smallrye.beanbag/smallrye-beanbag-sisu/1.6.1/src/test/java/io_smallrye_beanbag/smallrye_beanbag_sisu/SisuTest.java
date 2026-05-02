/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_beanbag.smallrye_beanbag_sisu;

import static org.assertj.core.api.Assertions.assertThat;

import javax.inject.Inject;
import javax.inject.Named;

import org.junit.jupiter.api.Test;

import io.smallrye.beanbag.BeanBag;
import io.smallrye.beanbag.DependencyFilter;
import io.smallrye.beanbag.sisu.Sisu;

public class SisuTest {
    @Test
    void configuresBeansFromNamedAndPlexusMetadataResources() {
        BeanBag.Builder builder = BeanBag.builder();

        Sisu.configureSisu(SisuTest.class.getClassLoader(), builder, DependencyFilter.ACCEPT);

        BeanBag beanBag = builder.build();
        NamedComponent namedComponent = beanBag.requireBean(NamedComponent.class, "named-component");
        XmlContract xmlComponent = beanBag.requireBean(XmlContract.class, "xml-component");

        assertThat(namedComponent.describe()).isEqualTo("constructor field method");
        assertThat(xmlComponent.describe()).isEqualTo("xml xml-dependency");
        assertThat(beanBag.requireBean(XmlContract.class, "xml-component")).isSameAs(xmlComponent);
    }

    @Test
    void addClassConfiguresConstructorFieldAndMethodInjection() {
        BeanBag.Builder builder = BeanBag.builder();
        Sisu sisu = Sisu.createFor(builder);

        sisu.addClass(NamedConstructorDependency.class, DependencyFilter.ACCEPT);
        sisu.addClass(NamedFieldDependency.class, DependencyFilter.ACCEPT);
        sisu.addClass(NamedMethodDependency.class, DependencyFilter.ACCEPT);
        sisu.addClass(NamedComponent.class, DependencyFilter.ACCEPT);

        NamedComponent component = builder.build().requireBean(NamedComponent.class, "named-component");

        assertThat(component.describe()).isEqualTo("constructor field method");
    }
}

@Named("named-component")
class NamedComponent {
    private final NamedConstructorDependency constructorDependency;

    @Inject
    NamedFieldDependency fieldDependency;

    private NamedMethodDependency methodDependency;

    @Inject
    NamedComponent(NamedConstructorDependency constructorDependency) {
        this.constructorDependency = constructorDependency;
    }

    @Inject
    void setMethodDependency(NamedMethodDependency methodDependency) {
        this.methodDependency = methodDependency;
    }

    String describe() {
        return constructorDependency.describe() + " " + fieldDependency.describe() + " " + methodDependency.describe();
    }
}

class NamedConstructorDependency {
    String describe() {
        return "constructor";
    }
}

class NamedFieldDependency {
    String describe() {
        return "field";
    }
}

class NamedMethodDependency {
    String describe() {
        return "method";
    }
}

interface XmlContract {
    String describe();
}

class XmlComponent implements XmlContract {
    XmlDependency manualDependency;

    @Override
    public String describe() {
        return "xml " + manualDependency.describe();
    }
}

class XmlDependency {
    String describe() {
        return "xml-dependency";
    }
}
