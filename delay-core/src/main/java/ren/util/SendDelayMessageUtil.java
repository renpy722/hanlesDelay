package ren.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendDelayMessageUtil {

    private static Logger LOGGER = LoggerFactory.getLogger(SendDelayMessageUtil.class);
    private static DelayScheduleHelper delayScheduleHelper = DelayScheduleHelper.getInstance();

    public static boolean sendDelayMessage(DelayMessage delayMessage){
        try{
            if (CommonState.nowState!= CommonState.RunStatus.RUNING.getCode()){
                throw new SendFailException("延迟组件尚未初始化完成，请稍后重试");
            }
            delayScheduleHelper.sendDelayMessage(delayMessage);
            return true;
        }catch (Exception e){
            LOGGER.error("发送延迟消息失败：{}",e);
            return false;
        }
    }
}
