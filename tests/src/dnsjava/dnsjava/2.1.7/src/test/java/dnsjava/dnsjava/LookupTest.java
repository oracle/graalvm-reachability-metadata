/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dnsjava.dnsjava;

import java.net.InetAddress;

import org.junit.jupiter.api.Test;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.Cache;
import org.xbill.DNS.Credibility;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.Type;

import static org.assertj.core.api.Assertions.assertThat;

public class LookupTest {
    @Test
    void returnsRecordsFromConfiguredCacheWithoutQueryingResolver() throws Exception {
        Name host = Name.fromString("cached.example.");
        ARecord address = new ARecord(host, DClass.IN, 300, InetAddress.getByAddress(new byte[] {
                (byte) 192, 0, 2, 10
        }));
        Cache cache = new Cache();
        cache.addRecord(address, Credibility.NORMAL, this);

        Lookup lookup = new Lookup(host, Type.A);
        lookup.setCache(cache);

        Record[] answers = lookup.run();

        assertThat(lookup.getResult()).isEqualTo(Lookup.SUCCESSFUL);
        assertThat(answers).containsExactly(address);
        assertThat(lookup.getAnswers()).containsExactly(address);
        assertThat(lookup.getAliases()).isEmpty();
    }
}
