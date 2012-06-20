package com.inmobi.messaging.consumer.databus;

import java.io.IOException;

import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.inmobi.messaging.ClientConfig;
import com.inmobi.messaging.consumer.util.TestUtil;

public class TestDatabusConsumerMultipleCollectors 
    extends TestAbstractDatabusConsumer{
  DatabusConsumer testConsumer;

  ClientConfig loadConfig() {
    return ClientConfig
        .loadFromClasspath("messaging-consumer-conf2.properties");
  }

  @BeforeTest
  public void setup() throws Exception {
    consumerName = "c2";
    collectors = new String[] {"collector1", "collector2"};
    dataFiles = new String[] {TestUtil.files[0],
        TestUtil.files[1], TestUtil.files[2], TestUtil.files[3]};
    super.setup(3);
  }

  @Test
  public void testMergeStream() throws Exception {
    ClientConfig config = loadConfig();
    config.set(DatabusConsumerConfig.databusClustersConfig,
        "testcluster1");
    config.set(DatabusConsumerConfig.checkpointDirConfig,
        "/tmp/test/databustest4/checkpoint1");
    assertMessages(config, 1, 2);
  }

  @Test
  public void testDefaultMergeStream() throws Exception {
    ClientConfig config = loadConfig();
    config.set(DatabusConsumerConfig.databusClustersConfig,
        null);
    config.set(DatabusConsumerConfig.checkpointDirConfig,
        "/tmp/test/databustest6/checkpoint1");
    assertMessages(config, 1, 2);
  }

  @Test
  public void testMergeStreamMultipleClusters() throws Exception {
    ClientConfig config = loadConfig();
    config.set(DatabusConsumerConfig.databusClustersConfig,
        "testcluster1,testcluster2");
    config.set(DatabusConsumerConfig.checkpointDirConfig,
        "/tmp/test/databustest6/checkpoint1");
    Throwable th = null;
    try {
      DatabusConsumer consumer = new DatabusConsumer();
      consumer.init(testStream, consumerName, null, config);
    } catch (Exception e) {
      th = e;
    }
    Assert.assertNotNull(th);
    Assert.assertTrue(th instanceof IllegalArgumentException);
  }

  @Test
  public void testLocalStream() throws Exception {
    ClientConfig config = loadConfig();
    config.set(DatabusConsumerConfig.databusClustersConfig,
        "testcluster1");
    config.set(DatabusConsumerConfig.checkpointDirConfig,
        "/tmp/test/databustest4/checkpoint2");
    config.set(DatabusConsumerConfig.databusStreamType,
        StreamType.LOCAL.name());
    assertMessages(config, 1, 2);
  }

  @Test
  public void testLocalStreamMultipleClusters() throws Exception {
    ClientConfig config = loadConfig();
    config.set(DatabusConsumerConfig.databusClustersConfig,
        "testcluster1,testcluster2");
    config.set(DatabusConsumerConfig.checkpointDirConfig,
        "/tmp/test/databustest5/checkpoint1");
    config.set(DatabusConsumerConfig.databusStreamType,
        StreamType.LOCAL.name());
    assertMessages(config, 2, 2);
  }

  @Test
  public void testLocalStreamAllClusters() throws Exception {
    ClientConfig config = loadConfig();
    config.set(DatabusConsumerConfig.databusClustersConfig,
        null);
    config.set(DatabusConsumerConfig.checkpointDirConfig,
        "/tmp/test/databustest5/checkpoint2");
    config.set(DatabusConsumerConfig.databusStreamType,
        StreamType.LOCAL.name());
    assertMessages(config, 3, 2);
  }

  @Test
  public void testCollectorStream() throws Exception {
    ClientConfig config = loadConfig();
    config.set(DatabusConsumerConfig.databusClustersConfig,
        "testcluster1");
    config.set(DatabusConsumerConfig.checkpointDirConfig,
        "/tmp/test/databustest4/checkpoint3");
    config.set(DatabusConsumerConfig.databusStreamType,
        StreamType.COLLECTOR.name());
    assertMessages(config, 1, 2, 4);
  }

  @Test
  public void testCollectorStreamMultipleClusters() throws Exception {
    ClientConfig config = loadConfig();
    config.set(DatabusConsumerConfig.databusClustersConfig,
        "testcluster1,testcluster2");
    config.set(DatabusConsumerConfig.checkpointDirConfig,
        "/tmp/test/databustest5/checkpoint3");
    config.set(DatabusConsumerConfig.databusStreamType,
        StreamType.COLLECTOR.name());
    assertMessages(config, 2, 2, 4);
  }

  @Test
  public void testCollectorStreamAllClusters() throws Exception {
    ClientConfig config = loadConfig();
    config.set(DatabusConsumerConfig.databusClustersConfig,
        null);
    config.set(DatabusConsumerConfig.checkpointDirConfig,
        "/tmp/test/databustest5/checkpoint4");
    config.set(DatabusConsumerConfig.databusStreamType,
        StreamType.COLLECTOR.name());
    assertMessages(config, 3, 2, 4);
  }

  @AfterTest
  public void cleanup() throws IOException {
    super.cleanup();
  }

}
