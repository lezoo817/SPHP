package com.sphp.patient;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * C 端后端启动类（用户挂号/问诊/购药/健康档案）。
 *
 * ComponentScan 扫描 com.sphp.patient（本端业务）和 com.sphp.shared（共享组件）。
 * MapperScan 扫描 com.sphp.patient 下所有 mapper 接口。
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.sphp.patient", "com.sphp.shared"})
@MapperScan("com.sphp.patient.**.mapper")
public class PatientApplication {

    public static void main(String[] args) {
        SpringApplication.run(PatientApplication.class, args);
    }
}
