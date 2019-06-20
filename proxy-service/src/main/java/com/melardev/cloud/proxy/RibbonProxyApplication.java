package com.melardev.cloud.proxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.netflix.hystrix.EnableHystrix;
import org.springframework.cloud.netflix.hystrix.dashboard.EnableHystrixDashboard;

@SpringBootApplication
@EnableHystrixDashboard
@EnableHystrix
@EnableDiscoveryClient
public class RibbonProxyApplication {
    public static void main(String[] args) {
        SpringApplication.run(RibbonProxyApplication.class, args);
    }
}
