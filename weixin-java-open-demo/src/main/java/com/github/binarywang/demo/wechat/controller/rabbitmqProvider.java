package com.github.binarywang.demo.wechat.controller;

import java.util.Date;

import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

@Controller
public class rabbitmqProvider {
	    @Autowired
	    private AmqpTemplate rabbitTemplate;

	    public void send() {
	        String context = "hello " + new Date();
	        System.out.println("Sender : " + context);
	        this.rabbitTemplate.convertAndSend("hello", context);
	    }
}
