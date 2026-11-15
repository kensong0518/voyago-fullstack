package com.voyago.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 啟用 @Scheduled（目前只有 AI 紀錄的每日清理）。 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
