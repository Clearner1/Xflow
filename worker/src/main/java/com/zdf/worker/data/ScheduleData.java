package com.zdf.worker.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ScheduleData {
    String traceId;  // 用于追踪和标识特定任务或请求的唯一标识符
    String errMsg; //用于存储错误信息（如果有的话）
    String cost; //记录任务执行的耗时
}
