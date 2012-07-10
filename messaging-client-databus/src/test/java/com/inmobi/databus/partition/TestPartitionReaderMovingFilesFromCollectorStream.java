package com.inmobi.databus.partition;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.inmobi.databus.Cluster;
import com.inmobi.databus.partition.CollectorReader;
import com.inmobi.databus.partition.PartitionId;
import com.inmobi.databus.partition.PartitionReader;
import com.inmobi.databus.readers.CollectorStreamReader;
import com.inmobi.databus.readers.LocalStreamCollectorReader;
import com.inmobi.messaging.consumer.databus.DataEncodingType;
import com.inmobi.messaging.consumer.databus.QueueEntry;
import com.inmobi.messaging.consumer.util.TestUtil;

public class TestPartitionReaderMovingFilesFromCollectorStream {
  private static final String testStream = "testclient";

  private static final String collectorName = "collector1";
  private static final String clusterName = "testCluster";
  private PartitionId partitionId = new PartitionId(clusterName, collectorName);

  private LinkedBlockingQueue<QueueEntry> buffer = 
      new LinkedBlockingQueue<QueueEntry>(149);
  private Cluster cluster;
  private Path collectorDir;
  private PartitionReader preader;
  private FileSystem fs;
  
  private String[] files = new String[] {TestUtil.files[0],
      TestUtil.files[2], TestUtil.files[3], TestUtil.files[4],
      TestUtil.files[6], TestUtil.files[8], TestUtil.files[9],
      TestUtil.files[10], TestUtil.files[11]};

  private String[] emptyfiles = new String[] {TestUtil.files[1],
      TestUtil.files[5], TestUtil.files[7]};

  @BeforeTest
  public void setup() throws Exception {
    // setup cluster
    cluster = TestUtil.setupLocalCluster(this.getClass().getSimpleName(),
        testStream, partitionId, files, emptyfiles, 1);
    collectorDir = new Path(new Path(cluster.getDataDir(), testStream),
        collectorName);
    fs = FileSystem.get(cluster.getHadoopConf());
  }

  @AfterTest
  public void cleanup() throws IOException {
    TestUtil.cleanupCluster(cluster);
  }

  @Test
  public void testCollectorFileMoved() throws Exception {
    preader = new PartitionReader(partitionId, null, cluster, buffer,
        testStream, CollectorStreamReader.getDateFromCollectorFile(files[0]),
        10, 1000, false, DataEncodingType.BASE64);
    preader.init();
    Assert.assertTrue(buffer.isEmpty());
    Assert.assertEquals(preader.getReader().getClass().getName(),
        CollectorReader.class.getName());
    Assert.assertEquals(((CollectorReader)preader.getReader()).
        getReader().getClass().getName(),
        LocalStreamCollectorReader.class.getName());

    preader.start();
    while (buffer.remainingCapacity() > 0) {
      Thread.sleep(10);
    }
    Assert.assertEquals(((CollectorReader)preader.getReader())
        .getReader().getClass().getName(),
        CollectorStreamReader.class.getName());

    // Move collector files files[1] and files[2]
    fs.delete(new Path(collectorDir, emptyfiles[0]), true);
    Path movedPath = TestUtil.moveFileToStreamLocal(fs, testStream,
        collectorName, cluster, collectorDir, files[1]);
    TestUtil.moveFileToStreamLocal(fs, testStream, collectorName, cluster,
        collectorDir, files[2]);
    fs.delete(movedPath, true);

    TestUtil.assertBuffer(LocalStreamCollectorReader.getDatabusStreamFile(
        collectorName, files[0]), 1, 0, 100, partitionId, buffer);
    TestUtil.assertBuffer(CollectorStreamReader.getCollectorFile(files[1]), 2,
        0, 50, partitionId, buffer);

    while (buffer.remainingCapacity() > 0) {
      Thread.sleep(10);
    }
    Assert.assertEquals(((CollectorReader)preader.getReader())
        .getReader().getClass().getName(),
        LocalStreamCollectorReader.class.getName());
    TestUtil.assertBuffer(CollectorStreamReader.getCollectorFile(files[1]), 2,
        50, 50, partitionId, buffer);
    TestUtil.assertBuffer(LocalStreamCollectorReader.getDatabusStreamFile(
        collectorName, files[2]), 3, 0, 100, partitionId, buffer);

    while (buffer.remainingCapacity() > 0) {
      Thread.sleep(10);
    }
    Assert.assertEquals(((CollectorReader)preader.getReader())
        .getReader().getClass().getName(),
        CollectorStreamReader.class.getName());

    // Copy collector files files [3] files[4] and files[5]
    TestUtil.moveFileToStreamLocal(fs, testStream, collectorName, cluster,
        collectorDir, files[3]);
    TestUtil.moveFileToStreamLocal(fs, testStream, collectorName, cluster,
        collectorDir, files[4]);
    TestUtil.moveFileToStreamLocal(fs, testStream, collectorName, cluster,
        collectorDir, files[5]);
    TestUtil.copyFileToStreamLocal(fs, testStream, collectorName, cluster,
        collectorDir, files[6]);
    TestUtil.copyFileToStreamLocal(fs, testStream, collectorName, cluster,
        collectorDir, files[7]);
    fs.delete(new Path(collectorDir, emptyfiles[1]), true);
    fs.delete(new Path(collectorDir, emptyfiles[2]), true);

    TestUtil.assertBuffer(CollectorStreamReader.getCollectorFile(files[3]),
        4, 0, 100, partitionId, buffer);
    TestUtil.assertBuffer(CollectorStreamReader.getCollectorFile(files[4]),
        5, 0, 50, partitionId, buffer);
    while (buffer.remainingCapacity() > 0) {
      Thread.sleep(10);
    }
    Assert.assertEquals(((CollectorReader)preader.getReader()).
        getReader().getClass().getName(),
        LocalStreamCollectorReader.class.getName());
    TestUtil.assertBuffer(CollectorStreamReader.getCollectorFile(files[4]),
        5, 50, 50, partitionId, buffer);
    TestUtil.assertBuffer(LocalStreamCollectorReader.getDatabusStreamFile(
        collectorName, files[5]), 6, 0, 100, partitionId, buffer); 
    TestUtil.assertBuffer(LocalStreamCollectorReader.getDatabusStreamFile(
        collectorName, files[6]), 7, 0, 100, partitionId, buffer); 
    TestUtil.assertBuffer(LocalStreamCollectorReader.getDatabusStreamFile(
        collectorName, files[7]), 8, 0, 100, partitionId, buffer); 
    TestUtil.assertBuffer(CollectorStreamReader.getCollectorFile(files[8]),
        9, 0, 100, partitionId, buffer);
    Assert.assertTrue(buffer.isEmpty());
    preader.close();

  }
}
