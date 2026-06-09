package com.voyago.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI voyagoOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("歐洲自助遊 API")
                .description("Spring Boot 前後端分離旅遊平台 REST API（JWT 驗證、Google 登入）")
                .version("v1.0.0"));
    }
}
