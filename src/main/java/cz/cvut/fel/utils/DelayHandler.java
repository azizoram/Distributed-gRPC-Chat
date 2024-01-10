package cz.cvut.fel.utils;

public interface DelayHandler {
    void set(String param, String value);
    void handleRequestDelay(String method);
    void handleResponseDelay(String method);
}
