package com.zdf.flowsvr.enums;

/**
 * 任务状态 任务生命周期中的一个阶段
 */
public enum TaskStatus {
    /** 等待执行 */
    PENDING(0x01),
    /** 执行中 */
    EXECUTING(0x02),
    /** 执行成功 */
    SUCCESS(0x04),
    /** 执行失败 */
    FAIL(0x08);

    private TaskStatus(int status) {
        this.status = status;
    }
    private int status;

    public int getStatus() {
        return this.status;
    }
}
