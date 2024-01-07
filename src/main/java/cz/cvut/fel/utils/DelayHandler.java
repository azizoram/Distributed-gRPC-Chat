package cz.cvut.fel.utils;

public interface DelayHandler {
    void handleRequestDelay(String method);
    void handleResponseDelay(String method);
}
