package ren.process;

import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ren.local.FilePersistImpl;
import ren.local.Persist;
import ren.util.*;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static ren.util.CommonState.lockNumbs;


public class ProcessMessageStory implements MessageStroy {

    private static Logger Logger = LoggerFactory.getLogger(ProcessMessageStory.class);
    /**
     *  按照时间排序存储的集合
     */
    private TreeMap<Long, List<DelayMessage>> sortMessagIdList = new TreeMap<>();

    private static List<Lock> lockArry = new ArrayList<>();

    private Persist persist;



    /**
     * 每次截取时，多截取下前100毫秒秒的数据，防止有执行压力过大问题，丢失部分数据未存储
     */
    private Integer repeatConvertTime = 100;

    /**
     * 上次的执行时间，用于检测是否压力过大
     */
    private long lastRunTime = 0;

    private double failRate = 0.3;

    static {
        for (int i=0;i<lockNumbs;i++){
            lockArry.add(new ReentrantLock());
        }
    }

    public void initPersid(){
        persist = FilePersistImpl.getInstance();
        //读取持久化数据，取出尚未处理的数据，放入Map中，因为此时持久化服务尚未初始化完成，所以需要等启动成功后再执行
        List<DelayMessage> delayMessages = persist.loadFromPersist();
        GlobalConfig.GlobalThreadPool.submit(()->{
            int runFlag = 0;
           while (CommonState.nowState!=CommonState.RunStatus.RUNING.getCode()&&runFlag<60){
               try {
                   Thread.sleep(1000);
                   runFlag+=1;
               } catch (InterruptedException e) {
                   e.printStackTrace();
               }
           }
           if (CommonState.nowState!=CommonState.RunStatus.RUNING.getCode()){
               Logger.error("超过60秒延迟服务尚未准备完成，取消加载持久化数据");
           }else {
               delayMessages.forEach(item -> messageStory(item));
               CommonState.presistState = CommonState.presistSuccessFlag;
           }
        });

        Thread persidThread = new Thread(()->{

            while (CommonState.nowState!=CommonState.RunStatus.RUNING.getCode()&&
                    (GlobalConfig.messagePersist.equalsIgnoreCase(CommonState.messageRedlay)&&CommonState.presistState != CommonState.presistSuccessFlag)){
                try {
                    Thread.sleep(1000);
                    Logger.info("等待延迟服务启动成功，等待持久化消息加载完成。。。。。");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            Logger.info("消息持久化线程启动");
            while (true){
                runPersidOnce();
                //todo 删除操作留在持久化组件里面自己去维护 oldMessageDel();
                try {
                    Thread.sleep(GlobalConfig.persistRate);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        persidThread.start();
    }

    @Override
    public void runPersidOnce() {
        try{
            Logger.debug("once deal messag local store");
            checkServerPress();
            long nowTime = System.currentTimeMillis();
            long needDealTime = nowTime + GlobalConfig.persistAfterSecond * 1000;
            NavigableMap<Long, List<DelayMessage>> longListNavigableMap = sortMessagIdList.subMap(needDealTime - repeatConvertTime, true, needDealTime + GlobalConfig.persistRate, true);
            for (Long itemKey : longListNavigableMap.keySet()){
                persist.runPersist(itemKey,longListNavigableMap.get(itemKey));
            }
            lastRunTime = System.currentTimeMillis();
        }catch (Exception e){
            Logger.error("延迟消息本地持久化失败");
        }
    }

    /**
     * 运行时间检测，防止出现消息持久化丢失问题
     */
    private void checkServerPress() {
        if (lastRunTime==0){
            return;
        }
        long nowTime = System.currentTimeMillis();
        if ((nowTime-lastRunTime)>GlobalConfig.persistRate*(1+failRate)){
            Logger.warn("消息持久化线程检测：服务压力过大可能会出现消息持久化丢失");
        }
    }

    @Override
    public void messageStory(DelayMessage t) {
        if (CommonState.nowState!= CommonState.RunStatus.RUNING.getCode()){
            throw new SendFailException("延迟组件不可用，请稍后重试");
        }
        if (Objects.isNull(t)){
            throw new SendFailException("延迟消息不能为空");
        }
        ValidationUtil.validateBean(t);
        if (System.currentTimeMillis()>t.getExecuteTime()){
            throw new SendFailException("延迟执行时间必须大于当前时间");
        }
        Logger.info("send delay message :{}",t);
        Long executeTime = t.getExecuteTime();
        Lock currentLock = getLockObj(executeTime);
        try {
            boolean lockSuccess = currentLock.tryLock(1, TimeUnit.SECONDS);
            if (!lockSuccess){
                throw new SendFailException("延迟消息发送失败，请尽量错峰延迟的执行时间");
            }
            try{
                List<DelayMessage> delayMessages = sortMessagIdList.get(executeTime);
                if (Objects.isNull(delayMessages)){
                    delayMessages = new ArrayList<>();
                    sortMessagIdList.put(executeTime,delayMessages);
                }
                delayMessages.add(t);
                Logger.info("发送延迟消息成功，执行时间：{},消息内容：{}",executeTime,t);
            }catch (Exception e){

            }finally {
                currentLock.unlock();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            throw new SendFailException("延迟组件不可用，请稍后重试");
        }
    }

    @Override
    public List<DelayMessage> queryMessageToRun() {
        if (sortMessagIdList.keySet().size()==0){
            return new ArrayList<>();
        }
        Logger.debug("now time Message Size :"+sortMessagIdList.keySet().size());
        Long firstKey = sortMessagIdList.firstKey();
        Long nowTime = System.currentTimeMillis();
        if (firstKey.longValue()>=nowTime.longValue()){
            return new ArrayList<>();
        }
        Iterator<Long> keyIterator = sortMessagIdList.keySet().iterator();
        LinkedList<Long> needDealKeys = new LinkedList<>();
        while (keyIterator.hasNext()){
            Long nextKey = keyIterator.next();
            if (nextKey.longValue()>nowTime.longValue()){
                break;
            }
            needDealKeys.add(nextKey);
        }
        List<DelayMessage> allDelayMessage = new ArrayList<>();
        while (true){
            Long itemKey = needDealKeys.pop();
            Lock lock = getLockObj(itemKey);
            boolean itemLockRs = false;
            try {
                itemLockRs = lock.tryLock(50, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (!itemLockRs){
                //获取锁失败，重新入队
                needDealKeys.add(itemKey);
            }
            try{
                //获取锁成功，取出数据
                List<DelayMessage> delayMessages = sortMessagIdList.get(itemKey);
                allDelayMessage.addAll(delayMessages);
                sortMessagIdList.remove(itemKey);
            }finally {
                lock.unlock();
            }
            if (System.currentTimeMillis()>(nowTime+CommonState.maxTimeOnceDeal) || needDealKeys.size()==0){
                //超过最大取数时长，或者数据取完跳出循环
                break;
            }
        }
        return allDelayMessage;
    }

    private Lock getLockObj(Long executeTime){
        int lockIndex = (int) (executeTime%lockNumbs);
        Lock currentLock = lockArry.get(lockIndex);
        return currentLock;
    }
}
