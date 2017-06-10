package com.alayouni.ansihighlight;

/**
 * Created by alayouni on 6/10/17.
 */
public class ItemHolder<T> {
    private T item = null;

    public T get() {
        return item;
    }

    public void set(T item) {
        this.item = item;
    }
}
