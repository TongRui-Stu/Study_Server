package com.tongrui.study.client;

public interface AiStreamListener {

    void onSource(String provider, String model);

    void onDelta(String content);
}
