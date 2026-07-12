package com.aiknowledgeworkspace.workspacecore.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

@Component
public class DirectUploadDeprecationReporter implements InitializingBean {

    static final String WARNING_MESSAGE = "Processing mode direct_upload is deprecated for normal Project3 operation; "
            + "use kafka_request through the project3 profile. The compatibility path remains temporarily supported "
            + "for rollback and recovery. No removal date has been assigned.";

    private static final Logger logger = LoggerFactory.getLogger(DirectUploadDeprecationReporter.class);

    private final ProcessingProperties processingProperties;

    public DirectUploadDeprecationReporter(ProcessingProperties processingProperties) {
        this.processingProperties = processingProperties;
    }

    @Override
    public void afterPropertiesSet() {
        if (processingProperties.getTriggerMode() == ProcessingTriggerMode.DIRECT_UPLOAD) {
            logger.warn(WARNING_MESSAGE);
        }
    }
}
