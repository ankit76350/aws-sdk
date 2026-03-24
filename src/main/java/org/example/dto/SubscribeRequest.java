package org.example.dto;

public class SubscribeRequest {

    private String topicArn;
    private String endpoint; // email or phone number (+91XXXXXXXXXX)
    private String protocol; // "email" or "sms"

    public String getTopicArn() { return topicArn; }
    public void setTopicArn(String topicArn) { this.topicArn = topicArn; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }
}
