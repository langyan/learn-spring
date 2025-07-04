package com.lin.spring.websocket.controller;

import com.lin.spring.websocket.model.User;
import com.lin.spring.websocket.service.RedisPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Controller
@RequiredArgsConstructor
public class WebSocketController {



    @GetMapping("/socket")
    public String greetingForm(Model model) {
//        model.addAttribute("greeting", new Greeting());
        return "socket";
    }

}
