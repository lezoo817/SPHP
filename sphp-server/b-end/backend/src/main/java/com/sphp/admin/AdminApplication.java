package com.sphp.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * B 端后端启动类（医院管理/医生工作台/处方药品）。
 *
 * ComponentScan 扫描 com.sphp.admin（本端业务）和 com.sphp.shared（共享组件）。
 * MapperScan 扫描 com.sphp.admin 下所有 mapper 接口。
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.sphp.admin", "com.sphp.shared"})
@MapperScan("com.sphp.admin.**.mapper")
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
