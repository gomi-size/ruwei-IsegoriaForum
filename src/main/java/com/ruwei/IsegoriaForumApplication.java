package com.ruwei;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
@SpringBootApplication
@MapperScan("com.ruwei.mapper")
@EnableCaching
public class IsegoriaForumApplication {

    public static void main(String[] args) {
        System.out.println(SpringApplication.run(IsegoriaForumApplication.class, args));
    }

}
