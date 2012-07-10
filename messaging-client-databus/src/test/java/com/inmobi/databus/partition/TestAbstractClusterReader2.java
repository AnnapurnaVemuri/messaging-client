package com.inmobi.databus.partition;

import java.io.IOException;
import java.util.Calendar;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.testng.Assert;

import com.inmobi.databus.partition.PartitionCheckpoint;
import com.inmobi.databus.partition.PartitionId;
import com.inmobi.databus.partition.PartitionReader;
import com.inmobi.databus.readers.DatabusStreamWaitingReader;
import com.inmobi.messaging.consumer.databus.DataEncodingType;
import com.inmobi.messaging.consumer.databus.QueueEntry;
import com.inmobi.messaging.consumer.util.HadoopUtil;
import com.inmobi.messaging.consumer.util.TestUtil;

public abstract class TestAbstractClusterReader2 {
  protected static final String testStream = "testclient";
  protected static final String clusterName = "testCluster";
  protected PartitionId partitionId = new PartitionId(clusterName, null);
  protected LinkedBlockingQueue<QueueEntry> buffer = 
      new LinkedBlockingQueue<QueueEntry>(1000);
  protected PartitionReader preader;

  protected String[] files = new String[] {TestUtil.files[1], TestUtil.files[3],
      TestUtil.files[5]};
  protected Path[] databusFiles = new Path[3];

  protected final String collectorName = "collector1";
  FileSystem fs;
  Path streamDir;
  Configuration conf = new Configuration();
  String inputFormatClass;
  DataEncodingType dataEncoding;

  public void cleanup() throws IOException {
    fs.delete(streamDir.getParent(), true);
  }

  abstract Path getStreamsDir();

