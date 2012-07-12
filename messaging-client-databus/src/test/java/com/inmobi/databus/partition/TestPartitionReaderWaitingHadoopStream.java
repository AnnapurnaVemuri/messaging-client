package com.inmobi.databus.partition;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapred.SequenceFileInputFormat;
import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.inmobi.databus.readers.DatabusStreamWaitingReader;
import com.inmobi.messaging.consumer.databus.DataEncodingType;
import com.inmobi.messaging.consumer.databus.QueueEntry;
import com.inmobi.messaging.consumer.util.HadoopUtil;
import com.inmobi.messaging.consumer.util.TestUtil;

public class TestPartitionReaderWaitingHadoopStream {

  protected static final String testStream = "testclient";
  protected static final String clusterName = "testCluster";
  protected PartitionId partitionId = new PartitionId(clusterName, null);

  protected LinkedBlockingQueue<QueueEntry> buffer = 
      new LinkedBlockingQueue<QueueEntry>(150);
  protected PartitionReader preader;

  protected String[] files = new String[] {HadoopUtil.files[1],
      HadoopUtil.files[3], HadoopUtil.files[5]};

  protected Path[] databusFiles = new Path[3];

  protected final String collectorName = "collector1";

  FileSystem fs;
  Path streamDir;
  Configuration conf = new Configuration();
  String inputFormatClass;
  DataEncodingType dataEncoding;

  @BeforeTest
  public void setup() throws Exception {
    // setup fs
    fs = FileSystem.getLocal(conf);
    streamDir = new Path("/tmp/test/hadoop/" + this.getClass().getSimpleName(),
         testStream).makeQualified(fs);
    HadoopUtil.setupHadoopCluster(conf, files, databusFiles, streamDir);
    inputFormatClass = SequenceFileInputFormat.class.getName();
    dataEncoding = DataEncodingType.NONE;
  }

  @AfterTest
  public void cleanup() throws IOException {
    fs.delete(streamDir.getParent(), true);
  }

  @Test
  public void testReadFromStart() throws Exception {
    preader = new PartitionReader(partitionId, null, fs, buffer,
        streamDir, conf, inputFormatClass, DatabusStreamWaitingReader.getDateFromStreamDir(streamDir,
            databusFiles[0]),
        1000, DataEncodingType.NONE);
    preader.init();
    Assert.assertTrue(buffer.isEmpty());
    Assert.assertEquals(preader.getReader().getClass().getName(),
        ClusterReader.class.getName());
    Assert.assertEquals(((ClusterReader)preader.getReader())
        .getReader().getClass().getName(),
        DatabusStreamWaitingReader.class.getName());
    preader.start();
    while (buffer.remainingCapacity() > 0) {
      Thread.sleep(10);
    }
    FileStatus fs0 = fs.getFileStatus(databusFiles[0]); 
    FileStatus fs1 = fs.getFileStatus(databusFiles[1]); 
    fs.delete(databusFiles[0], true);
    fs.delete(databusFiles[1], true);
    fs.delete(databusFiles[2], true);
    Path[] newDatabusFiles = new Path[3];
    HadoopUtil.setUpHadoopFiles(streamDir, conf, new String[] {
        HadoopUtil.files[6]},
        newDatabusFiles);
    TestUtil.assertBuffer(DatabusStreamWaitingReader.getHadoopStreamFile(
        fs0), 1, 0, 100, partitionId, buffer,
        dataEncoding.equals(DataEncodingType.BASE64));
    TestUtil.assertBuffer(DatabusStreamWaitingReader.getHadoopStreamFile(
        fs1), 2, 0, 50, partitionId, buffer,
        dataEncoding.equals(DataEncodingType.BASE64));
    
    while (buffer.remainingCapacity() > 0) {
      Thread.sleep(10);
    }
    TestUtil.assertBuffer(DatabusStreamWaitingReader.getHadoopStreamFile(
        fs1), 2, 50, 50, partitionId, buffer, dataEncoding.equals(DataEncodingType.BASE64));
    TestUtil.assertBuffer(DatabusStreamWaitingReader.getHadoopStreamFile(
        fs.getFileStatus(newDatabusFiles[0])), 1, 0, 100, partitionId,
        buffer, dataEncoding.equals(DataEncodingType.BASE64));
    Assert.assertTrue(buffer.isEmpty());
    Assert.assertNotNull(preader.getReader());
    Assert.assertEquals(((ClusterReader)preader.getReader())
        .getReader().getClass().getName(),
        DatabusStreamWaitingReader.class.getName());
    HadoopUtil.setUpHadoopFiles(streamDir, conf, new String[] {
        HadoopUtil.files[7],
        HadoopUtil.files[8]}, newDatabusFiles);
    TestUtil.assertBuffer(DatabusStreamWaitingReader.getHadoopStreamFile(
        fs.getFileStatus(newDatabusFiles[0])), 1, 0, 100, partitionId, buffer,
        dataEncoding.equals(DataEncodingType.BASE64));
    TestUtil.assertBuffer(DatabusStreamWaitingReader.getHadoopStreamFile(
        fs.getFileStatus(newDatabusFiles[1])), 2, 0, 100, partitionId,
        buffer, dataEncoding.equals(DataEncodingType.BASE64));
    Assert.assertTrue(buffer.isEmpty());    
    preader.close();
  }
}
