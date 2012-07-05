package com.inmobi.databus.readers;

import java.io.IOException;

import org.apache.commons.codec.binary.Base64;
import org.apache.hadoop.fs.FileSystem;
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

public class TestMergeStreamReader {
  private static final String testStream = "testclient";

  private static final String collectorName = "collector1";
  private static final String clusterName = "testCluster";
  private PartitionId partitionId = new PartitionId(clusterName, null);
  private MergedStreamReader reader;
  private Cluster cluster;
  private FileSystem fs;
  private String[] files = new String[] {TestUtil.files[1], TestUtil.files[3],
      TestUtil.files[5]};
  private Path[] databusFiles = new Path[3];
  private String doesNotExist1 = TestUtil.files[0];
  private String doesNotExist2 = TestUtil.files[2];
  private String doesNotExist3 = TestUtil.files[7];

  @BeforeTest
  public void setup() throws Exception {
    // initialize config
    cluster = TestUtil.setupLocalCluster(this.getClass().getSimpleName(),
        testStream, new PartitionId(clusterName, collectorName), files, null,
        databusFiles, 0, 3);
    fs = FileSystem.get(cluster.getHadoopConf());

  }

  @AfterTest
  public void cleanup() throws IOException {
    TestUtil.cleanupCluster(cluster);
  }

  @Test
  public void testInitialize() throws Exception {
    // Read from start
    reader = new MergedStreamReader(partitionId, cluster, testStream, 1000, false);
    reader.build(CollectorStreamReader.getDateFromCollectorFile(files[0]));

    reader.initFromStart();
    Assert.assertEquals(reader.getCurrentFile(), databusFiles[0]);

    // Read from checkpoint with merge stream file name
    reader.initializeCurrentFile(new PartitionCheckpoint(
        databusFiles[1].getName(), 20));
    Assert.assertEquals(reader.getCurrentFile(), databusFiles[1]);

    // Read from checkpoint with merge stream file name, which does not exist
    reader.initializeCurrentFile(new PartitionCheckpoint(
        MergedStreamReader.getDatabusStreamFileName(collectorName,
            doesNotExist1), 20));
    Assert.assertEquals(reader.getCurrentFile(), databusFiles[0]);

    // Read from checkpoint with merge stream file name, which does not exist
    // with file timestamp after the stream
    reader.initializeCurrentFile(new PartitionCheckpoint(
        MergedStreamReader.getDatabusStreamFileName(collectorName,
            doesNotExist2), 20));
    Assert.assertNull(reader.getCurrentFile());  

    // Read from checkpoint with merge stream file name, which does not exist
    // with file timestamp within the stream
    reader.initializeCurrentFile(new PartitionCheckpoint(
        MergedStreamReader.getDatabusStreamFileName(collectorName,
            doesNotExist3), 20));
    Assert.assertNull(reader.getCurrentFile());  

    //Read from startTime in merge stream directory 
    reader.initializeCurrentFile(
        CollectorStreamReader.getDateFromCollectorFile(files[1]));
    Assert.assertEquals(reader.getCurrentFile(), databusFiles[1]);

    //Read from startTime in merge stream directory, before the stream
    reader.initializeCurrentFile(
        CollectorStreamReader.getDateFromCollectorFile(doesNotExist1));
    Assert.assertEquals(reader.getCurrentFile(), databusFiles[0]);

    //Read from startTime in merge stream directory, within the stream
    reader.initializeCurrentFile(
        CollectorStreamReader.getDateFromCollectorFile(doesNotExist2));
    Assert.assertEquals(reader.getCurrentFile(), databusFiles[1]);

    //Read from startTime in after the stream
    reader.initializeCurrentFile(
        CollectorStreamReader.getDateFromCollectorFile(doesNotExist3));
    Assert.assertNull(reader.getCurrentFile());  

    // startFromNextHigher with filename
    reader.startFromNextHigher(fs.getFileStatus(databusFiles[1]));
    Assert.assertEquals(reader.getCurrentFile(), databusFiles[2]);

    // startFromNextHigher with date
    reader.startFromTimestmp(
        CollectorStreamReader.getDateFromCollectorFile(files[1]));
    Assert.assertEquals(reader.getCurrentFile(), databusFiles[1]);
    
    // startFromBegining 
   reader.startFromBegining();
   Assert.assertEquals(reader.getCurrentFile(), databusFiles[0]);

  }

  private void readFile(int fileNum, int startIndex) throws Exception {
    int fileIndex = fileNum * 100 ;
    for (int i = startIndex; i < 100; i++) {
      String line = reader.readLine();
      Assert.assertNotNull(line);
      Assert.assertEquals(new String(Base64.decodeBase64(line)),
          MessageUtil.constructMessage(fileIndex + i));
    }
    Assert.assertEquals(reader.getCurrentFile(), databusFiles[fileNum]);
  }

  @Test
  public void testReadFromStart() throws Exception {
    reader = new MergedStreamReader(partitionId, cluster, testStream, 1000, false);
    reader.build(CollectorStreamReader.getDateFromCollectorFile(files[0]));
    reader.initFromStart();
    Assert.assertNotNull(reader.getCurrentFile());
    reader.openStream();
    readFile(0, 0);
    readFile(1, 0);
    readFile(2, 0);
    reader.close();
  }

  @Test
  public void testReadFromCheckpoint() throws Exception {
    reader = new MergedStreamReader(partitionId, cluster, testStream, 1000, false);
    PartitionCheckpoint pcp = new PartitionCheckpoint(
        MergedStreamReader.getDatabusStreamFileName(collectorName, files[1]), 20);
    reader.build(DatabusStreamReader.getBuildTimestamp(testStream, 
        MergedStreamReader.getDatabusStreamFileName(collectorName, files[1])));
    reader.initializeCurrentFile(pcp);
    Assert.assertNotNull(reader.getCurrentFile());
    reader.openStream();
    readFile(1, 20);
    readFile(2, 0);
    reader.close();
  }

  @Test
  public void testReadFromTimeStamp() throws Exception {
    reader = new MergedStreamReader(partitionId, cluster,  testStream, 1000, false);
    reader.build(CollectorStreamReader.getDateFromCollectorFile(files[1]));
    reader.initializeCurrentFile(CollectorStreamReader.getDateFromCollectorFile(
        files[1]));
    Assert.assertNotNull(reader.getCurrentFile());
    reader.openStream();
    readFile(1, 0);
    readFile(2, 0);
    reader.close();
  }

}
