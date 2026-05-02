/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.web.bindery.requestfactory.shared.EntityProxy;
import com.google.web.bindery.requestfactory.shared.RequestFactory;
import com.google.web.bindery.requestfactory.vm.impl.Deobfuscator;
import com.google.web.bindery.requestfactory.vm.impl.OperationKey;

import org.junit.jupiter.api.Test;

import java.util.Collections;

public class DeobfuscatorInnerBuilderTest {
    @Test
    void loadsGeneratedBuilderFromRequestFactoryClassName() {
        Deobfuscator.Builder builder = Deobfuscator.Builder.load(
                TestRequestFactory.class,
                DeobfuscatorInnerBuilderTest.class.getClassLoader());

        Deobfuscator deobfuscator = builder.build();
        String typeToken = OperationKey.hash(TestProxy.class.getName());

        assertThat(deobfuscator.getTypeFromToken(typeToken)).isEqualTo(TestProxy.class.getName());
        assertThat(deobfuscator.getClientProxies(TestDomain.class.getName()))
                .containsExactly(TestProxy.class.getName());
        assertThat(deobfuscator.isReferencedType(TestProxy.class.getName())).isTrue();
    }

    @Test
    void loadsLiteGeneratedBuilderWhenServerBuilderIsAbsent() {
        Deobfuscator.Builder builder = Deobfuscator.Builder.load(
                TestLiteRequestFactory.class,
                DeobfuscatorInnerBuilderTest.class.getClassLoader());

        Deobfuscator deobfuscator = builder.build();
        String typeToken = OperationKey.hash(TestLiteProxy.class.getName());

        assertThat(deobfuscator.getTypeFromToken(typeToken))
                .isEqualTo(TestLiteProxy.class.getName());
        assertThat(deobfuscator.isReferencedType(TestLiteProxy.class.getName())).isTrue();
    }

    public interface TestRequestFactory extends RequestFactory {
    }

    public interface TestProxy extends EntityProxy {
    }

    public interface TestLiteRequestFactory extends RequestFactory {
    }

    public interface TestLiteProxy extends EntityProxy {
    }

    public static final class TestDomain {
    }

    public static final class TestRequestFactoryDeobfuscatorBuilder extends Deobfuscator.Builder {
        public TestRequestFactoryDeobfuscatorBuilder() {
            withRawTypeToken(
                    OperationKey.hash(TestProxy.class.getName()),
                    TestProxy.class.getName());
            withClientToDomainMappings(
                    TestDomain.class.getName(),
                    Collections.singletonList(TestProxy.class.getName()));
        }
    }

    public static final class TestLiteRequestFactoryDeobfuscatorBuilderLite
            extends Deobfuscator.Builder {
        public TestLiteRequestFactoryDeobfuscatorBuilderLite() {
            withRawTypeToken(
                    OperationKey.hash(TestLiteProxy.class.getName()),
                    TestLiteProxy.class.getName());
        }
    }
}
