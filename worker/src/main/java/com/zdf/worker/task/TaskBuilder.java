package com.zdf.worker.task;

import com.alibaba.fastjson.JSON;
import com.zdf.worker.constant.UserConfig;
import com.zdf.worker.data.AsyncFlowClientData;
import com.zdf.worker.data.NftTaskContext;
import com.zdf.worker.data.ScheduleLog;

import java.lang.reflect.Method;

/**
 * @author zhangdafeng
 */
public class TaskBuilder {

    public static AsyncFlowClientData build(AsyncExecutable executable) throws NoSuchMethodException {
        Class<? extends AsyncExecutable> aClass = executable.getClass();
        Method handProcess = aClass.getMethod("handleProcess");
        return TaskBuilder.build(aClass, handProcess.getName(), new Object[0], new Class[0]);
    }

    // 利用类信息创建任务
    public static AsyncFlowClientData build(Class<?> clazz, String methodName, Object[] params,
            Class<?>[] parameterTypes, Object... envs) {
        if (!AsyncExecutable.class.isAssignableFrom(clazz)) {
            throw new RuntimeException("The task must be implemented TaskDefinition!");
        }
        checkParamsNum(params, parameterTypes);
        Method method = getMethod(clazz, methodName, params, parameterTypes);

        // 获取类名
        String taskType = method.getDeclaringClass().getSimpleName();
        // get 方法名
        String taskStage = method.getName();
        // 调度日志
        ScheduleLog sl = new ScheduleLog();
        String scheduleLog = JSON.toJSONString(sl);

        // 上下文信息
        NftTaskContext nftTaskContext = new NftTaskContext(params, envs, parameterTypes);
        String taskContext = JSON.toJSONString(nftTaskContext);
        return new AsyncFlowClientData(
                UserConfig.USERID,
                taskType,
                taskStage,
                scheduleLog,
                taskContext);
    }

    /**
     * 检查 params 和 parameterTypes 是否为 null，或者它们的长度是否不匹配
     * 
     * @param params         参数列表: void myMethod() params->new Object[]{} void
     *                       myMethod(String name)->new Object[]{"zhangsan"}
     * @param parameterTypes 参数类型列表: void myMethod() parameterTypes->new Class[]{}
     *                       void myMethod(String name)->new Class[]{String.class}
     */
    // public static void checkParamsNum(Object[] params, Class<?>[] parameterTypes)
    // {
    // // 检查 params 和 parameterTypes 是否为 null，或者它们的长度是否不匹配
    // if (params == null || parameterTypes == null) {
    // // 在这里可以进一步细化错误信息，比如区分是 null 还是长度不匹配
    // // 但为了简单修复 NullPointerException，可以直接抛出异常或进行相应处理
    // throw new RuntimeException("Parameters (params or parameterTypes) are null");
    // }
    // // if (params.length != parameterTypes.length) {
    // // throw new RuntimeException("lengths do not match");
    // // }
    // }
    public static void checkParamsNum(Object[] params, Class<?>[] parameterTypes) {
        // 参数个数检验
        if (params.length != parameterTypes.length) {
            throw new RuntimeException("Parameters are invalid!");
        }
    }

    // public static Method getMethod(Class<?> clazz, String methodName, Object[]
    // params, Class<?>[] parameterTypes) {
    // // 校验参数数量是否一致
    // checkParamsNum(params, parameterTypes);
    // try {
    // Method method = clazz.getMethod(methodName, parameterTypes);
    // return method;
    // } catch (NoSuchMethodException e) {
    // e.printStackTrace();
    // }
    // return null;
    // }
    public static Method getMethod(Class<?> clazz, String methodName, Object[] params, Class<?>[] parameterTypes) {
        Method method = null;
        for (Method clazzMethod : clazz.getMethods()) {
            // 获取对应要执行的方法
            // 1. 通过方法名匹配: clazzMethod.getName().equals(methodName)
            // 2. 通过参数个数匹配: clazzMethod.getParameterCount() == params.length
            // 3. 通过参数类型匹配: judgeParamsTypes(clazzMethod, parameterTypes)
            // 如果以上三个条件都满足,说明找到了要执行的目标方法,将其赋值给method变量
            if (clazzMethod.getName().equals(methodName) && clazzMethod.getParameterCount() == params.length
                    && judgeParamsTypes(clazzMethod, parameterTypes)) {
                method = clazzMethod;
            }
        }

        return method;
    }

    private static boolean judgeParamsTypes(Method clazzMethod, Class<?>[] parameterTypes) {
        Class<?>[] types = clazzMethod.getParameterTypes();
        for (int i = 0; i < types.length; i++) {
            if (types[i] != parameterTypes[i]) {
                return false;
            }
        }
        return true;
    }
}
