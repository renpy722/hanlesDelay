package ren.local;

import ren.util.DelayMessage;

import java.util.List;

/**
 * 持久化策略：
 * 每隔X 秒 执行一次持久化 操作 持久化数据：执行时间在 Y秒之后的消息
 * 执行持久化的时候，需要处理一下当前队列中的消息的最小时间，将小于最小时间的del
 */
public interface Persist {
    void runPersist(long minTime);

    List<DelayMessage> loadFromPersist();
}
