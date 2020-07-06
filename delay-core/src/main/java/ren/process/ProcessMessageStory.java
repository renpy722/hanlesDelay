package ren.process;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    static {
        for (int i=0;i<lockNumbs;i++){
            lockArry.add(new ReentrantLock());
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
        ValidationUtil.validateBean(t,DelayMessage.class);
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
