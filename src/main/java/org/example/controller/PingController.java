package org.example.controller;


import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@EnableWebMvc
public class PingController {
    @RequestMapping(path = "/ping", method = RequestMethod.GET)
    public Map<String, String> ping() {
        Map<String, String> pong = new HashMap<>();
        log.info("Received a request to /ping endpoint.");
        pong.put("pong", "Hello, World! This is a response from the /ping endpoint. You can customize this message as needed.");
        return pong;
    }
}
