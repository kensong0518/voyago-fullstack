package com.voyago;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

@SpringBootApplication
public class VoyagoApplication {
    public static void main(String[] args) {
        SpringApplication.run(VoyagoApplication.class, args);
    }

    /** 系統時鐘注入為 Bean，讓依賴「現在時間」的邏輯（如出發日驗證）可在測試中替換為固定時間。 */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
