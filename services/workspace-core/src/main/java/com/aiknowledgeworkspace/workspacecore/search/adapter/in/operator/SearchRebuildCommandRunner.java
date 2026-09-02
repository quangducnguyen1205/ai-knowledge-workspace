package com.aiknowledgeworkspace.workspacecore.search.adapter.in.operator;

import com.aiknowledgeworkspace.workspacecore.search.application.configuration.SearchRebuildCommand;
import com.aiknowledgeworkspace.workspacecore.search.application.configuration.SearchRebuildProperties;
import com.aiknowledgeworkspace.workspacecore.search.application.result.SearchIndexRebuildResult;
import com.aiknowledgeworkspace.workspacecore.search.application.service.SearchIndexRebuildService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * The operator entry point for rebuilding the search projection. Deliberate by construction: it
 * does nothing unless {@code workspace.search.rebuild.command} names an action, so no ordinary
 * start — and no scheduler — can reconstruct or disturb the index behind anyone's back.
 *
 * <p>A run that could not rebuild every eligible asset fails the process rather than printing a
 * result that reads like success.
 */
@Component
public class SearchRebuildCommandRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchRebuildCommandRunner.class);

    private final SearchRebuildProperties properties;
    private final SearchIndexRebuildService searchIndexRebuildService;
    private final ConfigurableApplicationContext applicationContext;

    public SearchRebuildCommandRunner(
            SearchRebuildProperties properties,
            SearchIndexRebuildService searchIndexRebuildService,
            ConfigurableApplicationContext applicationContext
    ) {
        this.properties = properties;
        this.searchIndexRebuildService = searchIndexRebuildService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.getCommand() == SearchRebuildCommand.NONE) {
            return;
        }

        try {
            switch (properties.getCommand()) {
                case REPORT_CANDIDATES -> reportCandidates();
                case REBUILD_ALL -> rebuildAll();
                case NONE -> {
                }
            }
        } finally {
            applicationContext.close();
        }
    }

    private void reportCandidates() {
        int candidates = searchIndexRebuildService.countRebuildCandidates();
        LOGGER.info("Search rebuild candidate report completed eligible={}", candidates);
        System.out.println("SPRING_SEARCH_REBUILD_CANDIDATES eligible=%d".formatted(candidates));
    }

    private void rebuildAll() {
        SearchIndexRebuildResult result = searchIndexRebuildService.rebuildAll();
        System.out.println("SPRING_SEARCH_REBUILD %s".formatted(result.summary()));
        if (result.hasFailures()) {
            throw new IllegalStateException(
                    "Search rebuild did not complete for every eligible asset: " + result.summary());
        }
    }
}
