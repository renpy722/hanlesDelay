package ren.handler;

public interface MessageHandler {

    void init();

    ExecuteHandler getDelayExecuteHandler(String key);
}
