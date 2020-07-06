package ren.handler;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface DelayExecute {

    /**
     * 业务回调处理节点
     * @return
     */
    String dealKey();
}
