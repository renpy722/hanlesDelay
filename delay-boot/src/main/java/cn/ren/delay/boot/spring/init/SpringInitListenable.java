package cn.ren.delay.boot.spring.init;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import cn.ren.delay.boot.spring.util.AopTargetUtils;
import ren.handler.MessageHandler;
import ren.util.CommonState;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

public class SpringInitListenable implements ApplicationListener , ApplicationContextAware {

    private static Logger LOGGER = LoggerFactory.getLogger(SpringInitListenable.class);

    private ApplicationContext applicationContext;
    private MessageHandler messageHandler;
    public static Map<Class, List<FieldRelateObj>> annotationListMap = new HashMap<Class, List<FieldRelateObj>>();
    public static Map<Class, List<FieldRelateObj>> annotationMethodListMap = new HashMap<Class, List<FieldRelateObj>>();

    @Override
    public void onApplicationEvent(ApplicationEvent applicationEvent) {
        //项目的初始化改到这个地方，在这个地方进行容器对象的获取分析，然后进行组件的DelayScheduleHelper的
        // init，还有就是MessageHandler的构建
        annotionScan();
        messageHandler = new SpringHandler();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {

        this.applicationContext = applicationContext;
    }

    private void annotionScan(){
        String[] beanDefinitionNames = applicationContext.getBeanDefinitionNames();

        for (String itemBean : beanDefinitionNames){
            Object bean = applicationContext.getBean(itemBean);
            try{
                Object target = AopTargetUtils.getTarget(bean);
                scanFieldAnnotion(target.getClass(),target);
                scanMethodAnnotion(target.getClass(),bean);
            }catch (Exception e){
                LOGGER.error("获取Bean的真实对象操作异常");
            }
        }
    }

    /**
     * 这个地方传入的应该是真正的对象
     * @param itemClass
     * @param target
     */
    private void scanFieldAnnotion(Class itemClass,Object target){
        Field[] fields = itemClass.getDeclaredFields();
        for (Field field :fields){
            field.setAccessible(true);
            for (Class itemAnno : CommonState.needScanAnnos){
                Annotation annotation = field.getAnnotation(itemAnno);
                if (Objects.nonNull(annotation)){
                    //cache annotion
                    FieldRelateObj itemRelateObje = new FieldRelateObj();
                    itemRelateObje.setAnnotation(annotation);
                    itemRelateObje.setField(field);
                    itemRelateObje.setTarget(target);
                    List<FieldRelateObj> fieldRelateObjs = annotationListMap.get(annotation.getClass());
                    if (fieldRelateObjs == null){
                        fieldRelateObjs = new ArrayList<>();
                        annotationListMap.put(annotation.getClass(),fieldRelateObjs);
                    }
                    fieldRelateObjs.add(itemRelateObje);
                }
            }
        }
    }

    /**
     * 这个地方传入的对象应该是代理对象，否则会由aop的失效
     * @param itemClass
     * @param target
     */
    private void scanMethodAnnotion(Class itemClass,Object target){
        Method[] methods = itemClass.getDeclaredMethods();
        for (Method method :methods){
            method.setAccessible(true);
            for (Class itemAnno : CommonState.needScanAnnos){
                Annotation annotation = method.getAnnotation(itemAnno);
                if (Objects.nonNull(annotation)){
                    //cache annotion
                    FieldRelateObj itemRelateObje = new FieldRelateObj();
                    itemRelateObje.setAnnotation(annotation);
                    itemRelateObje.setMethod(method);
                    itemRelateObje.setTarget(target);
                    List<FieldRelateObj> fieldRelateObjs = annotationMethodListMap.get(annotation.getClass());
                    if (fieldRelateObjs == null){
                        fieldRelateObjs = new ArrayList<>();
                        annotationListMap.put(annotation.getClass(),fieldRelateObjs);
                    }
                    fieldRelateObjs.add(itemRelateObje);
                }
            }
        }
    }

    class FieldRelateObj{

        private Annotation annotation;
        private Field field;
        private Method method;
        private Object target;

        public Annotation getAnnotation() {
            return annotation;
        }

        public void setAnnotation(Annotation annotation) {
            this.annotation = annotation;
        }

        public Field getField() {
            return field;
        }

        public void setField(Field field) {
            this.field = field;
        }

        public Method getMethod() {
            return method;
        }

        public void setMethod(Method method) {
            this.method = method;
        }

        public Object getTarget() {
            return target;
        }

        public void setTarget(Object target) {
            this.target = target;
        }
    }
}
