package com.github.kuangcp.websocket.client;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * @author kuangcp on 18-12-19-下午12:10
 */
@Slf4j
public class WebSocketClientHandler extends SimpleChannelInboundHandler<Object> {

  private final WebSocketClientHandshaker handShaker;
  private ChannelPromise handshakeFuture;

  public WebSocketClientHandler(WebSocketClientHandshaker handShaker) {
    this.handShaker = handShaker;
  }

  public ChannelFuture handshakeFuture() {
    return handshakeFuture;
  }

  @Override
  public void handlerAdded(ChannelHandlerContext ctx) {
    handshakeFuture = ctx.newPromise();
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) {
    handShaker.handshake(ctx.channel());
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) {
    log.error("The channel was inactive!");
  }

  @Override
  public void channelRead0(ChannelHandlerContext ctx, Object msg) {
    Channel ch = ctx.channel();
    if (!handShaker.isHandshakeComplete()) {
      try {
        handShaker.finishHandshake(ch, (FullHttpResponse) msg);
        log.info("WebSocket Client was connected");
        handshakeFuture.setSuccess();
      } catch (WebSocketHandshakeException e) {
        log.error("WebSocket Client failed to connect server");
        handshakeFuture.setFailure(e);
      }
      return;
    }

    if (msg instanceof FullHttpResponse) {
      FullHttpResponse response = (FullHttpResponse) msg;
      String exceptionMsg = String.format("Unexpected FullHttpResponse (getStatus=%s,content=%s)",
          response.status(), response.content().toString(CharsetUtil.UTF_8));
      throw new IllegalStateException(exceptionMsg);
    }

    WebSocketFrame frame = (WebSocketFrame) msg;
    if (frame instanceof TextWebSocketFrame) {
      TextWebSocketFrame textFrame = (TextWebSocketFrame) frame;
      log.debug("WebSocket Client received message: {}", textFrame.text());
    } else if (frame instanceof PongWebSocketFrame) {
      log.debug("WebSocket Client received pong");
    } else if (frame instanceof CloseWebSocketFrame) {
      log.debug("WebSocket Client received closing");
      ch.close();
    }
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    log.error(cause.getMessage(), cause);

    if (!handshakeFuture.isDone()) {
      handshakeFuture.setFailure(cause);
    }
    ctx.close();
  }
}
