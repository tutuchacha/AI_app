package com.tutuchacha.ai.gateway;

/** 与厂商无关的一条对话消息。role 取 system / user / assistant。 */
public record AiMessage(String role, String content) {

    public static AiMessage user(String content) {
        return new AiMessage("user", content);
    }

    public static AiMessage assistant(String content) {
        return new AiMessage("assistant", content);
    }

    public static AiMessage system(String content) {
        return new AiMessage("system", content);
    }
}
