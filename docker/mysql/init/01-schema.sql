CREATE DATABASE IF NOT EXISTS genai_data_pipeline
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE genai_data_pipeline;

CREATE TABLE IF NOT EXISTS data_task (
    id BIGINT NOT NULL COMMENT 'Primary key',
    task_name VARCHAR(128) NOT NULL COMMENT 'Task name',
    task_type VARCHAR(64) NOT NULL COMMENT 'Task type: import, clean, deduplicate, export',
    source_type VARCHAR(64) NOT NULL COMMENT 'Source type: file, mysql, api, object_storage',
    source_uri VARCHAR(512) NOT NULL COMMENT 'Source location',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0 created, 1 running, 2 paused, 3 completed, 4 failed, 5 stopped',
    total_count BIGINT NOT NULL DEFAULT 0 COMMENT 'Total data item count',
    processed_count BIGINT NOT NULL DEFAULT 0 COMMENT 'Processed item count',
    success_count BIGINT NOT NULL DEFAULT 0 COMMENT 'Successful item count',
    failed_count BIGINT NOT NULL DEFAULT 0 COMMENT 'Failed item count',
    assigned_worker_id BIGINT NULL COMMENT 'Assigned worker node id',
    started_at DATETIME NULL COMMENT 'Task start time',
    finished_at DATETIME NULL COMMENT 'Task finish time',
    remark VARCHAR(512) NULL COMMENT 'Remark',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
    PRIMARY KEY (id),
    KEY idx_data_task_status (status),
    KEY idx_data_task_task_type (task_type),
    KEY idx_data_task_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Data cleaning task';

CREATE TABLE IF NOT EXISTS data_item (
    id BIGINT NOT NULL COMMENT 'Primary key',
    task_id BIGINT NOT NULL COMMENT 'Task id',
    source_id VARCHAR(128) NULL COMMENT 'Source side record id',
    data_type VARCHAR(16) NULL COMMENT 'TEXT or CODE',
    raw_content LONGTEXT NOT NULL COMMENT 'Raw text content',
    cleaned_content LONGTEXT NULL COMMENT 'Cleaned text content',
    ai_annotation JSON NULL COMMENT 'AI pre-annotation JSON result',
    expert_annotation JSON NULL COMMENT 'Expert final annotation JSON result',
    expert_id BIGINT NULL COMMENT 'Expert annotator id',
    content_hash CHAR(64) NULL COMMENT 'Content SHA-256 hash',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0 pending, 1 processing, 2 waiting expert annotation, 3 completed, 4 failed, 5 skipped',
    token_count INT NOT NULL DEFAULT 0 COMMENT 'Token count',
    quality_score DECIMAL(5,2) NULL COMMENT 'Quality score',
    error_message VARCHAR(1024) NULL COMMENT 'Error message',
    cleaned_at DATETIME NULL COMMENT 'Cleaned time',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
    PRIMARY KEY (id),
    KEY idx_data_item_task_id (task_id),
    KEY idx_data_item_status (status),
    KEY idx_data_item_hash (content_hash),
    KEY idx_data_item_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Data item';

CREATE TABLE IF NOT EXISTS worker_node (
    id BIGINT NOT NULL COMMENT 'Primary key',
    node_code VARCHAR(128) NOT NULL COMMENT 'Unique worker node code',
    host VARCHAR(128) NOT NULL COMMENT 'Worker host',
    port INT NOT NULL COMMENT 'Worker port',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0 offline, 1 online, 2 busy, 3 disabled',
    max_concurrency INT NOT NULL DEFAULT 1 COMMENT 'Max concurrent tasks',
    current_load INT NOT NULL DEFAULT 0 COMMENT 'Current running task count',
    last_heartbeat_time DATETIME NULL COMMENT 'Last heartbeat time',
    version VARCHAR(64) NULL COMMENT 'Worker version',
    remark VARCHAR(512) NULL COMMENT 'Remark',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Create time',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'Logical delete flag',
    PRIMARY KEY (id),
    UNIQUE KEY uk_worker_node_code (node_code),
    KEY idx_worker_node_status (status),
    KEY idx_worker_node_heartbeat (last_heartbeat_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Worker node';
