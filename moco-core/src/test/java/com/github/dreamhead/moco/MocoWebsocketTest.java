package com.github.dreamhead.moco;

import org.junit.Before;
import org.junit.Test;

import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.PongMessage;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.github.dreamhead.moco.Moco.binary;
import static com.github.dreamhead.moco.Moco.by;
import static com.github.dreamhead.moco.Moco.group;
import static com.github.dreamhead.moco.Moco.join;
import static com.github.dreamhead.moco.Moco.text;
import static com.github.dreamhead.moco.Moco.with;
import static com.github.dreamhead.moco.MocoWebSockets.broadcast;
import static com.github.dreamhead.moco.Runner.running;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class MocoWebsocketTest extends AbstractMocoHttpTest {
    private WebSocketServer webSocketServer;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        webSocketServer = server.websocket("/ws");
    }

    @Test
    public void should_connect() throws Exception {
        webSocketServer.connected(text("hello"));

        running(server, () -> {
            final Endpoint endpoint = new Endpoint(new URI("ws://localhost:12306/ws/"));
            assertThat(endpoint.getMessageAsText(), is("hello"));
        });
    }

    @Test
    public void should_connect_with_text() throws Exception {
        webSocketServer.connected("hello");

        running(server, () -> {
            final Endpoint endpoint = new Endpoint(new URI("ws://localhost:12306/ws/"));
            assertThat(endpoint.getMessageAsText(), is("hello"));
        });
    }

    @Test
    public void should_response_based_on_request() throws Exception {
        webSocketServer.request(by("foo")).response("bar");

        running(server, () -> {
            final Endpoint endpoint = new Endpoint(new URI("ws://localhost:12306/ws/"));
            endpoint.sendTextMessage("foo");
            assertThat(endpoint.getMessageAsText(), is("bar"));
        });
    }

    @Test
    public void should_response_any_response() throws Exception {
        webSocketServer.request(by("foo")).response("bar");
        webSocketServer.response("any");

        running(server, () -> {
            final Endpoint endpoint = new Endpoint(new URI("ws://localhost:12306/ws/"));
            endpoint.sendTextMessage("blah");
            assertThat(endpoint.getMessageAsText(), is("any"));
        });
    }

    @Test
    public void should_binary_response_based_on_binary_request() throws Exception {
        webSocketServer.request(by(binary(new byte[] {1, 2, 3}))).response(binary(new byte[] {4, 5, 6}));

        running(server, () -> {
            final Endpoint endpoint = new Endpoint(new URI("ws://localhost:12306/ws/"));
            endpoint.sendBinaryMessage(new byte[] {1, 2, 3});
            assertThat(endpoint.getMessage(), is(new byte[] {4, 5, 6}));
        });
    }

    @Test
    public void should_pong_based_on_ping() throws Exception {
        webSocketServer.request(by(binary(new byte[] {1, 2, 3}))).response(binary(new byte[] {4, 5, 6}));
        webSocketServer.ping("hello").pong("world");

        running(server, () -> {
            final Endpoint endpoint = new Endpoint(new URI("ws://localhost:12306/ws/"));
            endpoint.ping("hello");
            assertThat(endpoint.getMessage(), is("world".getBytes()));
        });
    }

    @Test
    public void should_pong_based_on_ping_resource() throws Exception {
        webSocketServer.request(by(binary(new byte[] {1, 2, 3}))).response(binary(new byte[] {4, 5, 6}));
        webSocketServer.ping(text("hello")).pong(text("world"));

        running(server, () -> {
            final Endpoint endpoint = new Endpoint(new URI("ws://localhost:12306/ws/"));
            endpoint.ping("hello");
            assertThat(endpoint.getMessage(), is("world".getBytes()));
        });
    }

    @Test
    public void should_pong_based_on_ping_matcher() throws Exception {
        webSocketServer.request(by(binary(new byte[] {1, 2, 3}))).response(binary(new byte[] {4, 5, 6}));
        webSocketServer.ping(by("hello")).pong("world");

        running(server, () -> {
            final Endpoint endpoint = new Endpoint(new URI("ws://localhost:12306/ws/"));
            endpoint.ping("hello");
            assertThat(endpoint.getMessage(), is("world".getBytes()));
        });
    }

    @Test
    public void should_pong_with_response_handler_based_on_ping() throws Exception {
        webSocketServer.request(by(binary(new byte[] {1, 2, 3}))).response(binary(new byte[] {4, 5, 6}));
        webSocketServer.ping("hello").pong(with(text("world")));

        running(server, () -> {
            final Endpoint endpoint = new Endpoint(new URI("ws://localhost:12306/ws/"));
            endpoint.ping("hello");
            assertThat(endpoint.getMessage(), is("world".getBytes()));
        });
    }

    @Test
    public void should_broadcast() throws Exception {
        webSocketServer.request(by("foo")).response(broadcast("bar"));
        running(server, () -> {
            final Endpoint endpoint = new Endpoint(new URI("ws://localhost:12306/ws/"));
            final Endpoint endpoint2 = new Endpoint(new URI("ws://localhost:12306/ws/"));
            endpoint.sendTextMessage("foo");

            assertThat(endpoint.getMessage(), is("bar".getBytes()));
            assertThat(endpoint2.getMessage(), is("bar".getBytes()));
        });
    }

    @Test
    public void should_broadcast_with_resource() throws Exception {
        webSocketServer.request(by("foo")).response(broadcast(text("bar")));
        running(server, () -> {
            final Endpoint endpoint = new Endpoint(new URI("ws://localhost:12306/ws/"));
            final Endpoint endpoint2 = new Endpoint(new URI("ws://localhost:12306/ws/"));
            endpoint.sendTextMessage("foo");

            assertThat(endpoint.getMessage(), is("bar".getBytes()));
            assertThat(endpoint2.getMessage(), is("bar".getBytes()));
        });
    }

    @Test
    public void should_broadcast_with_group() throws Exception {
        webSocketServer.request(by("subscribe")).response(with("subscribed"), join(group("foo")));
        webSocketServer.request(by("foo")).response(broadcast(text("foo"), group("foo")));
        running(server, () -> {
            final Endpoint endpoint = new Endpoint(new URI("ws://localhost:12306/ws/"));
            endpoint.sendTextMessage("subscribe");
            assertThat(endpoint.getMessage(), is("subscribed".getBytes()));

            endpoint.sendTextMessage("foo");
            assertThat(endpoint.getMessage(), is("foo".getBytes()));
        });
    }

    @ClientEndpoint
    public static class Endpoint {
        private Session userSession;
        private CompletableFuture<byte[]> message;

        public Endpoint(final URI uri) {
            try {
                WebSocketContainer container = ContainerProvider.getWebSocketContainer();
                container.connectToServer(this, uri);
                this.message = new CompletableFuture<>();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @OnOpen
        public void onOpen(final Session userSession) {
            this.userSession = userSession;
        }

        @OnClose
        public void onClose(final Session userSession, final CloseReason reason) {
            this.userSession = null;
        }

        @OnMessage
        public void onMessage(final byte[] message) {
            this.message.complete(message);
        }

        @OnMessage
        public void onPong(final PongMessage message) {
            this.message.complete(message.getApplicationData().array());
        }

        public void sendTextMessage(final String message) {
            this.message = new CompletableFuture<>();
            this.userSession.getAsyncRemote().sendText(message);
        }

        public void sendBinaryMessage(final byte[] message) {
            this.message = new CompletableFuture<>();
            ByteBuffer buffer = ByteBuffer.wrap(message);
            this.userSession.getAsyncRemote().sendBinary(buffer);
        }

        public String getMessageAsText() {
            return new String(getMessage());
        }

        private byte[] getMessage() {
            try {
                return message.get(2, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException e) {
                return new byte[0];
            } catch (TimeoutException e) {
                throw new IllegalStateException("No message found", e);
            }
        }

        public void ping(final String message) {
            ByteBuffer buffer = ByteBuffer.wrap(message.getBytes());
            try {
                this.userSession.getAsyncRemote().sendPing(buffer);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