  public void testInitialize() throws Exception {
    Calendar cal = Calendar.getInstance();
    cal.setTime(DatabusStreamWaitingReader.getDateFromStreamDir(streamDir,
        databusFiles[0].getParent()));
    // Read from start
    preader = new PartitionReader(partitionId, null, fs, buffer,
        testStream, streamDir, conf, inputFormatClass, cal.getTime(),
        1000, dataEncoding);
    Assert.assertEquals(preader.getReader().getClass().getName(),
        ClusterReader.class.getName());
    preader.init();
    Assert.assertEquals(preader.getCurrentFile().toString(),
        databusFiles[0].toString());

    // Read from checkpoint with local stream file name
    preader = new PartitionReader(partitionId, new PartitionCheckpoint(
        DatabusStreamWaitingReader.getHadoopStreamFile(
            fs.getFileStatus(databusFiles[1])), 20), fs, buffer,
        testStream, streamDir, conf, inputFormatClass, null,
        1000, dataEncoding);
    preader.init();
    Assert.assertEquals(preader.getCurrentFile().toString(),
        databusFiles[1].toString());

    // Read from checkpoint with local stream file name which does not exist
    // and is before the stream
    preader = new PartitionReader(partitionId, new PartitionCheckpoint(
        HadoopUtil.getOlderFile(streamDir, fs, databusFiles[0]), 20),
        fs, buffer, testStream, streamDir, conf, inputFormatClass, null,
        1000, dataEncoding);
    preader.init();
    Assert.assertEquals(preader.getCurrentFile().toString(),
        databusFiles[0].toString());

    //Read from startTime in local stream directory, with no checkpoint
    cal.setTime(DatabusStreamWaitingReader.getDateFromStreamDir(streamDir,
        databusFiles[1].getParent()));

    preader = new PartitionReader(partitionId, null, fs, buffer,
        testStream, streamDir, conf, inputFormatClass, cal.getTime(),
        1000, dataEncoding);
    preader.init();
    Assert.assertEquals(preader.getCurrentFile().toString(),
        databusFiles[1].toString());

    //Read from startTime in local stream directory, with checkpoint
    preader = new PartitionReader(partitionId, new PartitionCheckpoint(
        DatabusStreamWaitingReader.getHadoopStreamFile(
            fs.getFileStatus(databusFiles[1])), 20),fs, buffer,
            testStream, streamDir, conf, inputFormatClass, cal.getTime(),
            1000, dataEncoding);
    preader.init();
    Assert.assertEquals(preader.getCurrentFile().toString(),
        databusFiles[1].toString());

    //Read from startTime in local stream directory, with no timestamp file,
    // with no checkpoint
    cal.setTime(DatabusStreamWaitingReader.getDateFromStreamDir(streamDir,
        databusFiles[0].getParent()));
    cal.add(Calendar.MINUTE, 1);
    preader = new PartitionReader(partitionId, null, fs, buffer,
        testStream, streamDir, conf, inputFormatClass, cal.getTime(),
        1000, dataEncoding);
    preader.init();
    Assert.assertEquals(preader.getCurrentFile().toString(),
        databusFiles[1].toString());

    //Read from startTime in local stream directory, with no timestamp file,
    //with checkpoint
    preader = new PartitionReader(partitionId, new PartitionCheckpoint(
        DatabusStreamWaitingReader.getHadoopStreamFile(
            fs.getFileStatus(databusFiles[1])), 20), fs, buffer,
            testStream, streamDir, conf, inputFormatClass, cal.getTime(),
            1000, dataEncoding);
    preader.init();
    Assert.assertEquals(preader.getCurrentFile().toString(),
        databusFiles[1].toString());

    //Read from startTime beyond the stream
    cal.setTime(DatabusStreamWaitingReader.getDateFromStreamDir(streamDir,
        databusFiles[0].getParent()));
    cal.add(Calendar.MINUTE, -2);
    preader = new PartitionReader(partitionId, null, fs, buffer,
        testStream, streamDir, conf, inputFormatClass, cal.getTime(),
        1000, dataEncoding);
    preader.init();
    Assert.assertEquals(preader.getCurrentFile().toString(),
        databusFiles[0].toString());

    //Read from startTime beyond the stream, with checkpoint
    preader = new PartitionReader(partitionId, new PartitionCheckpoint(
        DatabusStreamWaitingReader.getHadoopStreamFile(
            fs.getFileStatus(databusFiles[1])), 20), fs, buffer,
            testStream, streamDir, conf, inputFormatClass, cal.getTime(),
            1000, dataEncoding);
    preader.init();
    Assert.assertEquals(preader.getCurrentFile().toString(),
        databusFiles[0].toString());

    //Read from startTime after the stream
    cal.setTime(DatabusStreamWaitingReader.getDateFromStreamDir(streamDir,
        databusFiles[2].getParent()));
    cal.add(Calendar.MINUTE, 2);
    preader = new PartitionReader(partitionId,
        null, fs, buffer,
        testStream, streamDir, conf, inputFormatClass, cal.getTime(),
        1000, dataEncoding, true);
    preader.init();
    Assert.assertNotNull(preader.getReader());
    Assert.assertEquals(preader.getReader().getClass().getName(),
        ClusterReader.class.getName());
    Assert.assertEquals(((ClusterReader)preader.getReader())
        .getReader().getClass().getName(),
        DatabusStreamWaitingReader.class.getName());
    Assert.assertNull(preader.getCurrentFile());

    //Read from startTime after the stream, with checkpoint
    preader = new PartitionReader(partitionId, new PartitionCheckpoint(
        DatabusStreamWaitingReader.getHadoopStreamFile(
            fs.getFileStatus(databusFiles[1])), 20), fs, buffer,
            testStream, streamDir, conf, inputFormatClass, cal.getTime(),
            1000, dataEncoding, true);
    preader.init();
    Assert.assertNotNull(preader.getReader());
    Assert.assertEquals(preader.getReader().getClass().getName(),
        ClusterReader.class.getName());
    Assert.assertEquals(((ClusterReader)preader.getReader())
        .getReader().getClass().getName(),
        DatabusStreamWaitingReader.class.getName());
    Assert.assertNull(preader.getCurrentFile());
  }

  public void testReadFromStart() throws Exception {
    preader = new PartitionReader(partitionId, null, fs, buffer, testStream,
        streamDir, conf, inputFormatClass,
        DatabusStreamWaitingReader.getDateFromStreamDir(streamDir,
                databusFiles[0].getParent()),
        1000, dataEncoding, true);
    preader.init();
    Assert.assertTrue(buffer.isEmpty());
    Assert.assertEquals(preader.getReader().getClass().getName(),
        ClusterReader.class.getName());
    Assert.assertEquals(((ClusterReader)preader.getReader())
        .getReader().getClass().getName(),
        DatabusStreamWaitingReader.class.getName());
    preader.execute();
    TestUtil.assertBuffer(DatabusStreamWaitingReader.getHadoopStreamFile(
        fs.getFileStatus(databusFiles[0])), 1, 0, 100, partitionId,
        buffer);
    TestUtil.assertBuffer(DatabusStreamWaitingReader.getHadoopStreamFile(
        fs.getFileStatus(databusFiles[1])), 2, 0, 100, partitionId,
        buffer);
    TestUtil.assertBuffer(DatabusStreamWaitingReader.getHadoopStreamFile(
        fs.getFileStatus(databusFiles[2])), 3, 0, 100, partitionId,
        buffer);
    Assert.assertTrue(buffer.isEmpty());
    Assert.assertNotNull(preader.getReader());
    Assert.assertEquals(preader.getReader().getClass().getName(),
        ClusterReader.class.getName());
    Assert.assertEquals(((ClusterReader)preader.getReader())
        .getReader().getClass().getName(),
        DatabusStreamWaitingReader.class.getName());
  }

