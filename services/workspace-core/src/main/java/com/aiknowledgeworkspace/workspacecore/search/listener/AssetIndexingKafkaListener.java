package com.aiknowledgeworkspace.workspacecore.search.listener;

import com.aiknowledgeworkspace.workspacecore.search.AssetIndexingEventRejectedException;
import com.aiknowledgeworkspace.workspacecore.search.AssetIndexingEventHandler;
import com.aiknowledgeworkspace.workspacecore.search.AssetIndexingHandleResult;
import com.aiknowledgeworkspace.workspacecore.search.AssetSearchIndexJobStatus;
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
        name = "indexing-listener-enabled",
        havingValue = "true"
)
class AssetIndexingKafkaListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(AssetIndexingKafkaListener.class);

    private final AssetIndexingEventHandler assetIndexingEventHandler;

    AssetIndexingKafkaListener(AssetIndexingEventHandler assetIndexingEventHandler) {
        this.assetIndexingEventHandler = assetIndexingEventHandler;
    }

    @KafkaListener(
            topics = "${workspace.kafka.indexing-requested-topic}",
            groupId = "${workspace.kafka.indexing-consumer-group}",
            containerFactory = AssetIndexingKafkaListenerConfiguration.CONTAINER_FACTORY_BEAN_NAME,
            autoStartup = "${workspace.kafka.indexing-listener-enabled:false}"
    )
    void onMessage(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            AssetIndexingHandleResult result = assetIndexingEventHandler.handle(record.value());
            acknowledgeHandledResult(record, acknowledgment, result);
        } catch (AssetIndexingEventRejectedException exception) {
            LOGGER.warn(
                    "Rejected asset indexing Kafka record at {}-{} offset {}: {}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    exception.getMessage()
            );
            acknowledgment.acknowledge();
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Unexpected asset indexing Kafka listener failure at {}-{} offset {}; leaving offset uncommitted",
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
            AssetIndexingHandleResult result
    ) {
        if (result.status() == AssetSearchIndexJobStatus.INDEXED
                || result.status() == AssetSearchIndexJobStatus.SUPERSEDED
                || result.status() == AssetSearchIndexJobStatus.FAILED) {
            LOGGER.info(
                    "Acknowledging asset indexing event {} job={} status={} at {}-{} offset {}",
                    result.eventId(),
                    result.indexingJobId(),
                    result.status(),
                    record.topic(),
                    record.partition(),
                    record.offset()
            );
            acknowledgment.acknowledge();
            return;
        }

        LOGGER.error(
                "Asset indexing event {} returned unexpected job status {} at {}-{} offset {}; leaving offset uncommitted",
                result.eventId(),
                result.status(),
                record.topic(),
                record.partition(),
                record.offset()
        );
        throw new IllegalStateException("Unexpected asset indexing job status: " + result.status());
    }
}
