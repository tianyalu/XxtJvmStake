package com.sty.xxt.jvmstake;

import org.junit.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.Method;

import java.io.FileInputStream;
import java.io.FileOutputStream;

public class ASMUnitTest {

    @Test
    public void test() {
        try {
            FileInputStream fis = new FileInputStream("/Users/tian/NeCloud/xxt/workspace/XxtJvmStake/app/src/test/java/com/sty/xxt/jvmstake/InjectTest.class");

            //获取一个分析器
            ClassReader classReader = new ClassReader(fis);
            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);

            //开始插桩
            classReader.accept(new MyClassVisitor(Opcodes.ASM7, classWriter), ClassReader.EXPAND_FRAMES);

            byte[] bytes = classWriter.toByteArray();
            FileOutputStream fos = new FileOutputStream("/Users/tian/NeCloud/xxt/workspace/XxtJvmStake/app/src/test/java/com/sty/xxt/jvmstake/InjectTest2.class");

            fos.write(bytes);
            fos.close();
            fis.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 用来访问类信息
     */
    static class MyClassVisitor extends ClassVisitor {
        public MyClassVisitor(int api) {
            super(api);
        }

        public MyClassVisitor(int api, ClassVisitor classVisitor) {
            super(api, classVisitor);
        }

        /**
         * 每读取到一个方法的时候，都会执行下面的方法
         * @param access
         * @param name
         * @param descriptor
         * @param signature
         * @param exceptions
         * @return
         */
        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);

            return new MyMethodVisitor(api, methodVisitor, access, name, descriptor);
        }
    }

    static class MyMethodVisitor extends AdviceAdapter {

        protected MyMethodVisitor(int api, MethodVisitor methodVisitor, int access, String name, String descriptor) {
            super(api, methodVisitor, access, name, descriptor);
        }

        int s;
        @Override
        protected void onMethodEnter() {
            super.onMethodEnter();
            if(!inject) {
                return;
            }
//            INVOKESTATIC java/lang/System.currentTimeMillis ()J
            invokeStatic(Type.getType("Ljava/lang/System;"), new Method("currentTimeMillis", "()J"));
//            LSTORE 1
            s = newLocal(Type.LONG_TYPE);
            storeLocal(s);
        }

        int e;
        @Override
        protected void onMethodExit(int opcode) {
            super.onMethodExit(opcode);
            if(!inject) {
                return;
            }
//            INVOKESTATIC java/lang/System.currentTimeMillis ()J
            invokeStatic(Type.getType("Ljava/lang/System;"), new Method("currentTimeMillis", "()J"));
//            LSTORE 3
            e = newLocal(Type.LONG_TYPE);
            storeLocal(e);

//            GETSTATIC java/lang/System.out : Ljava/io/PrintStream;
            getStatic(Type.getType("Ljava/lang/System;"), "out", Type.getType("Ljava/io/PrintStream;"));
//            NEW java/lang/StringBuilder
            newInstance(Type.getType("Ljava/lang/StringBuilder;"));
//            DUP
            dup();
//            INVOKESPECIAL java/lang/StringBuilder.<init> ()V
            invokeConstructor(Type.getType("Ljava/lang/StringBuilder;"), new Method("<init>", "()V"));
//            LDC "execute time = "
            visitLdcInsn("execute time = ");
//            INVOKEVIRTUAL java/lang/StringBuilder.append (Ljava/lang/String;)Ljava/lang/StringBuilder;
            invokeVirtual(Type.getType("Ljava/lang/StringBuilder;"), new Method("append","(Ljava/lang/String;)Ljava/lang/StringBuilder;") );
//            LLOAD 3
//            LLOAD 1
            loadLocal(e);
            loadLocal(s);
//            LSUB
            math(SUB, Type.LONG_TYPE);
//            INVOKEVIRTUAL java/lang/StringBuilder.append (J)Ljava/lang/StringBuilder;
            invokeVirtual(Type.getType("Ljava/lang/StringBuilder;"), new Method("append", "(J)Ljava/lang/StringBuilder;"));
//            LDC "ms"
            visitLdcInsn("ms");
//            INVOKEVIRTUAL java/lang/StringBuilder.append (Ljava/lang/String;)Ljava/lang/StringBuilder;
            invokeVirtual(Type.getType("Ljava/lang/StringBuilder;"), new Method("append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;"));
//            INVOKEVIRTUAL java/lang/StringBuilder.toString ()Ljava/lang/String;
            invokeVirtual(Type.getType("Ljava/lang/StringBuilder;"), new Method("toString", "()Ljava/lang/String;"));
//            INVOKEVIRTUAL java/io/PrintStream.println (Ljava/lang/String;)V
            invokeVirtual(Type.getType("Ljava/io/PrintStream;"), new Method("println", "(Ljava/lang/String;)V"));
        }

        boolean inject = false;
        /**
         * 每读到一个注解就执行一次
         * @param descriptor
         * @param visible
         * @return
         */
        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            System.out.println(getName() + "-->>>" + descriptor);
            if("Lcom/sty/xxt/jvmstake/ASMTest;".equals(descriptor)) {
                inject = true;
            }
            return super.visitAnnotation(descriptor, visible);
        }
    }

}
