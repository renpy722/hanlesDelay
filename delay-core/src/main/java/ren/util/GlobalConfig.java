package ren.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class GlobalConfig {

    private static Logger LOGGER = LoggerFactory.getLogger(GlobalConfig.class);
    /**
     * 运行模式
     */
    public static String runModule = CommonState.RunModule.PROCESS.getCode();

    /**
     * 延迟任务处理线程池的核心线程数
     */
    public static int threadCore = Runtime.getRuntime().availableProcessors();

    /**
     * 延迟任务处理线程池的最大线程数
     */
    public static int threadMax = threadCore;

    /**
     * 延迟任务处理线程池的队列Size
     */
    public static int threadQueueSize = 200;

    /**
     * 延迟消息的处理速率，越小越快，建议范围[50-1000]，如果配置小于10，则采用默认值
     */
    public static int messageDealRate = 100;

    /**
     * 消息持久化
     */
    public static String messagePersist = "on";

    /**
     * 消息持久化，持久化的是什么时间之后的消息，单位是秒
     */
    public static int persistAfterSecond = 60;

    /**
     * 消息持久化执行的速率，越小越快，建议范围大于[10000-60000]，如果小于10000，则采用默认值
     */
    public static int persistRate = 10000;

    public static ThreadPoolExecutor GlobalThreadPool = new ThreadPoolExecutor(threadCore/2,(threadCore/2)+1,
            70, TimeUnit.SECONDS,new ArrayBlockingQueue<>(50));

    static {
        //读取classpath目录下的配置文件
        URL classPath = Thread.currentThread().getContextClassLoader().getResource("");
        String classPathUrl = classPath.getPath();
        InputStream inputStream = null;
        Properties properties = new Properties();
        boolean configIsOk = true;
        File configFile = new File(classPathUrl+ File.separator+"delay.properties");
        if (configFile.exists()){
            try {
                inputStream = new FileInputStream(classPathUrl+ File.separator+"delay.properties");
                properties.load(inputStream);
            } catch (IOException e) {
                e.printStackTrace();
                configIsOk = false;
            }
        }

        if (configIsOk){

            try {
                String runModule = properties.getProperty("delay.module").trim();
                String threadCore = properties.getProperty("delay.thread.core").trim();
                String threadMax = properties.getProperty("delay.thread.max").trim();
                String threadQueueSize = properties.getProperty("delay.thread.queueSize").trim();
                String messageDealRate = properties.getProperty("delay.deal.rate").trim();
                String persistSwitch = properties.getProperty("delay.persist.switch").trim();
                String persistTime = properties.getProperty("delay.persist.time").trim();
                String persistRate = properties.getProperty("delay.persist.rate").trim();

                GlobalConfig.runModule = runModule;
                GlobalConfig.threadCore = Integer.valueOf(threadCore);
                GlobalConfig.threadMax = Integer.valueOf(threadMax);
                GlobalConfig.threadQueueSize = Integer.valueOf(threadQueueSize);
                if (Integer.valueOf(messageDealRate)>=10&&Integer.valueOf(messageDealRate)<=1000){
                    GlobalConfig.messageDealRate = Integer.valueOf(messageDealRate);
                }
                if ("on".equalsIgnoreCase(persistSwitch)||"off".equalsIgnoreCase(persistSwitch)){
                    GlobalConfig.messagePersist = persistSwitch;
                }
                GlobalConfig.persistAfterSecond = Integer.valueOf(persistTime);
                //执行速率需要满足条件范围在[10000-60000]毫秒之间，且执行速率要小于持久化的时间差
                if (Integer.valueOf(persistRate)>=10000&&Integer.valueOf(persistRate)<=60000&&Integer.valueOf(persistRate)
                    < GlobalConfig.persistAfterSecond*1000 ){
                    GlobalConfig.persistRate = Integer.valueOf(persistRate);
                }

            }catch (Exception e){
                e.printStackTrace();
                LOGGER.error("配置文件配置错误，部分配置将采取默认值运行");
            }
        }
    }

}
