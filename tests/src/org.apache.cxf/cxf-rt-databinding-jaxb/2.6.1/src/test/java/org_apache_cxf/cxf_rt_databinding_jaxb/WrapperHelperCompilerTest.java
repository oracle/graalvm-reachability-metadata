/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_rt_databinding_jaxb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import javax.xml.bind.JAXBContext;
import javax.xml.namespace.QName;
import org.apache.cxf.databinding.WrapperHelper;
import org.apache.cxf.jaxb.JAXBDataBinding;
import org.junit.jupiter.api.Test;

public class WrapperHelperCompilerTest {
    @Test
    void wrapperOptimizationPreservesPublicHelperBehavior() throws Exception {
        JaxbTestSupport.disableOptimizedAccessors();
        JAXBDataBinding binding = new JAXBDataBinding(JAXBContext.newInstance(String.class));
        QName wrapperName = new QName("urn:cxf-jaxb-test", "optimizableWrapper");
        WrapperHelper firstHelper = createHelper(binding, wrapperName);
        WrapperHelper reusedHelper = createHelper(binding, wrapperName);

        OptimizableWrapper firstWrapper = (OptimizableWrapper)
                firstHelper.createWrapperObject(Collections.<Object>singletonList("generated"));
        OptimizableWrapper reusedWrapper = (OptimizableWrapper)
                reusedHelper.createWrapperObject(Collections.<Object>singletonList("reused"));

        assertThat(firstHelper.getWrapperParts(firstWrapper)).containsExactly("generated");
        assertThat(reusedHelper.getWrapperParts(reusedWrapper)).containsExactly("reused");
    }

    private static WrapperHelper createHelper(JAXBDataBinding binding, QName wrapperName) {
        return binding.createWrapperHelper(
                OptimizableWrapper.class,
                wrapperName,
                Collections.singletonList("value"),
                Collections.singletonList("string"),
                Collections.<Class<?>>singletonList(String.class));
    }

    public static class OptimizableWrapper {
        private String value;

        public OptimizableWrapper() {
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
