# 前言

SOFABolt 是蚂蚁金融服务集团开发的一套基于 Netty 实现的网络通信框架。

- 为了让 Java 程序员能将更多的精力放在基于网络通信的业务逻辑实现上，而不是过多的纠结于网络底层 NIO 的实现以及处理难以调试的网络问题，Netty 应运而生。
- 为了让中间件开发者能将更多的精力放在产品功能特性实现上，而不是重复地一遍遍制造通信框架的轮子，SOFABolt 应运而生。

Bolt 名字取自迪士尼动画-闪电狗，是一个基于 Netty 最佳实践的轻量、易用、高性能、易扩展的通信框架。 这些年蚂蚁金融服务集团在微服务与消息中间件在网络通信上解决过很多问题，积累了很多经验，并持续的进行着优化和完善，希望能把总结出的解决方案沉淀到 SOFABolt 这个基础组件里，让更多的使用网络通信的场景能够统一受益。 目前该产品已经运用在了蚂蚁中间件的微服务 ([SOFARPC](https://github.com/alipay/sofa-rpc))、消息中心、分布式事务、分布式开关、以及配置中心等众多产品上。



# 调试环境搭建

## 依赖工具

- Maven

- Git

- JDK

- IntelliJ IDEA

## 源码拉取

从官方仓库https://github.com/alipay/sofa-bolt `Fork` 出属于自己的仓库，为什么要`Fork` ? 既然开始阅读、调试源码，我们可能会写一些注释，有了自己的仓库，可以进行自由的提交。:smiling_imp:

使用 `IntelliJ IDEA` 从 `Fork` 出来的仓库拉取代码。

## example 模块

在test模块里，官网提供了多个Bolt的使用示例。

我们提供了一个 RpcClient 与 RpcServer，经过简单的必要功能初始化，或者功能开关，即可使用。

### RpcServer

执行 `com.alipay.remoting.demo.RpcServerDemoByMain` 的 `#main(args)` 方法，启动服务端。输出日志如下：

```
Sofa-Middleware-Log SLF4J : Actual binding is of type [ com.alipay.remoting Log4j2 ]
server start ok!
```

### RpcClient

执行 `com.alipay.remoting.demo.RpcClientDemoByMain` 的 `#main(args)` 方法，启动服务端。输出日志如下：

```
Sofa-Middleware-Log SLF4J : Actual binding is of type [ com.alipay.remoting Log4j2 ]
invoke sync result = [HELLO WORLD! I'm server return]
```

如此，我们就可以愉快的进行 Netty 调试啦。读源码，一定要多多调试源码。非常重要！！！:imp:



# 私有通讯协议设计

![](/home/james/文档/Bolt-Protocol.png)

​                                                                     图1 - 私有协议与必要功能模块

## Protocol

| 字段名                | 字节范围       | 备注                                      |
| --------------------- | -------------- | ----------------------------------------- |
| proto                 | 1字节          | 协议的魔数                                |
| ver1                  | 1字节          | 协议版本                                  |
| type                  | 1字节          | (1)request (2)response (3) request oneway |
| cmdcode               | 2字节          | 远程命令代码                              |
| ver2                  | 1字节          | 远程命令版本                              |
| requestId             | 4字节          | 请求ID                                    |
| codec                 | 1字节          | 编解码代码                                |
| switch                | 1字节          | 协议功能开关                              |
| timeout或者respstatus | 4字节或者2字节 | 请求超时或者回复状态                      |
| classLen              | 2字节          | 请求或响应类名称的长度                    |
| headerLen             | 2字节          | 协议头长度                                |
| contentLen            | 4字节          | 协议内容长度                              |
| content               | N字节          | 内容                                      |
| CRC32(optional)       | 4字节          | 帧的CRC32（当ver1> 1时存在）              |

> 在Bolt通讯框架中，有2个协议规范。因为设计误差，其中RpcProtocol这个协议版本被废弃，以下的解读为RpcProtocolV2版本。

1. 首先，第一个字段是魔数，通常情况下为固定的几个字节（我们这边规定为1个字节）。                          为什么需要这个字段，而且还是一个固定的数？假设我们在服务器上开了一个端口，比如 80 端口，如果没有这个魔数，任何数据包传递到服务器，服务器都会根据自定义协议来进行处理，包括不符合自定义协议规范的数据包。                                                                                                                                               例如，我们直接通过 `http://服务器ip` 来访问服务器（默认为 80 端口）， 服务端收到的是一个标准的 HTTP 协议数据包，但是它仍然会按照事先约定好的协议来处理 HTTP 协议，显然，这是会解析出错的。而有了这个魔数之后，服务端首先取出前面四个字节进行比对，能够在第一时间识别出这个数据包并非是遵循自定义协议的，也就是无效数据包，为了安全考虑可以直接关闭连接以节省资源。                                        在 Java 的字节码的二进制文件中，开头的 1 个字节为`（byte）2` 用来标识这是个字节码文件，亦是异曲同工之妙。
2. 接下来一个字节为版本号，通常情况下是预留字段，用于协议升级的时候用到，有点类似 TCP 协议中的一个字段标识是 IPV4 协议还是 IPV6 协议，其中第一个版本为(byte) 1，第二个版本为(byte) 2。
3. 第三部分，type表示Rpc类型是请求命令还是回复命令。其中请求命令分为request_oneway和request,其中request_oneway代表单工，即只请求，不用回复。而request就是常规的请求回复模型。
4. 第四部分是远程命令代码，远程命令代码代表一种特定的远程命令，每种命令有自己的编号。其中在Bolt，(short) 0被心跳所占用，不能被其他命令所使用。
5. 第五部分是远程命令代码版本，其作用和协议版本作用相同，为预留字段，用于远程命令版本升级的时候用到。
6. 第六部分为请求编号，
7. 第七部分为序列化代码，虽然字段标示是codec,但是实际的意思为Serializer,二者是不同的意思。Serializer主要用于将字节反序列化为对象，或将对象序列化为字节。我们可以使用hessian，json，protocol buff等。

## Encoder 与 Decoder