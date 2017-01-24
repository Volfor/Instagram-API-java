package com.github.volfor;

public interface Callback<T> {

    void onSuccess(T response);

    void onFailure(Throwable t);

}
