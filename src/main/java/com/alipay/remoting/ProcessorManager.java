/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alipay.remoting;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;

import com.alipay.remoting.config.ConfigManager;
import com.alipay.remoting.log.BoltLoggerFactory;

/**
 * Manager of processors 处理器管理类<br>
 * Maintains the relationship between command and command processor through command code.
 * 
 * @author jiangping
 * @version $Id: ProcessorManager.java, v 0.1 Sept 6, 2015 2:49:47 PM tao Exp $
 */
public class ProcessorManager {
    private static final Logger                                  logger         = BoltLoggerFactory
                                                                                    .getLogger("CommonDefault");
    private ConcurrentHashMap<CommandCode, RemotingProcessor<?>> cmd2processors = new ConcurrentHashMap<CommandCode, RemotingProcessor<?>>(
                                                                                    4);

    private RemotingProcessor<?>                                 defaultProcessor;

    /** The default executor, if no executor is set for processor, this one will be used 如果没有为处理类设置线程池,则默认此线程池*/
    private ExecutorService                                      defaultExecutor;

    /**
     * minPoolSize(线程池的基本大小): 20
     * maxPoolSize(线程池最大大小): 400
     * queueSize(任务队列大小): 600
     * keepAliveTime(线程活动保持时间): 60s
     */
    private int                                                  minPoolSize    = ConfigManager
                                                                                    .default_tp_min_size();

    private int                                                  maxPoolSize    = ConfigManager
                                                                                    .default_tp_max_size();

    private int                                                  queueSize      = ConfigManager
                                                                                    .default_tp_queue_size();

    private long                                                 keepAliveTime  = ConfigManager
                                                                                    .default_tp_keepalive_time();

    // 创建队列大小为600的有界阻塞队列.和名字前缀为Bolt-default-executro的命名线程工厂
    public ProcessorManager() {
        defaultExecutor = new ThreadPoolExecutor(minPoolSize, maxPoolSize, keepAliveTime,
            TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(queueSize), new NamedThreadFactory(
                "Bolt-default-executor", true));
    }

    /**
     * Register processor to process command that has the command code of cmdCode.
     * 注册处理器以处理命令代码为cmdCode的命令。
     * @param cmdCode
     * @param processor
     */
    public void registerProcessor(CommandCode cmdCode, RemotingProcessor<?> processor) {
        if (this.cmd2processors.containsKey(cmdCode)) {
            logger
                .warn(
                    "Processor for cmd={} is already registered, the processor is {}, and changed to {}",
                    cmdCode, cmd2processors.get(cmdCode).getClass().getName(), processor.getClass()
                        .getName());
        }
        this.cmd2processors.put(cmdCode, processor);
    }

    /**
     * Register the default processor to process command with no specific processor registered.
     * 
     * @param processor
     */
    public void registerDefaultProcessor(RemotingProcessor<?> processor) {
        if (this.defaultProcessor == null) {
            this.defaultProcessor = processor;
        } else {
            throw new IllegalStateException("The defaultProcessor has already been registered: "
                                            + this.defaultProcessor.getClass());
        }
    }

    /**
     * Get the specific processor with command code of cmdCode if registered, otherwise the default processor is returned.
     * 
     * @param cmdCode
     * @return
     */
    public RemotingProcessor<?> getProcessor(CommandCode cmdCode) {
        RemotingProcessor<?> processor = this.cmd2processors.get(cmdCode);
        if (processor != null) {
            return processor;
        }
        return this.defaultProcessor;
    }

    /**
     * Getter method for property <tt>defaultExecutor</tt>.
     * 
     * @return property value of defaultExecutor
     */
    public ExecutorService getDefaultExecutor() {
        return defaultExecutor;
    }

    /**
     * Set the default executor.
     * 
     * @param executor
     */
    public void registerDefaultExecutor(ExecutorService executor) {
        this.defaultExecutor = executor;
    }

}