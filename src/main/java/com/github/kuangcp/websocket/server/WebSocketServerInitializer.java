package com.github.kuangcp.websocket.server;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.stream.ChunkedWriteHandler;

/**
 * @author kuangcp on 3/31/19-12:02 PM
 */
public class WebSocketServerInitializer extends ChannelInitializer<SocketChannel> {

  private static final String WEBSOCKET_PATH = "/websocket";

  private final SslContext sslCtx;

  public WebSocketServerInitializer(SslContext sslCtx) {
    this.sslCtx = sslCtx;
  }

  @Override
  public void initChannel(SocketChannel ch) throws Exception {
    ChannelPipeline pipeline = ch.pipeline();
    if (sslCtx != null) {
      pipeline.addLast(sslCtx.newHandler(ch.alloc()));
    }
    pipeline.addLast(new HttpServerCodec());
    pipeline.addLast(new HttpObjectAggregator(65536));
    pipeline.addLast(new WebSocketServerCompressionHandler());
    pipeline.addLast(new WebSocketServerProtocolHandler(WEBSOCKET_PATH, null, true));
    pipeline.addLast("http-chunked", new ChunkedWriteHandler());

    pipeline.addLast("handler", new WebSocketServerHandler());

    pipeline.addLast(new WebSocketFrameHandler());
  }
}
