package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sns.model.SubscribeResponse;
import software.amazon.awssdk.services.sns.model.UnsubscribeRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnsTopicService {

    private final SnsClient snsClient;

    public String subscribe(String topicArn, String protocol, String endpoint) {
        SubscribeRequest request = SubscribeRequest.builder()
                .topicArn(topicArn)
                .protocol(protocol)   // "email" or "sms"
                .endpoint(endpoint)   // email address or phone number
                .build();

        SubscribeResponse response = snsClient.subscribe(request);
        log.info("Subscribed {} ({}) to topic {} | subscriptionArn={}",
                endpoint, protocol, topicArn, response.subscriptionArn());
        return response.subscriptionArn();
    }

    public void unsubscribe(String subscriptionArn) {
        snsClient.unsubscribe(UnsubscribeRequest.builder()
                .subscriptionArn(subscriptionArn)
                .build());
        log.info("Unsubscribed subscriptionArn={}", subscriptionArn);
    }
}
