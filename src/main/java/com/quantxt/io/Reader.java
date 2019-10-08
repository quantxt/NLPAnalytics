package com.quantxt.io;

public interface Reader<T, R> {

    R read(T source);


}
