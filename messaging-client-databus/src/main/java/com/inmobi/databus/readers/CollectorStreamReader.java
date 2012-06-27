package com.inmobi.databus.readers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.TreeMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;

import com.inmobi.databus.Cluster;
import com.inmobi.databus.files.CollectorFile;
import com.inmobi.databus.files.DatabusStreamFile;
import com.inmobi.databus.files.FileMap;
import com.inmobi.databus.partition.PartitionId;

public class CollectorStreamReader extends StreamReader<CollectorFile> {

  private static final Log LOG = LogFactory.getLog(CollectorStreamReader.class);

  private long waitTimeForFlush;
  protected long currentOffset = 0;
  private boolean sameStream = false;

  CollectorStreamReader(PartitionId partitionId, Cluster cluster,
      String streamName, long waitTimeForFlush) throws IOException {
    this(partitionId, cluster, streamName, waitTimeForFlush, false);
  }

  public CollectorStreamReader(PartitionId partitionId,
      Cluster cluster, String streamName, long waitTimeForFlush,
      boolean noNewFiles) throws IOException {
    super(partitionId, cluster, streamName);
    this.waitTimeForFlush = waitTimeForFlush;
    this.noNewFiles = noNewFiles;
    LOG.info("Collector reader initialized with partitionId:" + partitionId +
        " streamDir:" + streamDir + 
        " waitTimeForFlush:" + waitTimeForFlush);
  }

  protected void initCurrentFile() {
    super.initCurrentFile();
    sameStream = false;
  }

  protected Path getStreamDir(Cluster cluster, String streamName) {
    Path streamDataDir = new Path(cluster.getDataDir(), streamName);
    return new Path(streamDataDir, partitionId.getCollector());
  }

  protected FileMap<CollectorFile> createFileMap() throws IOException {
    return new FileMap<CollectorFile>() {
      
      @Override
      protected PathFilter createPathFilter() {
        return new PathFilter() {
          @Override
          public boolean accept(Path p) {
            if (p.getName().endsWith("_current")
                || p.getName().endsWith("_stats")) {
              return false;
            }
            return true;
          }
        };
      }

      @Override
      protected void buildList() throws IOException {
        if (fs.exists(streamDir)) {
          FileStatus[] fileStatuses = fs.listStatus(streamDir, pathFilter);
          if (fileStatuses == null || fileStatuses.length == 0) {
            LOG.info("No files in directory:" + streamDir);
            return;
          }
          for (FileStatus file : fileStatuses) {
            LOG.debug("Adding Path:" + file.getPath());
            addPath(file.getPath());
          }
        }        
      }

      @Override
      protected TreeMap<CollectorFile, Path> createFilesMap() {
        return new TreeMap<CollectorFile, Path>();
      }

      @Override
      protected CollectorFile getStreamFile(String fileName) {
        return CollectorFile.create(fileName);
      }

      @Override
      protected CollectorFile getStreamFile(Path file) {
        return CollectorFile.create(file.getName());
      }

    };
  }
  
  public void build() throws IOException {
    fileMap.build();
  }

  @Override
  protected String getStreamFileName(String streamName, Date timestamp) {
    return getCollectorFileName(streamName, timestamp);
  }


  @Override
  protected BufferedReader createReader(FSDataInputStream in)
      throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(in));
    return reader;
  }

  protected void resetCurrentFileSettings() {
    super.resetCurrentFileSettings();
    currentOffset = 0;
  }

  protected void skipOldData(FSDataInputStream in, BufferedReader reader)
      throws IOException {
    if (sameStream) {
      LOG.info("Seeking to offset:" + currentOffset);
      seekToOffset(in, reader);
    } else {
      skipLines(in, reader, currentLineNum);
      sameStream = true;
    }
  }

  /**
   * Skip the number of lines passed.
   * 
   * @return the actual number of lines skipped.
   */
  private void seekToOffset(FSDataInputStream in, BufferedReader reader) 
      throws IOException {
    in.seek(currentOffset);
  }

  public boolean isStreamFile(String fileName) {
    return isCollectorFile(fileName);
  }

  public String readLine() throws IOException, InterruptedException {
    String line = null;
    if (inStream != null) {
      line = readLine(inStream, reader);
    }
    while (line == null) { // reached end of file?
      build(); // rebuild file list
      if (!nextFile()) { //there is no next file
        if (noNewFiles) {
          // this boolean check is only for tests 
          return null;
        } 
        if (!setIterator()) {
          LOG.info("Could not find current file in the stream");
          if (stillInCollectorStream()) {
            LOG.info("Staying in collector stream as earlier files still exist");
            startFromNextHigher(currentFile.getName());
            LOG.info("Reading from the next higher file");
          } else {
            LOG.info("Current file would have been moved to Local Stream");
            return null;
          }
        } else {
          waitForFlushAndReOpen();
          LOG.info("Reading from the same file after reopen");
        }
      } else {
        LOG.info("Reading from next file: " + currentFile);
      }
      line = readLine(inStream, reader);
    }
    currentOffset = inStream.getPos();
    return line;
  }

  private void waitForFlushAndReOpen() throws IOException, InterruptedException {
    LOG.info("Waiting for flush");
    Thread.sleep(waitTimeForFlush);
    openCurrentFile(false);    
  }

  private boolean stillInCollectorStream() throws IOException {
    if (fileMap.isWithin(currentFile.getName())) {
      return true;
    }
    return false;
  }

  public boolean isCollectorFile(String fileName) {
    try {
      CollectorFile.create(fileName);
    } catch (IllegalArgumentException ie) {
      return false;
    }
    return true;
  }

  public static String getCollectorFileName(String streamName,
      String localStreamfile) {
    return DatabusStreamFile.create(streamName, localStreamfile)
        .getCollectorFile().toString();
  }

  public static Date getDateFromCollectorFile(String fileName)
      throws IOException {
    return CollectorFile.create(fileName).getTimestamp();
  }

  public static String getCollectorFileName(String streamName, Date date) {
    return new CollectorFile(streamName, date, 0).toString();
  }

}
