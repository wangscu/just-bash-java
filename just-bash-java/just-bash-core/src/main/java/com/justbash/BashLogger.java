package com.justbash;

public interface BashLogger {
    void debug(String message);
    void info(String message);
    void warn(String message);
    void error(String message, Throwable cause);
}
