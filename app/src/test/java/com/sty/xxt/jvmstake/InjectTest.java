package com.sty.xxt.jvmstake;

public class InjectTest {

    @ASMTest
    public static void main(String[] args) throws InterruptedException {
        long startTime = System.currentTimeMillis();

        Thread.sleep(3000);

        long endTime = System.currentTimeMillis();
        System.out.println("execute time = " + (endTime - startTime) + "ms");
    }

    void method() {

    }
}
