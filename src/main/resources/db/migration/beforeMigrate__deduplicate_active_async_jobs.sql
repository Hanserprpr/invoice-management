SET @async_job_table_exists = (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'async_job'
);

SET @deduplicate_active_jobs = IF(
    @async_job_table_exists = 1,
    'UPDATE async_job job
     JOIN (
       SELECT id
       FROM (
         SELECT id,
                ROW_NUMBER() OVER (
                  PARTITION BY organization_id, job_type, target_type, target_id
                  ORDER BY CASE WHEN status = ''RUNNING'' THEN 0 ELSE 1 END,
                           created_at DESC, id DESC
                ) AS active_rank
         FROM async_job
         WHERE status IN (''PENDING'', ''RUNNING'')
       ) ranked
       WHERE active_rank > 1
     ) duplicate ON duplicate.id = job.id
     SET job.status = ''FAILED'',
         job.progress = 100,
         job.error_code = ''DUPLICATE_ACTIVE_JOB_MIGRATED'',
         job.error_message = ''升级时终止重复活跃任务'',
         job.next_attempt_at = NULL,
         job.finished_at = CURRENT_TIMESTAMP(3)',
    'SELECT 1'
);

PREPARE deduplicate_active_jobs_statement FROM @deduplicate_active_jobs;
EXECUTE deduplicate_active_jobs_statement;
DEALLOCATE PREPARE deduplicate_active_jobs_statement;
