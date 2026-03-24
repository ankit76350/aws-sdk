package org.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

import org.example.config.AwsConfig;
import org.example.controller.OrderController;
import org.example.controller.OtpController;
import org.example.controller.PingController;
import org.example.controller.SqsController;
import org.example.controller.SnsTopicController;
import org.example.service.OrderService;
import org.example.service.SnsService;
import org.example.service.SnsTopicService;
import org.example.service.SqsService;
import org.example.worker.OrderWorker;


@SpringBootApplication
@EnableScheduling
// We use direct @Import instead of @ComponentScan to speed up cold starts
@Import({
        PingController.class,
        AwsConfig.class,
        SqsService.class,
        SqsController.class,
        OrderService.class,
        OrderController.class,
        OrderWorker.class,
        SnsService.class,
        OtpController.class,
        SnsTopicService.class,
        SnsTopicController.class
})
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}