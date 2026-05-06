package com.qianxun.constant;

public final class CacheConstant {

    /**
     * 用户名密码统一存放文件路径
     */
    private static String filePath = "/work/conf/conf.cfg";
    private static String fileTempDir = "/data/docs/download";

    private CacheConstant() {
        //nothing to do
    }

    public static synchronized String getFilePath(){
        return CacheConstant.filePath;
    }

    public static void setFileTempDir(String fileTempDir) {
        CacheConstant.fileTempDir = fileTempDir;
    }

    public static String getFileTempDir() {
        return fileTempDir;
    }
}
