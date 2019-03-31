package com.github.kuangcp.websocket.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

/**
 * @author kuangcp on 3/31/19-1:32 PM
 */
@Slf4j
public class WebSocketServerHandler extends SimpleChannelInboundHandler<Object> {

  private WebSocketServerHandshaker handShaker;

  public static final String KEY_URI = ".URI";

  @Override
  public void channelActive(ChannelHandlerContext ctx) throws Exception {
    super.channelActive(ctx);

    log.info("online : sessionId={}, remoteAddress={} ",
        ctx.channel().id(), ctx.channel().remoteAddress());
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    ctx.channel().close();
    ctx.close();
    super.channelInactive(ctx);
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
    if (msg instanceof FullHttpRequest) {
      // Http
      handleHttpRequest(ctx, ((FullHttpRequest) msg));
    } else if (msg instanceof WebSocketFrame) {
      // WebSocket
      handlerWebSocketFrame2(ctx, (WebSocketFrame) msg);
    }
  }

  @Override
  public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
    ctx.flush();
  }

  /**
   * 处理 WebSocket 请求
   */
  private void handlerWebSocketFrame2(ChannelHandlerContext ctx, WebSocketFrame frame) {
    if (frame instanceof CloseWebSocketFrame) {
      // 关闭
      handShaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
      return;
    }
    if (frame instanceof PingWebSocketFrame) {
      // ping
      ctx.channel().write(new PongWebSocketFrame(frame.content().retain()));
      return;
    }
    if (frame instanceof PongWebSocketFrame) {
      // pong
      return;
    }
    if (frame instanceof TextWebSocketFrame) {
      // 文本协议
      handleTextWebSocketFrame(ctx, (TextWebSocketFrame) frame);
      return;
    }
    if (frame instanceof BinaryWebSocketFrame) {
      // 二进制协议
      handleBinaryWebSocketFrame(ctx, (BinaryWebSocketFrame) frame);
      return;
    }

    ctx.channel().writeAndFlush(new TextWebSocketFrame(frame.getClass().getName() +
        " be unsupported"));
    log.warn("unsupported WebSocketFrame.class=", frame.getClass().getName());
  }

  private void handleTextWebSocketFrame(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
    final String text = frame.text();

    log.info("receive msg: text={}", text);

    MDC.clear();
  }

  /**
   * 处理 BinaryWebSocketFrame
   */
  private void handleBinaryWebSocketFrame(ChannelHandlerContext ctx, BinaryWebSocketFrame frame) {
    // 解析出 ClientCmdData
    byte[] bytes = new byte[frame.content().readableBytes()];
    frame.content().readBytes(bytes);

    log.info("receive msg: frame={}", frame);
  }

  /**
   * 处理 HTTP 请求
   */
  private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
    if (!req.decoderResult().isSuccess() || (!"websocket".equals(req.headers().get("Upgrade")))) {
      // HTTP解码失败 或者 不是 websocket
      sendHttpResponse(ctx, req,
          new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST));
      return;
    }

    // 保存一下 URI, 方便后面使用
    String uri = req.uri();
    AttributeKey<String> attr = AttributeKey.valueOf(KEY_URI);
    ctx.channel().attr(attr).set(uri);

    // 构造握手响应返回
    String webSocketURL = String.format("ws://%s%s", req.headers().get(HttpHeaderNames.HOST), uri);
    WebSocketServerHandshakerFactory wsFactory =
        new WebSocketServerHandshakerFactory(webSocketURL, null, false);
    handShaker = wsFactory.newHandshaker(req);
    if (handShaker == null) {
      WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel());
    } else {
      handShaker.handshake(ctx.channel(), req);
    }
  }

  /**
   * 回写给客户端
   */
  private static void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest req,
      DefaultFullHttpResponse res) {
    // 返回应答给客户端
    if (res.status().code() != 200) {
      ByteBuf buf = Unpooled.copiedBuffer(res.status().toString(), CharsetUtil.UTF_8);
      res.content().writeBytes(buf);
      buf.release();
    }

    // 如果是非Keep-Alive，关闭连接
    ChannelFuture f = ctx.channel().writeAndFlush(res);
    if (!HttpUtil.isKeepAlive(req) || res.status().code() != 200) {
      log.debug("玩家已经断线");
      f.addListener(ChannelFutureListener.CLOSE);
    }

  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    ctx.channel().close();
    ctx.close();

  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
    // TODO: kick off idle user only when in production; we'd like to keep the connection in development
    if (evt instanceof IdleStateEvent && ((IdleStateEvent) evt).state() == IdleState.READER_IDLE) {
      log.info(
          "空闲超时: id=" + ctx.channel().id() + ", remoteAddress=" + ctx.channel().remoteAddress());
//      ProtocolSystem.systemHandler.idle(ctx);
    }

    super.userEventTriggered(ctx, evt);
  }
}

