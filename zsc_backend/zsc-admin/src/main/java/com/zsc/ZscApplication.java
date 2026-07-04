package com.zsc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启动程序
 *
 * @author zsc
 */
@EnableAsync
@EnableScheduling
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class ZscApplication
{
    public static void main(String[] args)
    {
//         System.setProperty("spring.devtools.restart.enabled", "false");
        SpringApplication.run(ZscApplication.class, args);
        System.out.println("票据系统启动完成 0^O");
    }
}
