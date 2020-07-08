package ren.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ren.handler.ExecuteHandler;
import ren.handler.MessageHandler;
import ren.process.ProcessMessageStory;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DelayScheduleHelper {

    private static Logger Logger = LoggerFactory.getLogger(DelayScheduleHelper.class);
    private static volatile DelayScheduleHelper instance = null;

    private MessageStroy messageStroy;
    private MessageHandler messageHandler;
    private String runType = CommonState.RunModule.PROCESS.getCode();
    private ThreadPoolExecutor poolExecutor;
    private Lock queryMsgLock = new ReentrantLock();

    public static DelayScheduleHelper getInstance(){
        if (instance==null){
            synchronized (DelayScheduleHelper.class){
                if (instance==null){
                    instance = new DelayScheduleHelper();
                }
            }
        }
        return instance;
    }

    /**
     * 设置运行模式
     * @param module
     */
    public void setRunType(CommonState.RunModule module) {
        this.runType = module.getCode();
    }

    public void setMessageHandler(MessageHandler handler){
        this.messageHandler = handler;
    }
    /**
     * 组件初始化
     */
    public void Init(){

        if (runType.equals(CommonState.RunModule.DB.getCode())){
            Logger.info("延迟调度组件初始化，运行模式：DB");
        }else if (runType.equals(CommonState.RunModule.PROCESS.getCode())){
            Logger.info("延迟调度组件初始化，运行模式：PROCESS");
            messageStroy = new ProcessMessageStory();
        }else {
            Logger.error("延迟调度组件初始化失败");
            return;
        }

        poolExecutor = new ThreadPoolExecutor(2, 4, 60, TimeUnit.SECONDS,new ArrayBlockingQueue<>(300));        //todo 后续可做优化，将配置信息抽离出来，由用户自己控制

        //todo 持久化的数据再加载到内存中

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                Logger.warn("run shoutDown。。。。");
                CommonState.nowState = CommonState.RunStatus.STOP.getCode();
                while (true){
                    if (queryMsgLock.tryLock()){
                        break;
                    }
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                poolExecutor.shutdown();
            }
        }));
        TimeWheel timeWheel = new TimeWheel();
        Thread dealThread = new Thread(timeWheel);
        CommonState.nowState = CommonState.RunStatus.RUNING.getCode();
        dealThread.start();
    }

    /**
     * 发送延迟消息
     * @param delayMessage
     */
    public void sendDelayMessage(DelayMessage delayMessage){
        messageStroy.messageStory(delayMessage);
    }

    class TimeWheel implements Runnable{

        @Override
        public void run() {
            Logger.debug("init Thread try to get Delay Message ");
            while (true){
                if (CommonState.nowState==CommonState.RunStatus.STOP.getCode()){
                    break;
                }
                boolean lockRs = queryMsgLock.tryLock();
                if (lockRs){
                    try{
                        List<DelayMessage> delayMessages = messageStroy.queryMessageToRun();
                        if (delayMessages!=null&& delayMessages.size()>0){
                            Logger.debug("once get DelayMessage size ：{}",delayMessages.size());
                            try{
                                delayMessages.forEach(item -> {
                                    poolExecutor.submit(()->{
                                        String dealKey = item.getDealKey();
                                        ExecuteHandler delayExecuteHandler = messageHandler.getDelayExecuteHandler(dealKey);
                                        try {
                                            delayExecuteHandler.getMethod().invoke(delayExecuteHandler.getTarget(),item.getData());
                                        }catch (Exception e){
                                            e.printStackTrace();
                                            Logger.error("run delay message execute fail ：{},fail key :{}",e,dealKey);
                                        }
                                    });
                                });
                            }catch (Exception e){
                                Logger.error("delay Thread run error ：{}",e);
                                e.printStackTrace();
                            }
                        }
                    }finally {
                        queryMsgLock.unlock();
                    }
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
