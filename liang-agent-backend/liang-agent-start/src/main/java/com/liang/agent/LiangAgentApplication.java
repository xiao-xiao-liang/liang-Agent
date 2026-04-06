package com.liang.agent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启动类
 */
@EnableScheduling
@SpringBootApplication
@MapperScan("com.liang.agent.service.mapper")
public class LiangAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(LiangAgentApplication.class, args);
    }
}
