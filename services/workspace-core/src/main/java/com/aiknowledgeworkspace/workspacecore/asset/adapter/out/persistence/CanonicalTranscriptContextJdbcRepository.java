package com.aiknowledgeworkspace.workspacecore.asset.adapter.out.persistence;

import com.aiknowledgeworkspace.workspacecore.asset.application.model.CanonicalTranscriptContextRow;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.CanonicalTranscriptContextTarget;
import com.aiknowledgeworkspace.workspacecore.asset.application.model.CanonicalTranscriptContextWindow;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class CanonicalTranscriptContextJdbcRepository {

    private static final String QUERY_TEMPLATE = """
            WITH requested_targets(requested_transcript_row_id, requested_segment_index) AS (
                VALUES %s
            )
            SELECT
                target.requested_transcript_row_id,
                target.requested_segment_index,
                previous_row.transcript_row_id AS previous_transcript_row_id,
                previous_row.segment_index AS previous_segment_index,
                previous_row.start_ms AS previous_start_ms,
                previous_row.end_ms AS previous_end_ms,
                previous_row.text AS previous_text,
                previous_row.created_at AS previous_created_at,
                hit.transcript_row_id AS hit_transcript_row_id,
                hit.segment_index AS hit_segment_index,
                hit.start_ms AS hit_start_ms,
                hit.end_ms AS hit_end_ms,
                hit.text AS hit_text,
                hit.created_at AS hit_created_at,
                next_row.transcript_row_id AS next_transcript_row_id,
                next_row.segment_index AS next_segment_index,
                next_row.start_ms AS next_start_ms,
                next_row.end_ms AS next_end_ms,
                next_row.text AS next_text,
                next_row.created_at AS next_created_at
            FROM requested_targets target
            JOIN asset_transcript_rows hit
             ON hit.asset_id = CAST(? AS uuid)
             AND hit.segment_index IS NOT NULL
             AND hit.text ~ '[^[:space:]]'
             AND (
                    (
                        target.requested_transcript_row_id IS NOT NULL
                        AND hit.transcript_row_id = target.requested_transcript_row_id
                    )
                    OR (
                        target.requested_transcript_row_id IS NULL
                        AND target.requested_segment_index IS NOT NULL
                        AND hit.segment_index = target.requested_segment_index
                    )
             )
            LEFT JOIN LATERAL (
                SELECT candidate.*
                FROM asset_transcript_rows candidate
                WHERE candidate.asset_id = hit.asset_id
                  AND candidate.segment_index IS NOT NULL
                  AND candidate.text ~ '[^[:space:]]'
                  AND candidate.segment_index < hit.segment_index
                ORDER BY candidate.segment_index DESC
                LIMIT 1
            ) previous_row ON true
            LEFT JOIN LATERAL (
                SELECT candidate.*
                FROM asset_transcript_rows candidate
                WHERE candidate.asset_id = hit.asset_id
                  AND candidate.segment_index IS NOT NULL
                  AND candidate.text ~ '[^[:space:]]'
                  AND candidate.segment_index > hit.segment_index
                ORDER BY candidate.segment_index ASC
                LIMIT 1
            ) next_row ON true
            ORDER BY hit.segment_index, hit.transcript_row_id
            """;

    private final JdbcTemplate jdbcTemplate;

    CanonicalTranscriptContextJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<CanonicalTranscriptContextWindow> load(
            UUID assetId,
            List<CanonicalTranscriptContextTarget> targets
    ) {
        if (targets.isEmpty()) {
            return List.of();
        }

        String targetPlaceholders = String.join(
                ", ",
                Collections.nCopies(
                        targets.size(),
                        "(CAST(? AS varchar(255)), CAST(? AS integer))"
                )
        );
        String query = QUERY_TEMPLATE.formatted(targetPlaceholders);
        return jdbcTemplate.query(
                query,
                statement -> bind(statement, assetId, targets),
                this::mapWindow
        );
    }

    private void bind(
            PreparedStatement statement,
            UUID assetId,
            List<CanonicalTranscriptContextTarget> targets
    ) throws SQLException {
        int parameterIndex = 1;
        for (CanonicalTranscriptContextTarget target : targets) {
            if (target.transcriptRowId() == null) {
                statement.setNull(parameterIndex++, Types.VARCHAR);
            } else {
                statement.setString(parameterIndex++, target.transcriptRowId());
            }
            if (target.segmentIndex() == null) {
                statement.setNull(parameterIndex++, Types.INTEGER);
            } else {
                statement.setInt(parameterIndex++, target.segmentIndex());
            }
        }
        statement.setObject(parameterIndex, assetId);
    }

    private CanonicalTranscriptContextWindow mapWindow(ResultSet resultSet, int rowNumber) throws SQLException {
        CanonicalTranscriptContextRow matchedRow = mapRow(resultSet, "hit");
        if (matchedRow == null) {
            throw new IllegalStateException("Canonical context query returned no matched row");
        }

        List<CanonicalTranscriptContextRow> orderedRows = new ArrayList<>(3);
        addIfPresent(orderedRows, mapRow(resultSet, "previous"));
        orderedRows.add(matchedRow);
        addIfPresent(orderedRows, mapRow(resultSet, "next"));
        return new CanonicalTranscriptContextWindow(
                resultSet.getString("requested_transcript_row_id"),
                resultSet.getObject("requested_segment_index", Integer.class),
                matchedRow,
                orderedRows
        );
    }

    private CanonicalTranscriptContextRow mapRow(ResultSet resultSet, String prefix) throws SQLException {
        Integer segmentIndex = resultSet.getObject(prefix + "_segment_index", Integer.class);
        if (segmentIndex == null) {
            return null;
        }
        return new CanonicalTranscriptContextRow(
                resultSet.getString(prefix + "_transcript_row_id"),
                segmentIndex,
                resultSet.getObject(prefix + "_start_ms", Long.class),
                resultSet.getObject(prefix + "_end_ms", Long.class),
                resultSet.getString(prefix + "_text"),
                resultSet.getString(prefix + "_created_at")
        );
    }

    private void addIfPresent(
            List<CanonicalTranscriptContextRow> rows,
            CanonicalTranscriptContextRow candidate
    ) {
        if (candidate != null) {
            rows.add(candidate);
        }
    }
}
