package com.inmobi.databus.readers;

import java.io.IOException;

import org.apache.commons.codec.binary.Base64;
import org.apache.hadoop.fs.Path;
import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.inmobi.databus.Cluster;
import com.inmobi.databus.partition.PartitionCheckpoint;
import com.inmobi.databus.partition.PartitionId;
import com.inmobi.messaging.consumer.util.MessageUtil;
import com.inmobi.messaging.consumer.util.TestUtil;

public class TestLocalStreamCollectorReader {
  private static final String testStream = "testclient";

  private static final String collectorName = "collector1";
  private static final String clusterName = "testCluster";
  private PartitionId partitionId = new PartitionId(clusterName, collectorName);
  private LocalStreamCollectorReader lreader;
  private Cluster cluster;
  private String[] files = new String[] {TestUtil.files[1], TestUtil.files[3],
      TestUtil.files[5]};
  private Path[] databusFiles = new Path[3];

  @BeforeTest
  public void setup() throws Exception {
    // initialize config
    cluster = TestUtil.setupLocalCluster(this.getClass().getSimpleName(),
        testStream, partitionId, files, null, databusFiles, 3);

  }

  @AfterTest
  public void cleanup() throws IOException {
    TestUtil.cleanupCluster(cluster);
  }

  @Test
  public void testInitialize() throws Exception {
    // Read from start
    lreader = new LocalStreamCollectorReader(partitionId, cluster, testStream);
    lreader.build(CollectorStreamReader.getDateFromCollectorFile(files[0]));

    lreader.initFromStart();
    Assert.assertEquals(lreader.getCurrentFile(),
        databusFiles[0]);

    // Read from checkpoint with collector file name
    lreader.initializeCurrentFile(new PartitionCheckpoint(files[1], 20));
    Assert.assertNull(lreader.getCurrentFile());

    // Read from checkpoint with local stream file name
    lreader.initializeCurrentFile(new PartitionCheckpoint(
        LocalStreamCollectorReader.getDatabusStreamFileName(collectorName,
            files[1]), 20));
    Assert.assertEquals(lreader.getCurrentFile(),
        databusFiles[1]);

    // Read from checkpoint with local stream file name, which does not exist
    lreader.initializeCurrentFile(new PartitionCheckpoint(
        LocalStreamCollectorReader.getDatabusStreamFileName(collectorName,
            TestUtil.files[0]), 20));
    Assert.assertEquals(lreader.getCurrentFile(),
        databusFiles[0]);

    // Read from checkpoint with local stream file name, which does not exist
    // with file timestamp after the stream
    lreader.initializeCurrentFile(new PartitionCheckpoint(
        LocalStreamCollectorReader.getDatabusStreamFileName(collectorName,
            TestUtil.files[7]), 20));
    Assert.assertNull(lreader.getCurrentFile());  

    // Read from checkpoint with local stream file name, which does not exist
    // with file timestamp within the stream
    lreader.initializeCurrentFile(new PartitionCheckpoint(
        LocalStreamCollectorReader.getDatabusStreamFileName(collectorName,
            TestUtil.files[4]), 20));
    Assert.assertNull(lreader.getCurrentFile());  

    //Read from startTime in local stream directory 
    lreader.initializeCurrentFile(
        CollectorStreamReader.getDateFromCollectorFile(files[1]));
    Assert.assertEquals(lreader.getCurrentFile(),
        databusFiles[1]);

    //Read from startTime in local stream directory, before the stream
    lreader.initializeCurrentFile(
        CollectorStreamReader.getDateFromCollectorFile(TestUtil.files[0]));
    Assert.assertEquals(lreader.getCurrentFile(),
        databusFiles[0]);

    //Read from startTime in local stream directory, within the stream
    lreader.initializeCurrentFile(
        CollectorStreamReader.getDateFromCollectorFile(TestUtil.files[2]));
    Assert.assertEquals(lreader.getCurrentFile(),
        databusFiles[1]);

    //Read from startTime in local stream directory, within the stream
    lreader.initializeCurrentFile(
        CollectorStreamReader.getDateFromCollectorFile(TestUtil.files[4]));
    Assert.assertEquals(lreader.getCurrentFile(),
        databusFiles[2]);

    //Read from startTime in collector dir
    lreader.initializeCurrentFile(
        CollectorStreamReader.getDateFromCollectorFile(TestUtil.files[7]));
    Assert.assertNull(lreader.getCurrentFile());  

  }

  private void readFile(int fileNum, int startIndex) throws Exception {
    int fileIndex = fileNum * 100 ;
    for (int i = startIndex; i < 100; i++) {
      String line = lreader.readLine();
      Assert.assertNotNull(line);
      Assert.assertEquals(new String(Base64.decodeBase64(line)),
          MessageUtil.constructMessage(fileIndex + i));
    }
    Assert.assertEquals(lreader.getCurrentFile().getName(),
        databusFiles[fileNum].getName());
  }

  @Test
  public void testReadFromStart() throws Exception {
    lreader = new LocalStreamCollectorReader(partitionId, cluster, testStream);
    lreader.build(CollectorStreamReader.getDateFromCollectorFile(files[0]));
    lreader.initFromStart();
    Assert.assertNotNull(lreader.getCurrentFile());
    lreader.openStream();
    readFile(0, 0);
    readFile(1, 0);
    readFile(2, 0);
    lreader.close();
  }

  @Test
  public void testReadFromCheckpoint() throws Exception {
    lreader = new LocalStreamCollectorReader(partitionId, cluster, testStream);
    PartitionCheckpoint pcp = new PartitionCheckpoint(
        LocalStreamCollectorReader.getDatabusStreamFileName(collectorName,
            files[1]), 20);
    lreader.build(LocalStreamCollectorReader.getBuildTimestamp(testStream, 
        collectorName, pcp));
    lreader.initializeCurrentFile(pcp);
    Assert.assertNotNull(lreader.getCurrentFile());
    lreader.openStream();
    readFile(1, 20);
    readFile(2, 0);
    lreader.close();
  }

  @Test
  public void testReadFromTimeStamp() throws Exception {
    lreader = new LocalStreamCollectorReader(partitionId, cluster,  testStream);
    lreader.build(CollectorStreamReader.getDateFromCollectorFile(files[1]));
    lreader.initializeCurrentFile(
        CollectorStreamReader.getDateFromCollectorFile(files[1]));
    Assert.assertNotNull(lreader.getCurrentFile());
    lreader.openStream();
    readFile(1, 0);
    readFile(2, 0);
    lreader.close();
  }

}
