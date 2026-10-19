/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.dubbo.common.compiler.Compiler;
import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.common.extension.SPI;
import com.alibaba.dubbo.common.serialize.Serialization;
import com.alibaba.dubbo.rpc.Protocol;
import org.junit.jupiter.api.Test;

public class ExtensionLoaderTest {
    @Test
    void discoversAndCreatesNamedSerializationExtensions() {
        ExtensionLoader<Serialization> loader = ExtensionLoader.getExtensionLoader(Serialization.class);

        assertThat(loader.getSupportedExtensions())
                .contains("hessian2", "java", "compactedjava", "dubbo", "nativejava");
        assertThat(loader.getExtension("hessian2").getContentType()).isEqualTo("x-application/hessian2");
        assertThat(loader.getExtension("java").getContentType()).isEqualTo("x-application/java");

        Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class).getExtension("injvm");
        assertThat(protocol).isNotNull();
        protocol.destroy();
    }

    @Test
    void injectsAdaptiveExtensionsIntoProgrammaticallyAddedExtensions() {
        ExtensionLoader<InjectableExtension> loader =
                ExtensionLoader.getExtensionLoader(InjectableExtension.class);
        loader.addExtension("compiler-aware", CompilerAwareExtension.class);

        InjectableExtension extension = loader.getExtension("compiler-aware");

        assertThat(extension.compiler())
                .isSameAs(ExtensionLoader.getExtensionLoader(Compiler.class).getAdaptiveExtension());
        assertThat(loader.getExtensionName(extension)).isEqualTo("compiler-aware");
    }

    @SPI
    public interface InjectableExtension {
        Compiler compiler();
    }

    public static class CompilerAwareExtension implements InjectableExtension {
        private Compiler compiler;

        public void setCompiler(Compiler compiler) {
            this.compiler = compiler;
        }

        @Override
        public Compiler compiler() {
            return compiler;
        }
    }
}
