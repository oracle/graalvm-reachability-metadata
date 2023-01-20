/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.stub.StreamObserver;

class GrpcTests {

    @Test
    void test() throws Exception {
        Server server = Grpc.newServerBuilderForPort(0, InsecureServerCredentials.create())
                .addService(new SimpleService()).build().start();
        assertEquals(1, server.getListenSockets().size());
        var channel = Grpc.newChannelBuilderForAddress("0.0.0.0", server.getPort(), InsecureChannelCredentials.create()).build();
        var stub = SimpleGrpc.newBlockingStub(channel);
        HelloReply response = stub.sayHello(HelloRequest.newBuilder().setName("Alien").build());
        assertEquals("Hello Alien World", response.getMessage());
        channel.shutdownNow();
        server.shutdownNow();
        channel.awaitTermination(5, TimeUnit.SECONDS);
        server.awaitTermination(5, TimeUnit.SECONDS);
    }
}

class SimpleService extends SimpleGrpc.SimpleImplBase {
    @Override
    public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        HelloReply reply = HelloReply.newBuilder()
                .setMessage("Hello " + request.getName() + " World")
                .build();
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }
}
