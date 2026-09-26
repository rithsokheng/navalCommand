package com.battleship.net;

import com.battleship.model.CellStatus;
import com.battleship.model.Coordinate;
import com.battleship.model.Orientation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class NetworkSessionIntegrationTest {

    private NetworkSession hostSession;
    private NetworkSession clientSession;

    @AfterEach
    void tearDown() {
        if (clientSession != null) clientSession.close();
        if (hostSession != null) hostSession.close();
    }

    @Test
    void endToEndHostAndClientCommunication() throws InterruptedException {
        int port = NetUtil.findFreePort();
        Executor directExecutor = Runnable::run;

        CountDownLatch clientConnectedLatch = new CountDownLatch(1);
        CountDownLatch hostReceivedHelloLatch = new CountDownLatch(1);
        CountDownLatch clientReceivedWelcomeLatch = new CountDownLatch(1);
        CountDownLatch clientReceivedFireResultLatch = new CountDownLatch(1);

        AtomicReference<NetMessage> hostReceivedMsg = new AtomicReference<>();
        AtomicReference<NetMessage> clientReceivedMsg = new AtomicReference<>();
        AtomicReference<NetMessage> clientFireResultMsg = new AtomicReference<>();

        // 1. start host
        hostSession = NetworkSession.host(
                port,
                session -> {
                    session.setOnMessage(msg -> {
                        hostReceivedMsg.set(msg);
                        hostReceivedHelloLatch.countDown();
                    });
                },
                err -> fail("Host error: " + err.getMessage()),
                directExecutor
        );

        // 2. connect client
        NetworkSession.connect(
                "127.0.0.1",
                port,
                session -> {
                    clientSession = session;
                    session.setOnMessage(msg -> {
                        if (msg instanceof NetMessage.Welcome) {
                            clientReceivedMsg.set(msg);
                            clientReceivedWelcomeLatch.countDown();
                        } else if (msg instanceof NetMessage.FireResult) {
                            clientFireResultMsg.set(msg);
                            clientReceivedFireResultLatch.countDown();
                        }
                    });
                    clientConnectedLatch.countDown();
                },
                err -> fail("Client connect error: " + err.getMessage()),
                directExecutor
        );

        assertTrue(clientConnectedLatch.await(5, TimeUnit.SECONDS), "Client failed to connect within timeout");
        assertNotNull(clientSession);

        // 3. client sends hello
        clientSession.send(new NetMessage.Hello("4821"));
        assertTrue(hostReceivedHelloLatch.await(5, TimeUnit.SECONDS), "Host failed to receive HELLO");
        assertInstanceOf(NetMessage.Hello.class, hostReceivedMsg.get());
        assertEquals("4821", ((NetMessage.Hello) hostReceivedMsg.get()).code());

        // 4. host sends welcome
        hostSession.send(new NetMessage.Welcome("SKIRMISH"));
        assertTrue(clientReceivedWelcomeLatch.await(5, TimeUnit.SECONDS), "Client failed to receive WELCOME");
        assertInstanceOf(NetMessage.Welcome.class, clientReceivedMsg.get());
        assertEquals("SKIRMISH", ((NetMessage.Welcome) clientReceivedMsg.get()).theater());

        // 5. host sends fire_result
        NetMessage.FireResult fr = new NetMessage.FireResult(
                List.of(new NetMessage.CellResult(new Coordinate(0, 0), CellStatus.HIT)),
                List.of(),
                false
        );
        hostSession.send(fr);
        assertTrue(clientReceivedFireResultLatch.await(5, TimeUnit.SECONDS), "Client failed to receive FIRE_RESULT");
        assertInstanceOf(NetMessage.FireResult.class, clientFireResultMsg.get());
        NetMessage.FireResult receivedFr = (NetMessage.FireResult) clientFireResultMsg.get();
        assertEquals(1, receivedFr.results().size());
        assertEquals(CellStatus.HIT, receivedFr.results().get(0).outcome());
    }
}
