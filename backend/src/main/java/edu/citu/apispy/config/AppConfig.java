package edu.citu.apispy.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AdaptiveLearningProperties.class)
public class AppConfig {
}
