package com.zdf.flowsvr.service.impl;

import com.zdf.flowsvr.constant.ErrorStatusReturn;
import com.zdf.flowsvr.constant.Task;
import com.zdf.flowsvr.dao.AsyncFlowTaskDao;
import com.zdf.flowsvr.dao.ScheduleConfigDao;
import com.zdf.flowsvr.dao.TSchedulePosDao;
import com.zdf.flowsvr.data.*;
import com.zdf.flowsvr.enums.ErrorStatus;
import com.zdf.flowsvr.enums.TaskStatus;
import com.zdf.flowsvr.service.AsyncTaskService;
import com.zdf.flowsvr.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author zhangdafeng
 */
@Service
public class AsyncTaskServiceImpl implements AsyncTaskService {
    Logger logger = LoggerFactory.getLogger(AsyncTaskServiceImpl.class);

    @Autowired
    private AsyncFlowTaskDao asyncFlowTaskDao;

    @Autowired
    private ScheduleConfigDao scheduleConfigDao;

    @Autowired
    private TSchedulePosDao tSchedulePosDao;

    // 之前套壳，算是解开里面的信息
    private AsyncFlowClientData getAsyncFlowClientData(AsyncTaskRequest asyncTaskGroup) {
        AsyncFlowClientData asyncFlowClientData = asyncTaskGroup.getTaskData();
        return asyncFlowClientData;
    }

    @Override
    public <T> ReturnStatus<T> createTask(AsyncTaskRequest asyncTaskRequest) {
        // 从请求中提取任务相关的客户端数据（如任务类型、上下文信息等）
        AsyncFlowClientData asyncFlowClientData = getAsyncFlowClientData(asyncTaskRequest);

        // 用于存储任务位置信息的对象
        TSchedulePos taskPos = null;
        try {
            // 根据任务类型查询 t_schedule_pos 表，获取该类型任务应该存储在哪个分表范围
            taskPos = tSchedulePosDao.getTaskPos(asyncFlowClientData.getTask_type());
        } catch (Exception e) {
            // 如果查询失败，返回错误状态
            return ErrorStatusReturn.ERR_GET_TASK_POS;
        }
        if (taskPos == null) {
            // 如果未找到任务位置配置，记录错误日志
            logger.error("db.TaskPosNsp.GetTaskPos failed.");
        }

        // 根据任务位置信息和任务类型，生成具体的分表名称（如 t_video_tasks_1）
        // t + 任务类型 + 表名 + 末尾位置表
        // "t_" + taskType.toLowerCase() + "_" + this.tableName() + "_" + pos;
        String tableName = getTableName(taskPos.getScheduleEndPos(), asyncFlowClientData.getTask_type());

        // 用于存储任务类型配置的对象
        ScheduleConfig taskTypeCfg;
        try {
            // 根据任务类型查询 t_schedule_cfg 表，获取该类型任务的调度配置（如最大重试次数、调度间隔等）
            taskTypeCfg = scheduleConfigDao.getTaskTypeCfg(asyncFlowClientData.getTask_type());
        } catch (Exception e) {
            // 如果查询失败，记录错误日志并返回错误状态
            logger.error("Visit t_task_type_cfg error");
            return ErrorStatusReturn.ERR_GET_TASK_SET_POS_FROM_DB;
        }

        // 创建新的异步任务对象
        AsyncFlowTask asyncFlowTask = new AsyncFlowTask();
        // 生成唯一的任务ID（基于任务类型、位置信息和表名）
        String taskId = getTaskId(asyncFlowClientData.getTask_type(), taskPos.getScheduleEndPos(), tableName);
        try {
            // 使用客户端数据、任务ID和任务类型配置填充任务对象
            // 设置任务的初始状态、重试次数、创建时间等属性
            fillTaskModel(asyncFlowClientData, asyncFlowTask, taskId, taskTypeCfg);
            // 将任务对象保存到对应的分表中
            asyncFlowTaskDao.create(tableName, asyncFlowTask);
        } catch (Exception e) {
            // 如果创建任务失败，打印异常堆栈，记录错误日志并返回错误状态
            e.printStackTrace();
            logger.error("create task error");
            return ErrorStatusReturn.ERR_CREATE_TASK;
        }

        // 创建任务结果对象，包含新生成的任务ID
        TaskResult taskResult = new TaskResult(taskId);
        // 返回成功状态和任务结果
        return new ReturnStatus(taskResult);
    }

    // tableName稳定输出task 🤣
    private String getTaskId(String taskType, int taskPos, String tableName) {
        return Utils.getTaskId() + "_" + taskType + "_" + tableName() + "_" + taskPos;
    }

