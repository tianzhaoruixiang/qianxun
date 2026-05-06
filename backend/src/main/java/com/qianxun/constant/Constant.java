package com.qianxun.constant;

import java.util.Map;

public final class Constant {
    /**
     * 前端自定义参数的KEY
     */
    public static final String JSON_ARG = "jsonArg";
    /**
     * 前端框架自动参数的KEY
     */
    public static final String GENERAL_ARGUMENT = "generalArgument";

    public static final String SERVICE_NAME = "NNS";

    public static final String TASK_ID = "task-id";


    public static final Map<String, String> MODE_FILE_TYPE_MAP = Map.of(
            "email", "email",
            "voice", "voice",
            "doc", "doc"
    );

    private Constant(){
    }
}
