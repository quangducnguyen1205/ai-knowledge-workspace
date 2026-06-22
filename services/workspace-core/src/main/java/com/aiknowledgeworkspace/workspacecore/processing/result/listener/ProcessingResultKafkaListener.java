package com.aiknowledgeworkspace.workspacecore.processing.result.listener;

import com.aiknowledgeworkspace.workspacecore.processing.result.ConsumedProcessingResultEventStatus;
import com.aiknowledgeworkspace.workspacecore.processing.result.ProcessingResultEventHandler;
import com.aiknowledgeworkspace.workspacecore.processing.result.ProcessingResultEventRejectedException;
import com.aiknowledgeworkspace.workspacecore.processing.result.ProcessingResultHandleResult;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "workspace.kafka",
        name = "processing-result-listener-enabled",
        havingValue = "true"
)
class ProcessingResultKafkaListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessingResultKafkaListener.class);

    private final ProcessingResultEventHandler processingResultEventHandler;

    ProcessingResultKafkaListener(ProcessingResultEventHandler processingResultEventHandler) {
        this.processingResultEventHandler = processingResultEventHandler;
    }

    @KafkaListener(
            topics = "#{@workspaceKafkaProperties.processingResultTopic}",
            groupId = "#{@workspaceKafkaProperties.processingResultConsumerGroup}",
            containerFactory = ProcessingResultKafkaListenerConfiguration.CONTAINER_FACTORY_BEAN_NAME,
            autoStartup = "#{@workspaceKafkaProperties.processingResultListenerEnabled}"
    )
    void onMessage(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            ProcessingResultHandleResult result = processingResultEventHandler.handle(record.value());
            acknowledgeHandledResult(record, acknowledgment, result);
        } catch (ProcessingResultEventRejectedException exception) {
            LOGGER.warn(
                    "Rejected processing result Kafka record at {}-{} offset {}: {}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    exception.getMessage()
            );
            acknowledgment.acknowledge();
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Unexpected processing result Kafka listener failure at {}-{} offset {}; leaving offset uncommitted",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    exception
            );
            throw exception;
        }
    }

    private void acknowledgeHandledResult(
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment,
            ProcessingResultHandleResult result
    ) {
        if (result.status() == ConsumedProcessingResultEventStatus.APPLIED) {
            LOGGER.info(
                    "Acknowledging processing result event {} with status {} applied={} at {}-{} offset {}",
                    result.eventId(),
                    result.status(),
                    result.applied(),
                    record.topic(),
                    record.partition(),
                    record.offset()
            );
            acknowledgment.acknowledge();
            return;
        }

        if (result.status() == ConsumedProcessingResultEventStatus.FAILED) {
            LOGGER.warn(
                    "Acknowledging processing result event {} with durable product-side status FAILED at {}-{} offset {}; manual recovery is required",
                    result.eventId(),
                    record.topic(),
                    record.partition(),
                    record.offset()
            );
            acknowledgment.acknowledge();
            return;
        }

        LOGGER.error(
                "Processing result event {} returned unexpected status {} at {}-{} offset {}; leaving offset uncommitted",
                result.eventId(),
                result.status(),
                record.topic(),
                record.partition(),
                record.offset()
        );
        throw new IllegalStateException("Unexpected processing result handler status: " + result.status());
    }
}
