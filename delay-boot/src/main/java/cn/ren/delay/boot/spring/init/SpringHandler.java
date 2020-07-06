package cn.ren.delay.boot.spring.init;

import ren.handler.ExecuteHandler;
import ren.handler.MessageHandler;

import java.util.List;
import java.util.Map;

public class SpringHandler implements MessageHandler {

    public SpringHandler() {
        init();
    }

    @Override
    public void init() {
        Map<Class, List<SpringInitListenable.FieldRelateObj>> objStore =  SpringInitListenable.annotationMethodListMap;

    }

    @Override
    public ExecuteHandler getDelayExecuteHandler() {
        return null;
    }
}
