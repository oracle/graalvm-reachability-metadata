/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package redis_clients.jedis;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ServerSocket;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import redis.clients.jedis.Jedis;
import redis.embedded.RedisServer;

/// Exercises Jedis Lua scripting to debit a wallet atomically with idempotent payment receipts.
public class WalletDebitScriptTest {
    @Test
    @Timeout(60)
    public void replaysPaymentReceiptsWithoutDebitingTheWalletAgain() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        RedisServer server = RedisServer.newRedisServer()
                .bind("127.0.0.1")
                .port(port)
                .setting("save \"\"")
                .setting("appendonly no")
                .build();
        try {
            server.start();
            try (Jedis payments = new Jedis("127.0.0.1", port, 10000, 10000);
                    Jedis retryWorker = new Jedis("127.0.0.1", port, 10000, 10000)) {
                String balanceKey = "wallet:{customer-42}:balance";
                String receiptsKey = "wallet:{customer-42}:receipts";
                List<String> keys = List.of(balanceKey, receiptsKey);
                payments.set(balanceKey, "100");
                String debitScript = """
                        local receipt = redis.call('HGET', KEYS[2], ARGV[1])
                        if receipt then
                            return {'replayed', tonumber(receipt)}
                        end
                        local balance = tonumber(redis.call('GET', KEYS[1]))
                        local amount = tonumber(ARGV[2])
                        if balance < amount then
                            return {'insufficient', balance}
                        end
                        local remaining = redis.call('DECRBY', KEYS[1], amount)
                        redis.call('HSET', KEYS[2], ARGV[1], remaining)
                        return {'debited', remaining}
                        """;
                String scriptSha = payments.scriptLoad(debitScript);

                assertThat(payments.evalsha(scriptSha, keys, List.of("payment-101", "25")))
                        .isEqualTo(List.of("debited", 75L));
                assertThat(retryWorker.evalsha(scriptSha, keys, List.of("payment-101", "25")))
                        .isEqualTo(List.of("replayed", 75L));
                assertThat(retryWorker.get(balanceKey)).isEqualTo("75");

                // A later payment changes the balance but not the first payment's saved result.
                assertThat(payments.evalsha(scriptSha, keys, List.of("payment-102", "10")))
                        .isEqualTo(List.of("debited", 65L));
                assertThat(retryWorker.evalsha(scriptSha, keys, List.of("payment-101", "25")))
                        .isEqualTo(List.of("replayed", 75L));

                // A rejected payment must neither overdraw the wallet nor create a receipt.
                assertThat(payments.evalsha(scriptSha, keys, List.of("payment-103", "100")))
                        .isEqualTo(List.of("insufficient", 65L));
                assertThat(retryWorker.get(balanceKey)).isEqualTo("65");
                assertThat(retryWorker.hgetAll(receiptsKey))
                        .isEqualTo(Map.of("payment-101", "75", "payment-102", "65"));
            }
        } finally {
            server.stop();
        }
    }
}
