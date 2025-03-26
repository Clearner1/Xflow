package com.zdf.flowsvr.enums;

/**
 * 错误状态和信息
 */
public enum ErrorStatus {
    /** 操作成功 */
    SUCCESS(0, "ok"),
    /** 输入参数无效 */
    ERR_INPUT_INVALID(8020, "input invalid"),
    /** 绑定失败 */
    ERR_SHOULD_BIND(8021, "should bind failed"),
    /** JSON序列化失败 */
    ERR_JSON_MARSHAL(8022, "json marshal failed"),
    /** 获取任务信息失败 */
    ERR_GET_TASK_INFO(8035, "get task info failed"),
    /** 获取任务处理进度失败 */
    ERR_GET_TASK_HANDLE_PROCESS(8036, "get task handle process Failed"),
    /** 创建任务失败 */
    ERR_CREATE_TASK(8037, "create task failed"),
    /** 从数据库获取任务列表失败 */
    ERR_GET_TASK_LIST_FROM_DB(8038, "get task list from db failed"),
    /** 从数据库获取任务位置设置失败 */
    ERR_GET_TASK_SET_POS_FROM_DB(8039, "get task set pos from db failed"),
    /** 增加当前重试次数失败 */
    ERR_INCREASE_CRT_RETRY_NUM(8040, "increase crt retry num failed"),
    /** 设置任务失败 */
    ERR_SET_TASK(8041, "set task failed"),
    /** 获取任务位置失败 */
    ERR_GET_TASK_POS(8042, "get task pos failed"),
    /** 获取处理计数失败 */
    ERR_GET_PROCESSING_COUNT(8043, "get processing count failed"),
    /** 设置用户优先级失败 */
    ERR_SET_USER_PRIORITY(8045, "set user priority failed"),
    /** 获取任务配置失败 */
    ERR_GET_TASK_CFG_FROM_DB(8046, "get task cfg failed"),
    /** 设置任务配置失败 */
    ERR_SET_TASK_CFG_FROM_DB(8047, "set task cfg failed");

    private int errCode;
    private String msg;
    private ErrorStatus(int errCode, String msg) {
        this.errCode = errCode;
        this.msg = msg;
    }

    public int getErrCode() {
        return errCode;
    }

    public String getMsg() {
        return msg;
    }
}
