package com.genai.datapipeline.dataservice.common;

public final class StatusConstants {

    private StatusConstants() {
    }

    public static final int TASK_CREATED = 0;

    public static final int TASK_RUNNING = 1;

    public static final int TASK_PAUSED = 2;

    public static final int TASK_COMPLETED = 3;

    public static final int TASK_FAILED = 4;

    public static final int TASK_STOPPED = 5;

    public static final int ITEM_PENDING = 0;

    public static final int ITEM_PROCESSING = 1;

    public static final int ITEM_PENDING_EXPERT_ANNOTATION = 2;

    public static final int ITEM_AI_ANNOTATED = ITEM_PENDING_EXPERT_ANNOTATION;

    public static final int ITEM_COMPLETED = 3;

    public static final int ITEM_FAILED = 4;

    public static final int ITEM_SKIPPED = 5;

    public static final int WORKER_OFFLINE = 0;

    public static final int WORKER_ONLINE = 1;

    public static final int WORKER_BUSY = 2;

    public static final int WORKER_DISABLED = 3;
}
