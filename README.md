# `JVM`核心原理与插桩技术实现性能监控

[TOC]

## 一、`JVM`基础原理

> 本项目`resources/`目录下有 `垃圾回收.docx` 和 `虚拟机基础.docx`文档，可作为学习资料参考。

### 1.1 `J2EE`体系结构

`J2EE`总体体系结构如下图所示：  

![image](https://github.com/tianyalu/XxtJvmStake/raw/master/show/j2ee_structure.png)  

### 1.2 `JVM`虚拟机

`JVM`虚拟机结构如下图所示：  

![image](https://github.com/tianyalu/XxtJvmStake/raw/master/show/jvm_structure.png)  

`JVM`内存地址顺序如下图所示：  

![image](https://github.com/tianyalu/XxtJvmStake/raw/master/show/jvm_memory_address_order.png)  

**每个线程都会有自己对应的程序计数器和栈。**  

#### 1.2.1 `JVM`程序计数器

`JVM`程序计数器：**当前线程**所执行字节码的行号指示器（程序计数器中只存储**当前线程**执行程序的行号，一个类指针的数据结构），每个线程都有一个独立的程序计数器，各个线程之间的计数器互不影响，独立存储。

`JVM`是线程私有的。

#### 1.2.2 栈&栈帧

栈：也是线程私有的，一般1M左右大小（可调），`Android`中栈（线程）虚拟机和本地方法栈是一起的，不用分开。

栈帧：程序运行时会在栈中为每一个方法单独划分出一个空间，叫做栈帧。

栈帧中包含如下内容：

> 1. 局部变量表：是一种线性表；
> 2. 操作数栈：存储程序执行过程中的各种临时数据；
> 3. 动态链接：方法执行时在内存中的入口地址通过动态链接找到（native方法，对象重新赋值后调用相同的方法）；
> 4. 方法出口：保存方法入口地址。

![image](https://github.com/tianyalu/XxtJvmStake/raw/master/show/thread_stack.png)  

### 1.3 `JVM`程序运行流程

#### 1.3.1 反汇编

我们观察程序运行流程，最接近能看懂的语言便是汇编语言了，所以首先要反汇编`.class`文件获得汇编代码：  

```shell
#java文件 --> class文件
javac Person.java
#反汇编(class文件 --> 汇编)
javap -c Person.class
```

比如如下`Java`代码：  

```java
public int work() {
  int x = 3;
  int y = 5;
  int z = (x + y) * 10;
  return z;
}
```

反汇编后得到如下代码：  

```assembly
public int work();
	Code:
    0: iconst_3    
    1: istore_1
    2: iconst_5
    3: istore_2
    4: iload_1
    5: iload_2
    6: iadd
    7: bipush        10
    9: imul
    10: istore_3
    11: iload_3
    12: ireturn
```

#### 1.3.2 代码执行分析

汇编代码与`Java`代码对照及运行分析如下：  

```assembly
public int work();
	Code:
		# int x = 3;
    0: iconst_3    # 将int型3入操作数栈
    1: istore_1  	 # 将操作数栈中栈顶int型数值出栈并存入局部变量表（下标为1的位置）
    # int y = 5;
    2: iconst_5		 # 将int型5入操作数栈
    3: istore_2		 # 将操作数栈中栈顶int型数值出栈并存入局部变量表（下标为2的位置）
    # int z = (x + y) * 10;
    4: iload_1		 # 将局部变量表中下标为1的int型数据入操作数栈
    5: iload_2		 # 将局部变量表中下标为2的int型数据入操作数栈
    6: iadd				 # 1）将操作数栈顶两int型数值出栈   2）相加   3）将结果压入操作数栈
    7: bipush 10   # 10的值扩展成int型数值入操作数栈
    9: imul				 # 1）将操作数栈顶两int型数值出栈   2）相称   3）将结果压入操作数栈
    10: istore_3   # 将操作数栈中栈顶int型数值，存入局部变量表（下标为3的位置）
    # return z;
    11: iload_3 	 # 将局部变量表中下标为3的int型数据入操作数栈
    12: ireturn		 # 将操作数栈栈顶数据返回到程序方法调用处
```

`Person`类中的入口方法如下：

```java
public static void main(String[] args) {
  Person person = new Person();
  person.work();
  person.hashCode();
}
```

当执行`person.work();`方法时，**字节码执行引擎** 会把 `work()`方法所对应的对象的引用先放入到局部变量表，如下图所示的`this`：  

![image](https://github.com/tianyalu/XxtJvmStake/raw/master/show/thread_method_execute_process.png)  

其余执行流程分析可参考上述代码中的注释。  

## 二、工具&资源

### 2.1 `asm`

`ASM`是被设计用于运行时的，离线的类生成和转换，作用于已编译好的`Java class`，并且被设计的尽可能的小巧快速，其目的是生成、转换和分析以字节数组表示的已编译 `Java` 类(它们在磁盘中的存储 和在 `Java` 虚拟机中的加载都采用这种字节数组形式)。为此，`ASM` 提供了一些工具，使用高于字节级别的概念来读写和转换这种字节数组，这些概念包括数值常数、字符串、`Java` 标识符、`Java` 类型、`Java` 类结构元素，等等。注意，`ASM` 库的范围严格限制于类的读、写、转换和分析。具体来说，类的加载过程就超出了它的范围之外。

```groovy
implementation 'org.ow2.asm:asm:7.1'
implementation 'org.ow2.asm:asm-commons:7.1'
```

可以通过官方文档来学习 [asm4-guide.pdf](https://asm.ow2.io/asm4-guide.pdf) 。  

### 2.2 `ASM ByteCode Viewer`

`ASM ByteCode Viewer`插件可以同步显示`Java`文件的汇编代码，从`Android Studio`从插件市场之间搜索就可以安装了，其效果截图如下：  

![image](https://github.com/tianyalu/XxtJvmStake/raw/master/show/asm_byte_code_viewer.png)   

### 2.3 `asm-bo-0.3.5`

`asm-bo-0.3.5`是一个`Android Studio` 插件（在本项目`resources/`目录下，离线安装即可）。它可以将汇编代码转为对应的`Java`实现，方便我们修改（甚至重写）汇编代码，实现字节码插桩（或其它效果）。其效果截图如下：  

![image](https://github.com/tianyalu/XxtJvmStake/raw/master/show/asm_bo.png)   

## 三、插桩实现流程

### 3.1 总体实现思路

`ASM`字节码插桩实现性能监控从根本上讲是利用`ASM`修改类的字节码文件，在特定的位置插入性能监控的代码，从而实现我们想要的效果，插桩流程总体思路如下图所示：  

![image](https://github.com/tianyalu/XxtJvmStake/raw/master/show/asm_stake_process.png)  

### 3.2 具体实现

方法签名参考表：  

| Java类型 | 类型标识             |
| -------- | -------------------- |
| boolean  | Z                    |
| byte     | B                    |
| char     | C                    |
| short    | S                    |
| int      | I                    |
| long     | J                    |
| float    | F                    |
| double   | D                    |
| String   | L/java/lang/String;  |
| int[]    | [I                   |
| Object[] | [L/java/lang/Object; |

#### 3.2.1 生成目标（要插桩）的`class`文件

`InjectTest.java`(依赖 `ASMTest.java`) 生成 `InjectTest.class`文件：  

```bash
# 绝对路径
javac -cp /Users/tian/NeCloud/xxt/workspace/XxtJvmStake/app/src/test/java/ InjectTest.java
# 或者相对路径（当前在 /Users/tian/NeCloud/xxt/workspace/XxtJvmStake/app/src/test/java/com/
# sty/xxt/jvmstake/ 目录）
javac -cp ../../../../ InjectTest.java
```
> `Java`文件生成`.class`，当有文件或库依赖时，需要加上`-cp`参数，后面跟依赖文件或依赖库路径（相对/绝对路径均可），但是该路径对于本项目`Java`文件只需要到`src/main/java/`即可，后面不需要跟具体包名路径了，因为`Java`的编译器会从`src/main/java/`开始找你在目标文件中`import`的包路径下（两者路径拼接）的文件。  
>参考：[关于久违的Javac，编译出现“找不到符号”](jianshu.com/p/584cc4ba792e) 。

#### 3.2.2 创建自己的`ClassVisitor`类

```java
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
```

#### 3.2.3 反汇编3.2.1生成的`class`文件

利用`javap -c`命令或者`asm-bo`插件，生成目标`class`文件的反汇编文件，方便下一步使用。  

#### 3.2.4 创建自己的`MethodVisitor`类

```java
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
    // INVOKESTATIC java/lang/System.currentTimeMillis ()J
    invokeStatic(Type.getType("Ljava/lang/System;"), new Method("currentTimeMillis", "()J"));
    // LSTORE 1
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
    // INVOKESTATIC java/lang/System.currentTimeMillis ()J
    invokeStatic(Type.getType("Ljava/lang/System;"), new Method("currentTimeMillis", "()J"));
    // LSTORE 3
    e = newLocal(Type.LONG_TYPE);
    storeLocal(e);

    // GETSTATIC java/lang/System.out : Ljava/io/PrintStream;
    getStatic(Type.getType("Ljava/lang/System;"), "out", Type.getType("Ljava/io/PrintStream;"));
    // NEW java/lang/StringBuilder
    newInstance(Type.getType("Ljava/lang/StringBuilder;"));
    // DUP
    dup();
    // INVOKESPECIAL java/lang/StringBuilder.<init> ()V
    invokeConstructor(Type.getType("Ljava/lang/StringBuilder;"), new Method("<init>", "()V"));
    // LDC "execute time = "
    visitLdcInsn("execute time = ");
    // INVOKEVIRTUAL java/lang/StringBuilder.append (Ljava/lang/String;)Ljava/lang/StringBuilder;
    invokeVirtual(Type.getType("Ljava/lang/StringBuilder;"), new Method("append","(Ljava/lang/String;)Ljava/lang/StringBuilder;") );
    // LLOAD 3
    // LLOAD 1
    loadLocal(e);
    loadLocal(s);
    // LSUB
    math(SUB, Type.LONG_TYPE);
    // INVOKEVIRTUAL java/lang/StringBuilder.append (J)Ljava/lang/StringBuilder;
    invokeVirtual(Type.getType("Ljava/lang/StringBuilder;"), new Method("append", "(J)Ljava/lang/StringBuilder;"));
    // LDC "ms"
    visitLdcInsn("ms");
    // INVOKEVIRTUAL java/lang/StringBuilder.append (Ljava/lang/String;)Ljava/lang/StringBuilder;
    invokeVirtual(Type.getType("Ljava/lang/StringBuilder;"), new Method("append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;"));
    // INVOKEVIRTUAL java/lang/StringBuilder.toString ()Ljava/lang/String;
    invokeVirtual(Type.getType("Ljava/lang/StringBuilder;"), new Method("toString", "()Ljava/lang/String;"));
    // INVOKEVIRTUAL java/io/PrintStream.println (Ljava/lang/String;)V
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
```

#### 3.2.5 对目标`class`文件进行插桩操作

```java
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
```

