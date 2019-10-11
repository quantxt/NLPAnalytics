package com.quantxt.io;

public interface Converter<T, R> {

    R convert(T input);

}
