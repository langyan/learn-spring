package com.lin.spring.loki.service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class LoggingTestRunner implements CommandLineRunner {
    
    private static final Logger log = LoggerFactory.getLogger(LoggingTestRunner.class);
    
    @Override
    public void run(String... args) {
        log.info("应用启动成功");
        log.warn("这是一个警告消息");
        log.error("这是一个错误消息", new RuntimeException("测试异常"));
    }
}