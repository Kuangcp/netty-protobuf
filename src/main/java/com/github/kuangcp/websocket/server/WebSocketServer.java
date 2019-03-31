package com.github.kuangcp.websocket.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;

/**
 * @author kuangcp on 18-12-19-下午4:14
 */
public final class WebSocketServer {

  public void startup(boolean ssl, int port) throws Exception {
    // Configure SSL.
    final SslContext sslCtx;
    if (ssl) {
      SelfSignedCertificate ssc = new SelfSignedCertificate();
      sslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
    } else {
      sslCtx = null;
    }

    EventLoopGroup bossGroup = new NioEventLoopGroup(1);
    EventLoopGroup workerGroup = new NioEventLoopGroup();
    try {
      ServerBootstrap b = new ServerBootstrap();
      b.group(bossGroup, workerGroup)
          .channel(NioServerSocketChannel.class)
          .handler(new LoggingHandler(LogLevel.INFO))
          .childHandler(new WebSocketServerInitializer(sslCtx));

      Channel ch = b.bind(port).sync().channel();

      System.out.println("Open your web browser and navigate to " +
          (ssl ? "https" : "http") + "://127.0.0.1:" + port + '/');

      ch.closeFuture().sync();
    } finally {
      bossGroup.shutdownGracefully();
      workerGroup.shutdownGracefully();
    }
  }
}
