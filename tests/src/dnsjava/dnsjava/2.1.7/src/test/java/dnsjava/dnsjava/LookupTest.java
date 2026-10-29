/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dnsjava.dnsjava;

import java.net.InetAddress;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.CNAMERecord;
import org.xbill.DNS.Cache;
import org.xbill.DNS.Credibility;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Name;
import org.xbill.DNS.Type;

import static org.assertj.core.api.Assertions.assertThat;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
public class LookupTest {
    @Test
    public void resolvesCachedAddressAndRefreshesAnswersWhenReused() throws Exception {
        Name host = Name.fromString("api.example.");
        ARecord original = new ARecord(host, DClass.IN, 300,
                InetAddress.getByAddress(new byte[]{(byte) 192, 0, 2, 10}));
        Cache cache = new Cache(DClass.IN);
        cache.addRecord(original, Credibility.AUTH_ANSWER, null);

        Lookup lookup = new Lookup(host, Type.A, DClass.IN);
        lookup.setCache(cache);

        assertThat(lookup.run()).containsExactly(original);
        assertThat(lookup.getAnswers()).containsExactly(original);
        assertThat(lookup.getResult()).isEqualTo(Lookup.SUCCESSFUL);
        assertThat(lookup.getErrorString()).isEqualTo("successful");
        assertThat(lookup.getAliases()).isEmpty();

        ARecord updated = new ARecord(host, DClass.IN, 300,
                InetAddress.getByAddress(new byte[]{(byte) 192, 0, 2, 20}));
        cache.clearCache();
        cache.addRecord(updated, Credibility.AUTH_ANSWER, null);

        assertThat(lookup.run()).containsExactly(updated);
        assertThat(lookup.getAnswers()).containsExactly(updated);
        assertThat(lookup.getResult()).isEqualTo(Lookup.SUCCESSFUL);
        assertThat(lookup.getAliases()).isEmpty();
    }

    @Test
    public void expandsSearchPathAndFollowsCachedAlias() throws Exception {
        Name alias = Name.fromString("portal.service.example.");
        Name canonical = Name.fromString("backend.service.example.");
        ARecord address = new ARecord(canonical, DClass.IN, 300,
                InetAddress.getByAddress(new byte[]{(byte) 192, 0, 2, 30}));
        Cache cache = new Cache(DClass.IN);
        cache.addRecord(new CNAMERecord(alias, DClass.IN, 300, canonical),
                Credibility.AUTH_ANSWER, null);
        cache.addRecord(address, Credibility.AUTH_ANSWER, null);

        Lookup lookup = new Lookup("portal", Type.A);
        lookup.setCache(cache);
        lookup.setSearchPath(new String[]{"service.example."});

        assertThat(lookup.run()).containsExactly(address);
        assertThat(lookup.getAnswers()).containsExactly(address);
        assertThat(lookup.getResult()).isEqualTo(Lookup.SUCCESSFUL);
        assertThat(lookup.getAliases()).containsExactly(alias);
        assertThat(lookup.getErrorString()).isEqualTo("successful");
    }
}
