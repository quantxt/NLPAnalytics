package com.quantxt.io;

public interface ResourceIO<E, T, R> {

    void write(E object);

    R read(T source);


}
