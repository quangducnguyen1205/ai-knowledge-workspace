package com.aiknowledgeworkspace.workspacecore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WorkspaceCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkspaceCoreApplication.class, args);
    }
}
