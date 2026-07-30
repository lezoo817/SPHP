package com.sphp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 智愈先锋统一后端启动类。
 *
 * 负责装配公共能力、B 端医院管理业务和 C 端患者业务，应用统一监听 8080 端口。
 */
@SpringBootApplication(scanBasePackages = "com.sphp")
@MapperScan({"com.sphp.admin.**.mapper", "com.sphp.patient.**.mapper"})
public class SphpApplication {

    /**
     * 启动智愈先锋统一后端应用。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        // 启动统一的 Spring Boot 应用上下文。
        SpringApplication.run(SphpApplication.class, args);
    }
}
