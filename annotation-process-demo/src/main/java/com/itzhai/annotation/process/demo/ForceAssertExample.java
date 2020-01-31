package com.itzhai.annotation.process.demo;

/**
 * Created by arthinking on 30/1/2020.
 */
@ForceAssertions
public class ForceAssertExample {

    /**
     * java -ea com.itzhai.annotation.process.demo.ForceAssertExample
     * @param args
     */
    public static void main(String[] args) {
        String str = null;
        assert str != null : "Must not be null";
    }

}
