package com.inmobi.messaging.metrics;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.inmobi.instrumentation.AbstractMessagingClientStatsExposer;
import com.inmobi.messaging.consumer.AbstractMessageConsumerStatsExposer;

public class PartitionReaderStatsExposer extends 
    AbstractMessageConsumerStatsExposer {
  public final static String MESSAGES_READ_FROM_SOURCE = "messagesReadFromSource";
  public final static String MESSAGES_ADDED_TO_BUFFER = "messagesAddedToBuffer";
  public final static String HANDLED_EXCEPTIONS = "handledExceptions";
  public final static String WAIT_TIME_UNITS_NEW_FILE = "waitTimeUnitsNewFile";
  public final static String PARTITION_CONTEXT = "PartitionId";

  private final AtomicLong numMessagesReadFromSource = new AtomicLong(0);
  private final AtomicLong numMessagesAddedToBuffer = new AtomicLong(0);
  private final AtomicLong numHandledExceptions = new AtomicLong(0);
  private final AtomicLong numWaitTimeUnitsNewFile = new AtomicLong(0);

  private final String pid;

  public PartitionReaderStatsExposer(String topicName, String consumerName,
      String pid) {
    super(topicName, consumerName);
    this.pid = pid;
  }

  public void incrementMessagesReadFromSource() {
    numMessagesReadFromSource.incrementAndGet();
  }

  public void incrementMessagesAddedToBuffer() {
    numMessagesAddedToBuffer.incrementAndGet();
  }

  public void incrementHandledExceptions() {
    numHandledExceptions.incrementAndGet();
  }

  public void incrementWaitTimeUnitsNewFile() {
    numWaitTimeUnitsNewFile.incrementAndGet();
  }

  @Override
  protected void addToStatsMap(Map<String, Number> map) {
    map.put(MESSAGES_READ_FROM_SOURCE, getMessagesReadFromSource());
    map.put(MESSAGES_ADDED_TO_BUFFER, getMessagesAddedToBuffer());
    map.put(HANDLED_EXCEPTIONS, getHandledExceptions());
    map.put(WAIT_TIME_UNITS_NEW_FILE, getWaitTimeUnitsNewFile());
  }

  @Override
  protected void addToContextsMap(Map<String, String> map) {
    super.addToContextsMap(map);
    map.put(PARTITION_CONTEXT, pid);
  }

  public long getMessagesReadFromSource() {
    return numMessagesReadFromSource.get();
  }

  public long getMessagesAddedToBuffer() {
    return numMessagesAddedToBuffer.get();
  }

  public long getHandledExceptions() {
    return numHandledExceptions.get();
  }

  public long getWaitTimeUnitsNewFile() {
    return numWaitTimeUnitsNewFile.get();
  }
}
