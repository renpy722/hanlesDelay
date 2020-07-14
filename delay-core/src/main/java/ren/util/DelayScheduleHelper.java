package ren.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ren.handler.ExecuteHandler;
import ren.handler.MessageHandler;
import ren.process.ProcessMessageStory;

import java.io.*;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
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
    private String messageCacheLocal = GlobalConfig.messagePersist;
    /**
     * 执行间隔毫秒值
     */
    private int sleepTime = 1000;

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
    public void Init() throws IOException {


        if (GlobalConfig.runModule.equalsIgnoreCase(CommonState.RunModule.DB.getCode())||GlobalConfig.runModule.equalsIgnoreCase(CommonState.RunModule.PROCESS.getCode())){
            Logger.info("delay run module use Custome Config : "+GlobalConfig.runModule);
            if (GlobalConfig.runModule.equalsIgnoreCase(CommonState.RunModule.DB.getCode())){
                setRunType(CommonState.RunModule.DB);
            }else {
                setRunType(CommonState.RunModule.PROCESS);
            }
        }
        if (runType.equals(CommonState.RunModule.DB.getCode())){
            Logger.info("延迟调度组件初始化，运行模式：DB");
        }else if (runType.equals(CommonState.RunModule.PROCESS.getCode())){
            Logger.info("延迟调度组件初始化，运行模式：PROCESS");
            messageStroy = new ProcessMessageStory();

        }else {
            Logger.error("延迟调度组件初始化失败");
            return;
        }
        //check线程池参数是否正常
        poolExecutor = new ThreadPoolExecutor(Integer.valueOf(GlobalConfig.threadCore), Integer.valueOf(GlobalConfig.threadMax)
                , 60, TimeUnit.SECONDS,new ArrayBlockingQueue<>(Integer.valueOf(GlobalConfig.threadQueueSize)));
        //处理速率配置
        sleepTime = Integer.valueOf(GlobalConfig.messageDealRate);
        //todo 持久化的数据再加载到内存中
        Runtime.getRuntime().addShutdownHook(new Thread(()->{
            Logger.warn("立即执行消息保存操作");
            messageStroy.runPersidOnce(true);
        }));
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
        //判断是否配置开启消息持久化
        if (messageCacheLocal.equalsIgnoreCase(CommonState.messageRedlay)){
            Logger.info("延迟调度组件初始化，PROCESS 模式，本地持久化开启");
            messageStroy.initPersid();
        }
    }

    /**
     * 发送延迟消息
     * @param delayMessage
     */
    public void sendDelayMessage(DelayMessage delayMessage){
        messageStroy.messageStory(delayMessage,true);
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
                                            Object param = item.getData();
                                            Class paramType = delayExecuteHandler.getMethod().getParameterTypes()[0];
                                            if (item.getData() instanceof JSONObject){
                                                //读取的文件中消息的类型可能是JSONObject，需要进行处理转化
                                                param = JSON.parseObject(JSON.toJSONString(param),paramType);
                                            }
                                            delayExecuteHandler.getMethod().invoke(delayExecuteHandler.getTarget(),param);
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
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
