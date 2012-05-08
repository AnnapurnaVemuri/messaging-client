package com.inmobi.messaging.consumer.databus;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.concurrent.BlockingQueue;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.fs.Path;

import com.inmobi.databus.Cluster;
import com.inmobi.messaging.Message;

class PartitionReader {

  private static final Log LOG = LogFactory.getLog(PartitionReader.class);

  private final PartitionId partitionId;
  private final PartitionCheckpoint partitionCheckpoint;
  private final BlockingQueue<QueueEntry> buffer;
  private Date startTime;
  
  private final Path collectorDir;

  private Thread thread;
  private volatile boolean stopped;
  private LocalStreamReader lReader;
  private CollectorStreamReader cReader;
  private StreamReader currentReader;
  

  PartitionReader(PartitionId partitionId,
      PartitionCheckpoint partitionCheckpoint, Cluster cluster,
      BlockingQueue<QueueEntry> buffer, String streamName,
      Date startTime, long waitTimeForFlush) {
    this.partitionId = partitionId;
    this.buffer = buffer;
    this.startTime = startTime;
    this.partitionCheckpoint = partitionCheckpoint;
    
    // initialize cluster and its directories
    Path streamDir = new Path(cluster.getDataDir(), streamName);
    this.collectorDir = new Path(streamDir, partitionId.getCollector());

    try {
      lReader = new LocalStreamReader(partitionId,  cluster, streamName);
      cReader = new CollectorStreamReader(partitionId, cluster, streamName,
          waitTimeForFlush);
      initializeCurrentFile();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    
    LOG.info("Partition reader initialized with partitionId:" + partitionId +
            " checkPoint:" + partitionCheckpoint +  
            " collectorDir:" + collectorDir +
            " startTime:" + startTime +
            " currentReader:" + currentReader);
  }

  public synchronized void start() {
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        while (!stopped && !thread.isInterrupted()) {
          long startTime = System.currentTimeMillis();
          try {
            execute();
            if (stopped || thread.isInterrupted())
              return;
          } catch (Exception e) {
            LOG.warn("Error in run", e);
          }
          long finishTime = System.currentTimeMillis();
          LOG.debug("Execution took ms : " + (finishTime - startTime));
          try {
            long sleep = 1000;
            if (sleep > 0) {
              LOG.debug("Sleeping for " + sleep);
              Thread.sleep(sleep);
            }
          } catch (InterruptedException e) {
            LOG.warn("thread interrupted " + thread.getName(), e);
            return;
          }
        }
      }

    };
    thread = new Thread(runnable, this.partitionId.toString());
    LOG.info("Starting thread " + thread.getName());
    thread.start();
  }

  public void close() {
    stopped = true;
    LOG.info(Thread.currentThread().getName() + " stopped [" + stopped + "]");
    if (currentReader != null) {
      try {
		currentReader.close();
	  } catch (IOException e) {
		LOG.warn("Error closing current stream", e);
	  }
    }
  }

  private void initializeCurrentFileFromTimeStamp(Date timestamp)
      throws Exception {
    if (startTime != null) {
      if (lReader.initializeCurrentFile(timestamp)) {
        currentReader = lReader;
      } else if (cReader.initializeCurrentFile(startTime)) {
        currentReader = cReader;
      } else {
        currentReader = null;
      }
    }
  }
  
  private void initializeCurrentFileFromCheckpoint() throws Exception {
    String fileName = partitionCheckpoint.getFileName();
    if (cReader.isCollectorFile(fileName)) {
      if (cReader.initializeCurrentFile(partitionCheckpoint)) {
        currentReader = cReader;
      } else {
        String localStreamFileName = 
          LocalStreamReader.getLocalStreamFileName(
            partitionId.getCollector(), fileName);
        if (lReader.initializeCurrentFile(new PartitionCheckpoint(
            localStreamFileName, partitionCheckpoint.getLineNum()))) {
          currentReader = lReader;
        } else {
          currentReader = null;
        }
      }
    } else if (lReader.isLocalStreamFile(fileName)) {
      LOG.debug("Checkpointed file is in local stream directory");
      if (lReader.initializeCurrentFile(partitionCheckpoint)) {
    	currentReader = lReader;
      }
    } else {
      currentReader = null;
    }
  }
    
  private void initFromStart() throws Exception {
    if (lReader.initFromStart()) {
      currentReader = lReader;
    } else if (cReader.initFromStart()) {
      currentReader = cReader;
    } else {
      LOG.warn("No files to start");
      currentReader = null;
    }
  }
 
  private void initializeCurrentFile() throws Exception {
    if (startTime != null) {
      initializeCurrentFileFromTimeStamp(startTime);
    } else if (partitionCheckpoint != null &&
        partitionCheckpoint.getFileName() != null) {
      initializeCurrentFileFromCheckpoint();
    } else {
      initFromStart();
    }
  }

  Path getCurrentFile() {
    if (currentReader != null) {
      return currentReader.getCurrentFile();
    }
    return null;
  }
  
  StreamReader getCurrentReader() {
    return currentReader;
  }

  protected void execute() {
    if (currentReader == null) {
      return;
    }
    try {
      currentReader.openStream();
      LOG.debug("Reading file " + currentReader.getCurrentFile() + 
              " and lineNum:" + currentReader.getCurrentLineNum());
      while (buffer.remainingCapacity() != 0 && !stopped) {
        String line = currentReader.readLine();
        if (line != null) {
          // add the data to queue
          byte[] data = Base64.decodeBase64(line);
          LOG.debug("Current LineNum: " + currentReader.getCurrentLineNum());
          if (!buffer.offer(new QueueEntry(new Message(
            ByteBuffer.wrap(data)), partitionId,
            new PartitionCheckpoint(currentReader.getCurrentFile().getName(),
                currentReader.getCurrentLineNum())))) {
            LOG.warn("Could not add entry as buffer is full");
          }
        }
        if (line == null) {
          if (currentReader == lReader) {
            lReader.close();
            LOG.info("Switching to collector stream as we reached end of" +
                " stream on local stream");
            if (cReader.initFromStart()) {
              currentReader = cReader;
            } else {
              LOG.warn("No stream to read");
              currentReader.close();
              currentReader = null;
            }
          } else if (currentReader == cReader) {
            cReader.close();
            LOG.info("Looking for current file in local stream reader");
            lReader.build();
            if (!lReader.setCurrentFile(
                LocalStreamReader.getLocalStreamFileName(
                  partitionId.getCollector(),
                  cReader.getCurrentFile().getName()),
                cReader.getCurrentLineNum())) {
              LOG.info("Did not find current file in local stream as well.");
              currentReader.close();
              currentReader = null;
            } else {
              LOG.info("Switching to local stream as the file got moved");
              currentReader = lReader;
            }
          }
          return;
        }
      }
    } catch (Exception e) {
      LOG.warn("Error while reading stream", e);
    } finally {
      try {
        if (currentReader != null) {
          currentReader.close();
        }
      } catch (Exception e) {
        LOG.warn("Error while closing stream", e);
      }
    }
  }

}
