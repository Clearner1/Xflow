package com.zdf.worker.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AsyncFlowClientData {
    // 用户id
    private String user_id;
    // 任务类型
    private String task_type;
    // 任务阶段
    private String task_stage;
    // 调度日志
    private String schedule_log;
    // 任务上下文
    private String task_context;
}
