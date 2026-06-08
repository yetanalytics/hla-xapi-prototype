package com.yetanalytics.hlaxapi;

public interface InteractionListener {

    void receivedStart(float timeScaleFactor);

    void receivedStop();
}