  public void testReadFromCheckpoint() throws Exception {
    preader = new PartitionReader(partitionId, new PartitionCheckpoint(
        DatabusStreamWaitingReader.getHadoopStreamFile(fs.getFileStatus(
            databusFiles[1])), 20), fs, buffer,
            testStream, streamDir, conf, inputFormatClass, null,
            1000, dataEncoding, true);
    preader.init();
    Assert.assertTrue(buffer.isEmpty());
    Assert.assertEquals(preader.getReader().getClass().getName(),
        ClusterReader.class.getName());
    Assert.assertEquals(((ClusterReader)preader.getReader())
        .getReader().getClass().getName(),
        DatabusStreamWaitingReader.class.getName());
    preader.execute();
    TestUtil.assertBuffer(DatabusStreamWaitingReader.getHadoopStreamFile(
        fs.getFileStatus(databusFiles[1])), 2, 20, 80, partitionId,
        buffer);
    TestUtil.assertBuffer(DatabusStreamWaitingReader.getHadoopStreamFile(
        fs.getFileStatus(databusFiles[2])), 3, 0, 100, partitionId,
        buffer);
    Assert.assertTrue(buffer.isEmpty());
    Assert.assertNotNull(preader.getReader());
    Assert.assertEquals(preader.getReader().getClass().getName(),
        ClusterReader.class.getName());
    Assert.assertEquals(((ClusterReader)preader.getReader())
        .getReader().getClass().getName(),
        DatabusStreamWaitingReader.class.getName());
  }

  public void testReadFromCheckpointWhichDoesNotExist() throws Exception {
    preader = new PartitionReader(partitionId, new PartitionCheckpoint(
        HadoopUtil.getOlderFile(streamDir, fs, databusFiles[0]),
        20), fs, buffer,
        testStream, streamDir, conf, inputFormatClass, null,
        1000, dataEncoding, true);
    preader.init();
    Assert.assertTrue(buffer.isEmpty());
    Assert.assertEquals(preader.getReader().getClass().getName(),
        ClusterReader.class.getName());
    Assert.assertEquals(((ClusterReader)preader.getReader())
        .getReader().getClass().getName(),
        DatabusStreamWaitingReader.class.getName());
    preader.execute();
    TestUtil.assertBuffer(DatabusStreamWaitingReader.getHadoopStreamFile(
        fs.getFileStatus(databusFiles[0])), 1, 0, 100, partitionId,
        buffer);
    TestUtil.assertBuffer(DatabusStreamWaitingReader.getHadoopStreamFile(
        fs.getFileStatus(databusFiles[1])), 2, 0, 100, partitionId,
        buffer);
    TestUtil.assertBuffer(DatabusStreamWaitingReader.getHadoopStreamFile(
        fs.getFileStatus(databusFiles[2])), 3, 0, 100, partitionId,
        buffer);
    Assert.assertTrue(buffer.isEmpty());
    Assert.assertNotNull(preader.getReader());
    Assert.assertEquals(preader.getReader().getClass().getName(),
        ClusterReader.class.getName());
    Assert.assertEquals(((ClusterReader)preader.getReader())
        .getReader().getClass().getName(),
        DatabusStreamWaitingReader.class.getName());
  }

  public void testReadFromStartTime() throws Exception {
    preader = new PartitionReader(partitionId, new PartitionCheckpoint(
        DatabusStreamWaitingReader.getHadoopStreamFile(
            fs.getFileStatus(databusFiles[1])), 20), fs, buffer,
            testStream, streamDir, conf, inputFormatClass,
            DatabusStreamWaitingReader.getDateFromStreamDir(streamDir,
                databusFiles[1].getParent()),
            1000, dataEncoding, true);
    preader.init();
    Assert.assertTrue(buffer.isEmpty());
    Assert.assertEquals(preader.getReader().getClass().getName(),
        ClusterReader.class.getName());
    Assert.assertEquals(((ClusterReader)preader.getReader())
        .getReader().getClass().getName(),
        DatabusStreamWaitingReader.class.getName());
    preader.execute();
    TestUtil.assertBuffer(DatabusStreamWaitingReader.getHadoopStreamFile(
        fs.getFileStatus(databusFiles[1])), 2, 0, 100, partitionId,
        buffer);
    TestUtil.assertBuffer(DatabusStreamWaitingReader.getHadoopStreamFile(
        fs.getFileStatus(databusFiles[2])), 3, 0, 100, partitionId,
        buffer);
    Assert.assertTrue(buffer.isEmpty());
    Assert.assertNotNull(preader.getReader());
    Assert.assertEquals(preader.getReader().getClass().getName(),
        ClusterReader.class.getName());
    Assert.assertEquals(((ClusterReader)preader.getReader())
        .getReader().getClass().getName(),
        DatabusStreamWaitingReader.class.getName());
  }

