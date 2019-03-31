package com.github.kuangcp.websocket.server;

import org.junit.Test;

/**
 * @author kuangcp on 3/31/19-12:11 PM
 */
public class WebSocketServerTest {

  private WebSocketServer webSocketServer = new WebSocketServer();

  @Test
  public void testStartup() throws Exception {
    webSocketServer.startup(false, 8082);
  }
}