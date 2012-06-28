package com.inmobi.databus.readers;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;

import com.inmobi.databus.Cluster;
import com.inmobi.databus.files.DatabusStreamFile;
import com.inmobi.databus.files.FileMap;
import com.inmobi.databus.partition.PartitionId;

public abstract class DatabusStreamWaitingReader extends DatabusStreamReader {

  DatabusStreamWaitingReader(PartitionId partitionId, Cluster cluster,
      String streamName, long waitTimeForCreate) throws IOException {
    super(partitionId, cluster, streamName);
    this.waitTimeForCreate = waitTimeForCreate;
  }

  private static final Log LOG = LogFactory.getLog(DatabusStreamWaitingReader.class);
  
  protected void startFromNextHigher(Path file) 
      throws IOException, InterruptedException {
    if (!setNextHigher(file)) {
      if (noNewFiles) {
        // this boolean check is only for tests 
        return;
      }
      waitForNextFileCreation(file);
    }
  }

  private void waitForNextFileCreation(Path file) 
      throws IOException, InterruptedException {
    while (!closed && !setNextHigher(file)) {
      LOG.info("Waiting for next file creation");
      Thread.sleep(waitTimeForCreate);
      build();
    }
  }

  @Override
  public String readLine() throws IOException, InterruptedException {
    String line = null;
    if (inStream != null) {
      line = readLine(inStream, reader);
    }
    while (line == null) { // reached end of file
      LOG.debug("Read " + currentFile + " with lines:" + currentLineNum);
      if (!nextFile()) { // reached end of file list
        LOG.info("could not find next file. Rebuilding");
        build(getDateFromDatabusStreamDir(streamDir, 
            currentFile));
        if (!nextFile()) { // reached end of stream
          if (noNewFiles) {
            // this boolean check is only for tests 
            return null;
          } 
          LOG.info("Could not find next file");
          startFromNextHigher(currentFile);
          LOG.info("Reading from next higher file "+ currentFile);
        } else {
          LOG.info("Reading from " + currentFile + " after rebuild");
        }
      } else {
        // read line from next file
        LOG.info("Reading from next file " + currentFile);
      }
      line = readLine(inStream, reader);
    }
    return line;
  }

  @Override
  protected FileMap<DatabusStreamFile> createFileMap() throws IOException {
    return new StreamFileMap() {

      @Override
      protected PathFilter createPathFilter() {
        return new PathFilter() {
          @Override
          public boolean accept(Path path) {
            return true;
          }          
        };
      }
      
    };
  }

}
