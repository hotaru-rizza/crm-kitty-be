package com.inkflow.crm;

import com.inkflow.crm.config.DevStartupListener;
import com.inkflow.crm.config.LocalEnvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
@EnableAsync
public class InkFlowCrmApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(InkFlowCrmApplication.class);
        application.setDefaultProperties(LocalEnvLoader.loadDefaults());
        application.addListeners(new DevStartupListener());
        application.run(args);
    }
}
