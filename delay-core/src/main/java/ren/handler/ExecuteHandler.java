package ren.handler;

import java.lang.reflect.Method;

public class ExecuteHandler {

    private Method method;

    private Object target;

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

    public ExecuteHandler(Method method, Object target) {
        this.method = method;
        this.target = target;
    }
}