  public void testReadFromStartTimeWithinStream() throws Exception {
    Calendar cal = Calendar.getInstance();
    cal.setTime(DatabusStreamWaitingReader.getDateFromStreamDir(streamDir,
        databusFiles[0].getParent()));
    cal.add(Calendar.MINUTE, 1);
    preader = new PartitionReader(partitionId, new PartitionCheckpoint(
        DatabusStreamWaitingReader.getHadoopStreamFile(
            fs.getFileStatus(databusFiles[1])), 20),  fs, buffer,
            testStream, streamDir, conf, inputFormatClass, cal.getTime(),
            1000, dataEncoding, true);
    preader.init();
    Assert.assertTrue(buffer.isEmpty());
    Assert.assertEquals(preader.getReader().getClass().getName(),
        ClusterReader.class.getName());
    Assert.assertEquals(((ClusterReader)preader.getReader())
        .getReader().getClass().getName(),
        DatabusStreamWaitingReader.class.getName());
    preader.execute();
    TestUtil.assertBuffer(DatabusStreamWaitingReader.getHadoopStreamFile(
        fs.getFileStatus(databusFiles[1])), 2, 0, 100, partitionId,
        buffer);
    TestUtil.assertBuffer(DatabusStreamWaitingReader.getHadoopStreamFile(
        fs.getFileStatus(databusFiles[2])), 3, 0, 100, partitionId,
        buffer);
    Assert.assertTrue(buffer.isEmpty());
    Assert.assertNotNull(preader.getReader());
    Assert.assertEquals(preader.getReader().getClass().getName(),
        ClusterReader.class.getName());
    Assert.assertEquals(((ClusterReader)preader.getReader())
        .getReader().getClass().getName(),
        DatabusStreamWaitingReader.class.getName());
  }

  public void testReadFromStartTimeBeforeStream() throws Exception {
    Calendar cal = Calendar.getInstance();
    cal.setTime(DatabusStreamWaitingReader.getDateFromStreamDir(streamDir,
        databusFiles[0].getParent()));
    cal.add(Calendar.MINUTE, -1);
    preader = new PartitionReader(partitionId, new PartitionCheckpoint(
        DatabusStreamWaitingReader.getHadoopStreamFile(
            fs.getFileStatus(databusFiles[1])), 20),  fs, buffer,
            testStream, streamDir, conf, inputFormatClass, cal.getTime(),
            1000, dataEncoding, true);
    preader.init();
    Assert.assertTrue(buffer.isEmpty());
    Assert.assertEquals(preader.getReader().getClass().getName(),
        ClusterReader.class.getName());
    Assert.assertEquals(((ClusterReader)preader.getReader())
        .getReader().getClass().getName(),
        DatabusStreamWaitingReader.class.getName());
    preader.execute();
    TestUtil.assertBuffer(DatabusStreamWaitingReader.getHadoopStreamFile(
        fs.getFileStatus(databusFiles[0])), 1, 0, 100, partitionId,
        buffer);
    TestUtil.assertBuffer(DatabusStreamWaitingReader.getHadoopStreamFile(
        fs.getFileStatus(databusFiles[1])), 2, 0, 100, partitionId,
        buffer);
    TestUtil.assertBuffer(DatabusStreamWaitingReader.getHadoopStreamFile(
        fs.getFileStatus(databusFiles[2])), 3, 0, 100, partitionId,
        buffer);
    Assert.assertTrue(buffer.isEmpty());
    Assert.assertNotNull(preader.getReader());
    Assert.assertEquals(preader.getReader().getClass().getName(),
        ClusterReader.class.getName());
    Assert.assertEquals(((ClusterReader)preader.getReader())
        .getReader().getClass().getName(),
        DatabusStreamWaitingReader.class.getName());
  }

  public void testReadFromStartTimeAfterStream() throws Exception {
    Calendar cal = Calendar.getInstance();
    cal.setTime(DatabusStreamWaitingReader.getDateFromStreamDir(streamDir,
        databusFiles[2].getParent()));
    cal.add(Calendar.MINUTE, 2);
    preader = new PartitionReader(partitionId, new PartitionCheckpoint(
        DatabusStreamWaitingReader.getHadoopStreamFile(
            fs.getFileStatus(databusFiles[1])), 20),  fs, buffer,
            testStream, streamDir, conf, inputFormatClass, cal.getTime(),
            1000, dataEncoding, true);
    preader.init();
    Assert.assertTrue(buffer.isEmpty());
    Assert.assertNotNull(preader.getReader());
    Assert.assertEquals(preader.getReader().getClass().getName(),
        ClusterReader.class.getName());
    Assert.assertEquals(((ClusterReader)preader.getReader())
        .getReader().getClass().getName(),
        DatabusStreamWaitingReader.class.getName());
  }

}