    public void fillTaskModel(AsyncFlowClientData asyncFlowClientData, AsyncFlowTask asyncFlowTask, String taskId,
            ScheduleConfig taskTypeCfg) {
        asyncFlowTask.setTask_id(taskId);
        asyncFlowTask.setUser_id(asyncFlowClientData.getUser_id());
        asyncFlowTask.setTask_type(asyncFlowClientData.getTask_type());
        asyncFlowTask.setTask_stage(asyncFlowClientData.getTask_stage());
        Long currentTime = System.currentTimeMillis();
        asyncFlowTask.setModify_time(currentTime);
        asyncFlowTask.setMax_retry_interval(taskTypeCfg.getRetry_interval());
        asyncFlowTask.setMax_retry_num(taskTypeCfg.getMax_retry_num());
        asyncFlowTask.setCrt_retry_num(0);
        asyncFlowTask.setOrder_time(currentTime);
        asyncFlowTask.setCreate_time(currentTime);
        asyncFlowTask.setStatus(TaskStatus.PENDING.getStatus());
        asyncFlowTask.setSchedule_log(asyncFlowClientData.getSchedule_log());
        asyncFlowTask.setTask_context(asyncFlowClientData.getTask_context());
    }

    @Override
    public <T> ReturnStatus<T> holdTask(String taskType, int status, int limit) {
        if (limit > Task.MAX_TASK_LIST_LIMIT) {
            limit = Task.MAX_TASK_LIST_LIMIT;
        }
        if (limit == 0) {
            limit = Task.DEFAULT_TASK_LIST_LIMIT;
        }
        TSchedulePos taskPos;
        try {
            // 从数据库中获取任务位置表
            taskPos = tSchedulePosDao.getTaskPos(taskType);
        } catch (Exception e) {
            e.printStackTrace();
            return ErrorStatusReturn.ERR_GET_TASK_SET_POS_FROM_DB;
        }
        // 根据任务类型和任务位置表获取任务列表
        // taskPos.getScheduleBeginPos() 表在哪一个位置
        String tableName = getTableName(taskPos.getScheduleBeginPos(), taskType);
        List<AsyncFlowTask> taskList;
        try {
            // 从数据库中获取任务列表
            taskList = asyncFlowTaskDao.getTaskList(taskType, status, limit, tableName);

        } catch (Exception e) {
            logger.error(ErrorStatus.ERR_GET_TASK_LIST_FROM_DB.getMsg());
            return ErrorStatusReturn.ERR_GET_TASK_LIST_FROM_DB;
        }
        List<AsyncFlowTask> filterList = taskList
                .stream()
                .parallel()// 尝试并行处理以提高效率
                // 过滤条件：
                // 1. 重试次数为0（即首次执行的任务）
                // 2. 最大重试间隔不为0
                // 3. 订单时间小于当前时间
                .filter(asyncFlowTask -> asyncFlowTask.getCrt_retry_num() == 0
                        || asyncFlowTask.getMax_retry_interval() != 0
                                && asyncFlowTask.getOrder_time() <= System.currentTimeMillis())
                .collect(Collectors.toList());
        // 将任务列表转换为任务ID列表
        List<String> ids = conventTaskIdList(filterList);
        if (!ids.isEmpty()) {
            // 更新任务状态为执行中
            asyncFlowTaskDao.updateStatusBatch(ids, TaskStatus.EXECUTING.getStatus(), System.currentTimeMillis(),
                    tableName);
        }
        List<AsyncTaskReturn> taskReturns = getTaskReturnList(filterList);
        TaskList list = new TaskList(taskReturns);
        return new ReturnStatus(list);
    }

    @Override
    public <T> ReturnStatus<T> getTaskList(String taskType, int status, int limit) {
        if (limit > Task.MAX_TASK_LIST_LIMIT) {
            limit = Task.MAX_TASK_LIST_LIMIT;
        }
        if (limit == 0) {
            limit = Task.DEFAULT_TASK_LIST_LIMIT;
        }
        TSchedulePos taskPos;
        try {
            taskPos = tSchedulePosDao.getTaskPos(taskType);
        } catch (Exception e) {
            e.printStackTrace();
            return ErrorStatusReturn.ERR_GET_TASK_SET_POS_FROM_DB;
        }
        String tableName = getTableName(taskPos.getScheduleBeginPos(), taskType);
        List<AsyncFlowTask> taskList;
        try {
            taskList = asyncFlowTaskDao.getTaskList(taskType, status, limit, tableName);

        } catch (Exception e) {
            logger.error(ErrorStatus.ERR_GET_TASK_LIST_FROM_DB.getMsg());
            return ErrorStatusReturn.ERR_GET_TASK_LIST_FROM_DB;
        }
        List<AsyncTaskReturn> taskReturns = getTaskReturns(taskList);
        TaskList list = new TaskList(taskReturns);
        return new ReturnStatus(list);
    }

