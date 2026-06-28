/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_keycloak.keycloak_client_common_synced;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.PrivilegedExceptionAction;
import java.util.Base64;
import java.util.Date;
import java.util.Set;
import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.kerberos.KerberosTicket;

import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.junit.jupiter.api.Test;
import org.keycloak.common.constants.KerberosConstants;
import org.keycloak.common.util.KerberosSerializationUtils;
import org.keycloak.common.util.KerberosSerializationUtils.KerberosSerializationException;

public class KerberosSerializationUtilsTest {
    @Test
    void rejectsSerializedObjectThatIsNotKerberosTicket() throws IOException {
        String serializedDate = serializeForKerberosInput(new Date(0L));

        assertThatThrownBy(() -> KerberosSerializationUtils.deserializeCredential(serializedDate))
                .isInstanceOf(KerberosSerializationException.class)
                .hasMessageContaining("Deserialized object is not KerberosTicket");
    }

    @Test
    void serializesKerberosTicketExtractedFromSubjectCredential() throws Exception {
        KerberosTicket ticket = newCurrentKerberosTicket();
        Subject subject = new Subject(false, Set.of(ticket.getClient()), Set.of(), Set.of(ticket));
        GSSCredential credential = Subject.doAs(subject, (PrivilegedExceptionAction<GSSCredential>) () -> {
            GSSManager manager = GSSManager.getInstance();
            GSSName name = manager.createName(ticket.getClient().getName(), KerberosConstants.KRB5_NAME_OID);
            return manager.createCredential(name, GSSCredential.DEFAULT_LIFETIME, KerberosConstants.KRB5_OID,
                    GSSCredential.INITIATE_ONLY);
        });

        try {
            String serializedCredential = KerberosSerializationUtils.serializeCredential(ticket, credential);

            assertThat(serializedCredential).isNotBlank();
        } finally {
            credential.dispose();
        }
    }

    private static String serializeForKerberosInput(Object value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(value);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    private static KerberosTicket newCurrentKerberosTicket() {
        String realm = "EXAMPLE.COM";
        KerberosPrincipal client = new KerberosPrincipal("user@" + realm);
        KerberosPrincipal server = new KerberosPrincipal("krbtgt/" + realm + "@" + realm,
                KerberosPrincipal.KRB_NT_SRV_INST);
        Date now = new Date();
        Date endTime = new Date(now.getTime() + 600_000L);
        boolean[] flags = new boolean[32];

        return new KerberosTicket(newTicketEncoding(realm), client, server, new byte[16], 17, flags, now, now,
                endTime, null, null);
    }

    private static byte[] newTicketEncoding(String realm) {
        byte[] serverName = sequence(
                contextSpecific(0, integer(2)),
                contextSpecific(1, sequence(generalString("krbtgt"), generalString(realm))));
        byte[] encryptedData = sequence(
                contextSpecific(0, integer(17)),
                contextSpecific(2, octetString(new byte[] {1})));

        return application(1, sequence(
                contextSpecific(0, integer(5)),
                contextSpecific(1, generalString(realm)),
                contextSpecific(2, serverName),
                contextSpecific(3, encryptedData)));
    }

    private static byte[] application(int tagNumber, byte[] content) {
        return tagged(0x60 + tagNumber, content);
    }

    private static byte[] contextSpecific(int tagNumber, byte[] content) {
        return tagged(0xA0 + tagNumber, content);
    }

    private static byte[] sequence(byte[]... values) {
        return tagged(0x30, concatenate(values));
    }

    private static byte[] integer(int value) {
        return tagged(0x02, new byte[] {(byte) value});
    }

    private static byte[] generalString(String value) {
        return tagged(0x1B, value.getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] octetString(byte[] value) {
        return tagged(0x04, value);
    }

    private static byte[] tagged(int tag, byte[] content) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(tag);
        writeLength(bytes, content.length);
        bytes.writeBytes(content);
        return bytes.toByteArray();
    }

    private static void writeLength(ByteArrayOutputStream bytes, int length) {
        if (length < 128) {
            bytes.write(length);
            return;
        }

        byte[] lengthBytes = integerBytes(length);
        bytes.write(0x80 | lengthBytes.length);
        bytes.writeBytes(lengthBytes);
    }

    private static byte[] integerBytes(int value) {
        int byteCount = (Integer.SIZE - Integer.numberOfLeadingZeros(value) + 7) / 8;
        byte[] bytes = new byte[byteCount];
        for (int index = byteCount - 1; index >= 0; index--) {
            bytes[index] = (byte) value;
            value >>>= 8;
        }
        return bytes;
    }

    private static byte[] concatenate(byte[]... values) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (byte[] value : values) {
            bytes.writeBytes(value);
        }
        return bytes.toByteArray();
    }
}
