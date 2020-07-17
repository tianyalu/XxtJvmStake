package com.sty.xxt.jvmstake;

public class Person {

    public static void main(String[] args) {
        Person person = new Person();
        person.work();
        person.hashCode();
    }

    public int work() {
        int x = 3;
        int y = 5;
        int z = (x + y) * 10;
        return z;
    }
}
