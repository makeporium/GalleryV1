SET @db_name = DATABASE();

SET @exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @db_name AND table_name = 'weekly_scores' AND column_name = 'week_id'
);
SET @sql = IF(@exists = 0, 'ALTER TABLE weekly_scores ADD COLUMN week_id BIGINT UNSIGNED NULL', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @db_name AND table_name = 'weekly_scores' AND column_name = 'user_id'
);
SET @sql = IF(@exists = 0, 'ALTER TABLE weekly_scores ADD COLUMN user_id BIGINT UNSIGNED NULL', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @db_name AND table_name = 'score_events' AND column_name = 'week_id'
);
SET @sql = IF(@exists = 0, 'ALTER TABLE score_events ADD COLUMN week_id BIGINT UNSIGNED NULL', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @db_name AND table_name = 'score_events' AND column_name = 'user_id'
);
SET @sql = IF(@exists = 0, 'ALTER TABLE score_events ADD COLUMN user_id BIGINT UNSIGNED NULL', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
