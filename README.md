##本组件实现的功能：提供延迟调度功能
###使用方只需要发送带有执行时间的DelayMessage消息，在对应时间即可回调对应方法

###模块介绍
项目目前分为两个模块：delay-core和delay-boot

delay-core实现的调度的核心功能：定义了MessageStory消息存储接口、定义了MessageHandler消息处理接口
    以及Persist消息持久化接口，并且提供了基于内存运行模式的消息存储实现，基于文件系统的消息持久化实现
    然后以时间轮的方式进行轮询获取对应的要执行的消息，同时提供消息持久化操作，对于轮询的频率，持久化的
    频率等都可以支持参数配置，来符合用户的自定义使用场景
delay-boot主要是针对使用者的接入落地：目前提供了基于spring的使用接入方式，因为不同的框架处理方式不一
    样，所以core包中定义的MessageHandler的实现也随着接入的不同不同，目前提供了基于Spring的Handler
    整体上主要包括组件的启动引导，spring的Bean中的调度注解扫描以及构建handler交由core使用    

###使用示例
在使用上比较简单，目前提供基于jvm的内存运行模式，且支持文件系统的持久化，基本保证高可用（kill -9）方式
    除外，因为此种模式是内存模式，所以不存在DB或者redis等第三方组件的依赖

接入Pom
    
    <dependency>
        <groupId>cn.ren</groupId>
        <artifactId>delay-core</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>cn.ren</groupId>
        <artifactId>delay-boot</artifactId>
        <version>1.0-SNAPSHOT</version>
    </dependency>
    
发送延迟消息
```java
    DelayMessage<Persons> pers = new DelayMessage<>();
    Persons ps = new Persons();
    ps.setPhone("17310209565");
    pers.setData(ps);
    pers.setDealKey("test");
    pers.setExecuteTime(System.currentTimeMillis()+1000*second);
    SendDelayMessageUtil.sendDelayMessage(pers);
```
延迟消息接收
```java
 @DelayExecute(dealKey = "test")
     public void executeDelayMessage(Persons persons){
         System.out.println("接收到延迟消息："+persons);
         try {
             Thread.sleep(10000);
         } catch (InterruptedException e) {
             e.printStackTrace();
         }
         System.out.println("接收到延迟消息处理完成："+persons);
     }   
```