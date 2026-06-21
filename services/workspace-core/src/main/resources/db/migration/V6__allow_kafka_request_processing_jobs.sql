ALTER TABLE processing_jobs
    ALTER COLUMN fastapi_task_id DROP NOT NULL;

ALTER TABLE processing_jobs
    ALTER COLUMN fastapi_video_id DROP NOT NULL;
