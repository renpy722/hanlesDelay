package cn.ren.delay.boot.spring.init;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ren.handler.DelayExecute;
import ren.handler.ExecuteHandler;
import ren.handler.MessageHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpringHandler implements MessageHandler {

    private static Logger LOGGER = LoggerFactory.getLogger(SpringHandler.class);

    private Map<String,ExecuteHandler> handlerMap = new HashMap<>();
    public SpringHandler() {
        init();
    }

    @Override
    public void init() {
        Map<Class, List<SpringInitListenable.FieldRelateObj>> objStore =  SpringInitListenable.annotationMethodListMap;
        List<SpringInitListenable.FieldRelateObj> fieldRelateObjs = objStore.get(DelayExecute.class);
        if (fieldRelateObjs==null||fieldRelateObjs.size()==0){
            return;
        }
        fieldRelateObjs.forEach( item ->{
            DelayExecute delayExecute = (DelayExecute) item.getAnnotation();
            if (handlerMap.containsKey(delayExecute.dealKey())){
                LOGGER.warn("repeat dealKey with DelayExecute：{}",delayExecute.dealKey());
            }
            handlerMap.put(delayExecute.dealKey(),new ExecuteHandler(item.getMethod(),item.getTarget()));
        });
    }

    @Override
    public ExecuteHandler getDelayExecuteHandler(String key) {
        return handlerMap.get(key);
    }
}
