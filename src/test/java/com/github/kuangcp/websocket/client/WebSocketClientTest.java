package com.github.kuangcp.websocket.client;

import com.github.kuangcp.websocket.Hi.Chat;
import com.github.kuangcp.websocket.Hi.Chat.Builder;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import org.junit.Test;

/**
 * @author kuangcp on 3/31/19-12:08 PM
 */
public class WebSocketClientTest {

  @Test
  public void testConnect() throws URISyntaxException {
    String url = String.format("ws://%s:%s", "127.0.0.1", 8082);
    final WebSocketClientHandler handler = new WebSocketClientHandler(
        WebSocketClientHandshakerFactory.newHandshaker(new URI(url), WebSocketVersion.V13,
            null, true, new DefaultHttpHeaders()));

    WebSocketClient client = new WebSocketClient(url, handler);
    Optional<Channel> channel = client.connectSever();

    assert channel.isPresent();

    Builder builder = Chat.newBuilder().setName("first").setMsg("hi");

    client.sendMsg(builder);
    client.closeConnect();
  }
}