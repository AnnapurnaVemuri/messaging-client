package com.inmobi.messaging;

import static org.testng.Assert.assertNotNull;

import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;

import random.pkg.NtMultiServer;

import com.inmobi.messaging.netty.ScribeMessagePublisher;

public class TestServerStarter {
  private static NtMultiServer server;

  @BeforeSuite
  public void setUp() {
    safeInit();
  }

  @AfterSuite
  public void tearDown() {
    server.stop();
  }

  public static NtMultiServer getServer() {
    safeInit();
    assertNotNull(server);
    return server;
  }

  private static synchronized void safeInit() {
    if (server == null) {
      server = new NtMultiServer();
    }
  }

  public static final int port = 7912;

  public static ScribeMessagePublisher createPublisher(int port, 
      int timeout) {
    ScribeMessagePublisher pub = new ScribeMessagePublisher();
    pub.init("localhost", port, 5, timeout);
    return pub;
  }

  public static ScribeMessagePublisher createPublisher() {
    return createPublisher(port, 5);
  }

}
