package ren.util;

import javax.validation.constraints.NotNull;

public class DelayMessage<T> {

    @NotNull(message = "延迟消息不能为空")
    private T data;
    @NotNull(message = "延迟执行时间不能为空")
    private Long executeTime;
    private int retryTimes = 0;
    @NotNull(message = "延迟调度Key不能为空")
    private String dealKey;

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public Long getExecuteTime() {
        return executeTime;
    }

    public void setExecuteTime(Long executeTime) {
        this.executeTime = executeTime;
    }

    public int getRetryTimes() {
        return retryTimes;
    }

    public void setRetryTimes(int retryTimes) {
        this.retryTimes = retryTimes;
    }

    public String getDealKey() {
        return dealKey;
    }

    public void setDealKey(String dealKey) {
        this.dealKey = dealKey;
    }
}
