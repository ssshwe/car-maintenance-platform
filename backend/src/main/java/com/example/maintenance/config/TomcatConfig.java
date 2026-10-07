package com.example.maintenance.config;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TomcatConfig {
    @Bean WebServerFactoryCustomizer<TomcatServletWebServerFactory> nio2Connector() {
        // 使用Tomcat标准NIO2连接器，避开本机JDK17的AF_UNIX唤醒管道兼容问题。
        return factory -> factory.setProtocol("org.apache.coyote.http11.Http11Nio2Protocol");
    }
}