    private List<AsyncTaskReturn> getTaskReturns(List<AsyncFlowTask> taskList) {
        List<AsyncTaskReturn> taskReturns = new ArrayList<>();
        for (AsyncFlowTask asyncFlowTask : taskList) {
            taskReturns.add(getTaskReturn(asyncFlowTask));
        }
        return taskReturns;
    }

    private AsyncTaskReturn getTaskReturn(AsyncFlowTask asyncFlowTask) {
        AsyncTaskReturn tr = new AsyncTaskReturn(
                asyncFlowTask.getUser_id(),
                asyncFlowTask.getTask_id(),
                asyncFlowTask.getTask_type(),
                asyncFlowTask.getTask_stage(),
                asyncFlowTask.getStatus(),
                asyncFlowTask.getCrt_retry_num(),
                asyncFlowTask.getMax_retry_num(),
                asyncFlowTask.getMax_retry_interval(),
                asyncFlowTask.getSchedule_log(),
                asyncFlowTask.getTask_context(),
                asyncFlowTask.getCreate_time(),
                asyncFlowTask.getModify_time());
        return tr;

    }

    @Override
    public <T> ReturnStatus<T> setTask(AsyncTaskSetRequest asyncTaskSetRequest) {
        AsyncFlowTask asyncFlowTask;
        String tableName = getTableNameById(asyncTaskSetRequest.getTask_id());
        try {
            asyncFlowTask = asyncFlowTaskDao.find(asyncTaskSetRequest.getTask_id(), tableName);
        } catch (Exception e) {
            logger.error(ErrorStatus.ERR_GET_TASK_INFO.getMsg());
            return ErrorStatusReturn.ERR_GET_TASK_INFO;
        }

        if (asyncFlowTask == null) {
            logger.error("db.TaskPosNsp.Find Task failed. TaskId:%s", asyncTaskSetRequest.getTask_id());
            return ErrorStatusReturn.ERR_GET_TASK_INFO;
        }
        if (!isUnUpdate(asyncTaskSetRequest.getStatus())) {
            asyncFlowTask.setStatus(asyncTaskSetRequest.getStatus());
        }
        if (!isNullString(asyncTaskSetRequest.getTask_stage())) {
            asyncFlowTask.setTask_stage(asyncTaskSetRequest.getTask_stage());
        }
        if (!isNullString(asyncTaskSetRequest.getTask_context())) {
            asyncFlowTask.setTask_context(asyncTaskSetRequest.getTask_context());
        }
        if (!isNullString(asyncTaskSetRequest.getSchedule_log())) {
            asyncFlowTask.setSchedule_log(asyncTaskSetRequest.getSchedule_log());
        }
        if (!isUnUpdate(asyncTaskSetRequest.getCrt_retry_num())) {
            asyncFlowTask.setCrt_retry_num(asyncTaskSetRequest.getCrt_retry_num());
        }
        if (!isUnUpdate(asyncTaskSetRequest.getMax_retry_interval())) {
            asyncFlowTask.setMax_retry_interval(asyncTaskSetRequest.getMax_retry_interval());
        }
        if (!isUnUpdate(asyncTaskSetRequest.getMax_retry_num())) {
            asyncFlowTask.setMax_retry_num(asyncTaskSetRequest.getMax_retry_num());
        }
        if (asyncTaskSetRequest.getOrder_time() != 0) {
            asyncFlowTask.setOrder_time(asyncTaskSetRequest.getOrder_time());
        }
        if (!isUnUpdate(asyncTaskSetRequest.getPriority())) {
            asyncFlowTask.setPriority(asyncTaskSetRequest.getPriority());
        }

        asyncFlowTask.setModify_time(System.currentTimeMillis());
        try {
            List<Integer> list = new ArrayList<Integer>() {
                {
                    add(TaskStatus.SUCCESS.getStatus());
                    add(TaskStatus.FAIL.getStatus());
                }
            };
            asyncFlowTaskDao.updateTask(asyncFlowTask, list, tableName);
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(ErrorStatus.ERR_SET_TASK.getMsg());
            return ErrorStatusReturn.ERR_SET_TASK;
        }
        return ErrorStatusReturn.SUCCESS;
    }

    private String getTableNameById(String taskId) {
        String[] strs = taskId.split("_");
        // 298128017035624448_Lark_task_1 提取表名为lark_task_table_1
        String tableName = getTableName(Integer.parseInt(strs[3]), strs[1]);
        return tableName;
    }

    private boolean isUnUpdate(int x) {
        return x == Task.DEFAULT_SET_TASK_STATUS;
    }

    private boolean isNullString(String s) {
        return s.equals(Task.DEFAULT_SET_TASK_STAGE_SCHEDULELOG_CONTEXT);
    }

