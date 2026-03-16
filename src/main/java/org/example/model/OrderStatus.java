package org.example.model;

public enum OrderStatus {
    PENDING,      // order received, sitting in SQS
    PROCESSING,   // worker picked it up from SQS
    COMPLETED,    // worker finished processing
    FAILED        // something went wrong during processing
}
