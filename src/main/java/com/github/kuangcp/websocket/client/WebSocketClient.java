package com.github.kuangcp.websocket.client;

import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.GeneratedMessageV3;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.net.ssl.SSLException;
import lombok.extern.slf4j.Slf4j;

/**
 * @author kuangcp on 18-12-19-下午12:05
 */
@Slf4j
public class WebSocketClient {

  private String clientId;
  private URI uri;
  private SimpleChannelInboundHandler msgHandler;
  private EventLoopGroup group;
  private Channel channel;

  public WebSocketClient(String url, SimpleChannelInboundHandler msgHandler)
      throws URISyntaxException {
    this.clientId = UUID.randomUUID().toString();
    this.uri = new URI(url);
    this.msgHandler = msgHandler;
    this.group = new NioEventLoopGroup();
  }

  public WebSocketClient(String clientId, String url, SimpleChannelInboundHandler msgHandler)
      throws URISyntaxException {
    this.clientId = clientId;
    this.uri = new URI(url);
    this.msgHandler = msgHandler;
    this.group = new NioEventLoopGroup();
  }


  public Optional<Channel> connectSever() {
    try {
      String scheme = uri.getScheme() == null ? "ws" : uri.getScheme();
      final String host = uri.getHost() == null ? "127.0.0.1" : uri.getHost();
      final int port = getPort(uri, scheme);
      final SslContext sslCtx = getSslContext(scheme);

      if (!"ws".equalsIgnoreCase(scheme) && !"wss".equalsIgnoreCase(scheme)) {
        log.error("clientId={}: Only WS(S) is supported.", clientId);
        return Optional.empty();
      }

      // Connect with V13 (RFC 6455 aka HyBi-17). You can change it to V08 or V00.
      // If you change it to V00, ping is not supported and remember to change
      // HttpResponseDecoder to WebSocketHttpResponseDecoder in the pipeline.
      final WebSocketClientHandler handler = new WebSocketClientHandler(
          WebSocketClientHandshakerFactory.newHandshaker(uri, WebSocketVersion.V13,
              null, true, new DefaultHttpHeaders()));

      Bootstrap b = new Bootstrap()
          .group(group)
          .channel(NioSocketChannel.class)
          .handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
              ChannelPipeline p = ch.pipeline();
              if (sslCtx != null) {
                p.addLast(sslCtx.newHandler(ch.alloc(), host, port));
              }

              p.addLast(new HttpClientCodec(),
                  new HttpObjectAggregator(8192),
                  WebSocketClientCompressionHandler.INSTANCE, msgHandler, handler);
            }
          });

      Channel ch = b.connect(uri.getHost(), port).sync().channel();
      handler.handshakeFuture().sync();

      this.channel = ch;
      return Optional.of(ch);
    } catch (SSLException | InterruptedException e) {
      log.error("clientId={}: {} {}", clientId, e.getMessage(), e);
      return Optional.empty();
    }
  }

  public void closeConnect() {
    log.info("clientId={}: shutdown", clientId);
    group.shutdownGracefully();
  }

  public boolean hasConnected() {
    return Objects.nonNull(channel);
  }

  public void sendMsg(GeneratedMessage.Builder msgBuilder) {
    sendMsg(msgBuilder.build().toByteArray());
  }

  public void sendMsg(GeneratedMessageV3.Builder msgBuilder) {
    sendMsg(msgBuilder.build().toByteArray());
  }

  private void sendMsg(byte[] data) {
    if (!hasConnected()) {
      log.error("clientId={}: channel not establish", clientId);
      return;
    }

    ByteBuf byteBuf = Unpooled.copiedBuffer(data);
    BinaryWebSocketFrame frame = new BinaryWebSocketFrame(byteBuf);
    this.channel.writeAndFlush(frame);
  }

  private SslContext getSslContext(String scheme) throws SSLException {
    boolean ssl = "wss".equalsIgnoreCase(scheme);
    SslContext sslCtx;
    if (ssl) {
      sslCtx = SslContextBuilder.forClient()
          .trustManager(InsecureTrustManagerFactory.INSTANCE).build();
    } else {
      sslCtx = null;
    }
    return sslCtx;
  }

  private int getPort(URI uri, String scheme) {
    int port;
    if (uri.getPort() == -1) {
      if ("ws".equalsIgnoreCase(scheme)) {
        port = 80;
      } else if ("wss".equalsIgnoreCase(scheme)) {
        port = 443;
      } else {
        port = -1;
      }
    } else {
      port = uri.getPort();
    }
    return port;
  }

}
