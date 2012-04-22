package com.inmobi.messaging.consumer.databus;

import com.inmobi.messaging.Message;

public class QueueEntry {

  final Message message;
  final PartitionId partitionId;
  final String fileName;
  final long offset;

  QueueEntry(Message msg, PartitionId partitionId, String fileName, long offset) {
    this.message = msg;
    this.partitionId = partitionId;
    this.fileName = fileName;
    this.offset = offset;
  }
}
