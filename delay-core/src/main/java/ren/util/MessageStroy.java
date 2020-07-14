package ren.util;

import java.util.List;

public interface MessageStroy {

    /**
     * 初始化持久化策略
     */
    public void initPersid();

    public void runPersidOnce(boolean forceAll);

    /**
     * 消息存储
     */
    public void messageStory(DelayMessage t,boolean checkTimeVaild);

    /**
     * 消息获取
     */
    public List<DelayMessage> queryMessageToRun();

}
