package com.inmobi.databus.readers;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapred.TextInputFormat;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.inmobi.databus.Cluster;
import com.inmobi.databus.partition.PartitionId;
import com.inmobi.messaging.consumer.util.TestUtil;

public class TestMergeStreamMultipleCollectors {

  private static final String testStream = "testclient";

  private String[] collectors = new String[] {"collector1", "collector2"};
  private static final String clusterName = "testCluster";
  private PartitionId partitionId = new PartitionId(clusterName, null);
  private DatabusStreamWaitingReader reader;
  private Cluster cluster;
  private String[] files = new String[] {TestUtil.files[1], TestUtil.files[3],
      TestUtil.files[5]};
  Path[] databusFiles1 = new Path[3];
  Path[] databusFiles2 = new Path[3];
  Configuration conf;
  boolean encoded = true;

  @BeforeTest
  public void setup() throws Exception {
    // initialize config
    cluster = TestUtil.setupLocalCluster(this.getClass().getSimpleName(),
        testStream, new PartitionId(clusterName, collectors[0]), files, null,
        databusFiles1, 0, 3);
    TestUtil.setUpFiles(cluster, collectors[1], files, null, databusFiles2, 0,
        3);
    conf = cluster.getHadoopConf();
  }

  @Test
  public void testReadFromStart() throws Exception {
    reader = new DatabusStreamWaitingReader(partitionId,
        FileSystem.get(cluster.getHadoopConf()), testStream,
        DatabusStreamReader.getStreamsDir(cluster, testStream),
        TextInputFormat.class.getCanonicalName(), conf, 1000, false);
    reader.build(CollectorStreamReader.getDateFromCollectorFile(files[0]));
    reader.initFromStart();
    Assert.assertNotNull(reader.getCurrentFile());
    reader.openStream();
    TestAbstractDatabusWaitingReader.readFile(reader, 0, 0, databusFiles1[0],
        encoded);
    TestAbstractDatabusWaitingReader.readFile(reader, 0, 0, databusFiles2[0],
        encoded);
    TestAbstractDatabusWaitingReader.readFile(reader, 1, 0, databusFiles1[1],
        encoded);
    TestAbstractDatabusWaitingReader.readFile(reader, 1, 0, databusFiles2[1],
        encoded);
    TestAbstractDatabusWaitingReader.readFile(reader, 2, 0, databusFiles1[2],
        encoded);
    TestAbstractDatabusWaitingReader.readFile(reader, 2, 0, databusFiles2[2],
        encoded);
    reader.close();
  }
}
