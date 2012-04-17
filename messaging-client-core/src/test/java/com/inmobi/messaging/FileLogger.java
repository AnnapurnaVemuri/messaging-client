package com.inmobi.messaging;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public class FileLogger {

  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      System.out.println("Usage: FileLogger <file>");
    }
    String file = args[0];
    AbstractMessagePublisher publisher = 
        (AbstractMessagePublisher) MessagePublisherFactory
        .create();
    BufferedReader in = new BufferedReader(new FileReader(new File(file)));
    String line = in.readLine();
    while (line != null) {
      Message msg = new Message("test", line.getBytes());
      publisher.publish(msg);
      line = in.readLine();
    }
    publisher.close();
    long invocation = publisher.getStats().getInvocationCount();
    System.out.println("Total invocations: " + invocation);
  }
}
