package ren.util;

import java.util.List;

public interface MessageStroy {

    /**
     * 初始化持久化策略
     */
    public void initPersid();

    public void runPersidOnce();

    /**
     * 消息存储
     */
    public void messageStory(DelayMessage t);

    /**
     * 消息获取
     */
    public List<DelayMessage> queryMessageToRun();

}
