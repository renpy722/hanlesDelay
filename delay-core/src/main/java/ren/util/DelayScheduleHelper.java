package ren.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ren.handler.ExecuteHandler;
import ren.handler.MessageHandler;
import ren.process.ProcessMessageStory;

import java.io.*;
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
    private String messageCacheLocal = "off";
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

        //读取classpath目录下的配置文件
        URL classPath = Thread.currentThread().getContextClassLoader().getResource("");
        String classPathUrl = classPath.getPath();
        InputStream inputStream = new FileInputStream(classPathUrl+ File.separator+"delay.properties");
        Properties properties = new Properties();
        properties.load(inputStream);
        String runModule = properties.getProperty("delay.module").trim();
        String threadCore = properties.getProperty("delay.thread.core").trim();
        String threadMax = properties.getProperty("delay.thread.max").trim();
        String threadQueueSize = properties.getProperty("delay.thread.queueSize").trim();
        String messageDealRate = properties.getProperty("delay.deal.rate").trim();
        if (runModule.equalsIgnoreCase(CommonState.RunModule.DB.getCode())||runModule.equalsIgnoreCase(CommonState.RunModule.PROCESS.getCode())){
            Logger.info("delay run module use Custome Config : "+runModule);
            if (runModule.equalsIgnoreCase(CommonState.RunModule.DB.getCode())){
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
            //判断是否配置开启消息持久化
            if (messageCacheLocal.equalsIgnoreCase(CommonState.messageRedlay)){
                Logger.info("延迟调度组件初始化，PROCESS 模式，本地持久化开启");

            }
        }else {
            Logger.error("延迟调度组件初始化失败");
            return;
        }
        //check线程池参数是否正常
        if (threadCore!=null&&threadMax!=null&&threadQueueSize!=null){
            Logger.info("delay run ThreadConfig use Custome Config ");
            poolExecutor = new ThreadPoolExecutor(Integer.valueOf(threadCore), Integer.valueOf(threadMax), 60, TimeUnit.SECONDS,new ArrayBlockingQueue<>(Integer.valueOf(threadQueueSize)));
        }else {
            poolExecutor = new ThreadPoolExecutor(2, 4, 60, TimeUnit.SECONDS,new ArrayBlockingQueue<>(300));
        }

        //处理速率配置
        if (messageDealRate!=null){
            sleepTime = Integer.valueOf(messageDealRate);
        }
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
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
