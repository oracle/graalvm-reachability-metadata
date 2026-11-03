/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_jandex;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.IndexWriter;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Exercises Jandex's index persistence subsystem to cache application type metadata between runs.
public class IndexPersistenceTest {
    @Test
    @Timeout(60)
    public void restoresCachedApplicationIndex() throws IOException {
        Index original = Index.of(InvoiceService.class, PaymentService.class, AuditedService.class);

        ByteArrayOutputStream cache = new ByteArrayOutputStream();
        int bytesWritten = new IndexWriter(cache).write(original);
        Index restored = new IndexReader(new ByteArrayInputStream(cache.toByteArray())).read();

        assertEquals(cache.size(), bytesWritten);
        assertEquals(List.of(
                AuditedService.class.getName(),
                InvoiceService.class.getName(),
                PaymentService.class.getName()),
                restored.getKnownClasses().stream()
                        .map(ClassInfo::name)
                        .map(Object::toString)
                        .sorted()
                        .toList());

        ClassInfo invoiceService = restored.getClassByName(
                DotName.createSimple(InvoiceService.class));
        assertNotNull(invoiceService);
        MethodInfo findById = invoiceService.method("findById", Type.create(Long.class));
        assertNotNull(findById);
        assertEquals("java.lang.String", findById.returnType().name().toString());
        assertEquals(List.of("java.lang.IllegalArgumentException"), findById.exceptions().stream()
                .map(type -> type.name().toString())
                .toList());
    }

    private interface AuditedService {
        String audit();
    }

    private static class PaymentService implements AuditedService {
        @Override
        public String audit() {
            return "payment";
        }
    }

    private static final class InvoiceService extends PaymentService {
        public String findById(Long id) throws IllegalArgumentException {
            return "invoice-" + id;
        }
    }
}
