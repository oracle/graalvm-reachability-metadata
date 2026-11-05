/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_cxf.cxf_api;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import org.apache.cxf.feature.AbstractFeature;
import org.apache.cxf.feature.Features;
import org.apache.cxf.feature.LoggingFeature;
import org.apache.cxf.interceptor.AbstractBasicInterceptorProvider;
import org.apache.cxf.interceptor.AnnotationInterceptors;
import org.apache.cxf.interceptor.InFaultInterceptors;
import org.apache.cxf.interceptor.InInterceptors;
import org.apache.cxf.interceptor.LoggingInInterceptor;
import org.apache.cxf.interceptor.LoggingOutInterceptor;
import org.apache.cxf.interceptor.OutFaultInterceptors;
import org.apache.cxf.interceptor.OutInterceptors;
import org.apache.cxf.phase.Phase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AnnotationInterceptorsTest {
    @Test
    void createsInterceptorsFromClassAndNameAnnotations() {
        AnnotationInterceptors annotations = new AnnotationInterceptors(AnnotatedService.class);
        assertEquals(1, annotations.getInInterceptors().size());
        LoggingInInterceptor inbound = (LoggingInInterceptor) annotations.getInInterceptors().get(0);
        assertEquals(Phase.RECEIVE, inbound.getPhase());
        assertEquals(1, annotations.getOutInterceptors().size());
        LoggingOutInterceptor outbound = (LoggingOutInterceptor) annotations.getOutInterceptors().get(0);
        assertEquals(Phase.PRE_STREAM, outbound.getPhase());
        assertEquals(1, annotations.getInFaultInterceptors().size());
        assertTrue(annotations.getInFaultInterceptors().get(0) instanceof LoggingInInterceptor);
        assertEquals(1, annotations.getOutFaultInterceptors().size());
        assertTrue(annotations.getOutFaultInterceptors().get(0) instanceof LoggingOutInterceptor);

        StringWriter log = new StringWriter();
        try (PrintWriter logWriter = new PrintWriter(log)) {
            inbound.setPrintWriter(logWriter);
            inbound.setLimit(1024);
            inbound.setPrettyLogging(true);
            assertSame(logWriter, inbound.getPrintWriter());
            assertEquals(1024, inbound.getLimit());
            assertTrue(inbound.isPrettyLogging());
        }
    }

    @Test
    void instantiatesAndAppliesAnAnnotatedFeature() {
        AnnotationInterceptors annotations = new AnnotationInterceptors(AnnotatedService.class);
        List<AbstractFeature> features = annotations.getFeatures();
        assertEquals(1, features.size());
        LoggingFeature feature = (LoggingFeature) features.get(0);
        feature.setLimit(2048);
        AbstractBasicInterceptorProvider provider = new AbstractBasicInterceptorProvider() { };
        feature.initialize(provider, null);

        assertEquals(1, provider.getInInterceptors().size());
        assertEquals(1, provider.getOutInterceptors().size());
        assertEquals(1, provider.getInFaultInterceptors().size());
        assertEquals(1, provider.getOutFaultInterceptors().size());
        assertEquals(2048, ((LoggingInInterceptor) provider.getInInterceptors().get(0)).getLimit());
        assertEquals(2048, ((LoggingOutInterceptor) provider.getOutInterceptors().get(0)).getLimit());
    }

    @InInterceptors(classes = LoggingInInterceptor.class)
    @OutInterceptors(interceptors = "org.apache.cxf.interceptor.LoggingOutInterceptor")
    @InFaultInterceptors(interceptors = "org.apache.cxf.interceptor.LoggingInInterceptor")
    @OutFaultInterceptors(classes = LoggingOutInterceptor.class)
    @Features(features = "org.apache.cxf.feature.LoggingFeature")
    public static class AnnotatedService {
    }
}