    /**
     * 获取任务
     * 
     * @param task_id
     * @param <T>
     * @return
     */
    @Override
    public <T> ReturnStatus<T> getTask(String task_id) {
        AsyncFlowTask asyncFlowTask;
        String tableName = getTableNameById(task_id);
        try {
            asyncFlowTask = asyncFlowTaskDao.find(task_id, tableName);
        } catch (Exception e) {
            logger.error("get task info error");
            return ErrorStatusReturn.ERR_GET_TASK_INFO;
        }
        TaskByTaskIdReturn<AsyncTaskReturn> taskByTaskIdReturn = new TaskByTaskIdReturn(getTaskReturn(asyncFlowTask));
        return new ReturnStatus(taskByTaskIdReturn);
    }

    @Override
    public <T> ReturnStatus<T> getTaskByUserIdAndStatus(String user_id, int statusList) {
        List<AsyncFlowTask> asyncFlowTaskList;
        String tableName = getTableName(1, "LarkTask");
        try {
            asyncFlowTaskList = asyncFlowTaskDao.getTaskByUser_idAndStatus(user_id, getStatusList(statusList),
                    tableName);
        } catch (Exception e) {
            logger.error("get task info error");
            return ErrorStatusReturn.ERR_GET_TASK_INFO;
        }
        List<AsyncTaskReturn> taskReturns = getTaskReturns(asyncFlowTaskList);
        TaskList list = new TaskList(taskReturns);
        return new ReturnStatus(list);
    }

    private List<Integer> getStatusList(int status) {
        List<Integer> statusList = new ArrayList<>();
        while (status != 0) {
            int cur = status & -status;
            statusList.add(cur);
            status ^= cur;
        }
        return statusList;
    }

    private List<AsyncTaskReturn> getAsyncTaskReturns(List<AsyncFlowTask> taskList) {
        return getTaskReturnList(taskList);
    }

    private List<AsyncTaskReturn> getTaskReturnList(List<AsyncFlowTask> taskList) {
        List<AsyncTaskReturn> tasks = new ArrayList<>();
        for (AsyncFlowTask asyncFlowTask : taskList) {
            AsyncTaskReturn asyncTaskReturn = new AsyncTaskReturn(
                    asyncFlowTask.getUser_id(),
                    asyncFlowTask.getTask_id(),
                    asyncFlowTask.getTask_type(),
                    asyncFlowTask.getTask_stage(),
                    asyncFlowTask.getStatus(),
                    asyncFlowTask.getCrt_retry_num(),
                    asyncFlowTask.getMax_retry_num(),
                    asyncFlowTask.getMax_retry_interval(),
                    asyncFlowTask.getSchedule_log(),
                    asyncFlowTask.getTask_context(),
                    asyncFlowTask.getCreate_time(),
                    asyncFlowTask.getModify_time());
            tasks.add(asyncTaskReturn);
        }
        return tasks;
    }

    public List<String> conventTaskIdList(List<AsyncFlowTask> list) {
        return list.stream().map(AsyncFlowTask::getId).collect(Collectors.toList());
    }

    public int getTaskCountByStatus(TaskStatus taskStatus) {
        String tableName = getTableName(1, "LarkTask");
        return asyncFlowTaskDao.getTaskCountByStatus(taskStatus.getStatus(), tableName);
    }

    public int getAliveTaskCount() {
        String tableName = getTableName(1, "LarkTask");
        return asyncFlowTaskDao.getTaskCount(this.getAliveStatus(), tableName);
    }

    public int getAllTaskCount() {
        String tableName = getTableName(1, "LarkTask");
        return asyncFlowTaskDao.getTaskCount(this.getAllStatus(), tableName);
    }

    public List<AsyncFlowTask> getAliveTaskList() {
        String tableName = getTableName(1, "LarkTask");
        return asyncFlowTaskDao.getAliveTaskList(this.getAliveStatus(), tableName);
    }

    public List<Integer> getAliveStatus() {
        return new LinkedList<Integer>() {
            {
                add(TaskStatus.PENDING.getStatus());
                add(TaskStatus.EXECUTING.getStatus());
            }
        };
    }

    public List<Integer> getAllStatus() {
        return new LinkedList<Integer>() {
            {
                add(TaskStatus.PENDING.getStatus());
                add(TaskStatus.EXECUTING.getStatus());
                add(TaskStatus.SUCCESS.getStatus());
                add(TaskStatus.FAIL.getStatus());
            }
        };
    }

    public String getTableName(int pos, String taskType) {
        return "t_" + taskType.toLowerCase() + "_" + this.tableName() + "_" + pos;
    }

    public String tableName() {
        return "task";
    }
}
