package ren.util;


import ren.handler.DelayExecute;
import ren.handler.ExecuteHandler;

import java.lang.annotation.Annotation;

public class CommonState {

    public static int nowState = -1;

    /**
     * 并发限制，最大有20个散列时间能够进行同时操作
     */
    public static int lockNumbs = 20;

    /**
     * 一次轮询处理的取数据最大时长（毫秒）
     */
    public static long maxTimeOnceDeal = 800;

    /**
     * 运行状态
     */
    public enum RunStatus{
        RUNING("运行中",1),
        STOP("不可用",-1);
        private String desc;
        private int code;

        RunStatus(String desc, int code) {
            this.desc = desc;
            this.code = code;
        }

        public String getDesc() {
            return desc;
        }

        public void setDesc(String desc) {
            this.desc = desc;
        }

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }
    }

    /**
     * 运行模式
     */
    public enum RunModule{
        PROCESS("进程模式","process"),
        DB("DB模式","db")
        ;
        private String desc;
        private String code;

        RunModule(String desc, String code) {
            this.desc = desc;
            this.code = code;
        }

        public String getDesc() {
            return desc;
        }

        public void setDesc(String desc) {
            this.desc = desc;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }
    }

    public static Class[] needScanAnnos = new Class[]{DelayExecute.class};
}
