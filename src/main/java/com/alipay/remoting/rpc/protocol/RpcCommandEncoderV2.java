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
package com.alipay.remoting.rpc.protocol;

import java.io.Serializable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alipay.remoting.CommandEncoder;
import com.alipay.remoting.Connection;
import com.alipay.remoting.config.switches.ProtocolSwitch;
import com.alipay.remoting.rpc.RequestCommand;
import com.alipay.remoting.rpc.ResponseCommand;
import com.alipay.remoting.rpc.RpcCommand;
import com.alipay.remoting.util.CrcUtil;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.Attribute;

/**
 * Encode remoting command into ByteBuf v2.
 * 编码远程命令成ByteBuf 第二版本
 *
 * @author jiangping
 * @version $Id: RpcCommandEncoderV2.java, v 0.1 2017-05-27 PM8:11:27 tao Exp $
 */
public class RpcCommandEncoderV2 implements CommandEncoder {
    /** logger  日志 */
    private static final Logger logger = LoggerFactory.getLogger("RpcRemoting");

    /**
     * @see CommandEncoder#encode(ChannelHandlerContext, Serializable, ByteBuf)
     */
    @Override
    public void encode(ChannelHandlerContext ctx, Serializable msg, ByteBuf out) throws Exception {
        try {
            if (msg instanceof RpcCommand) {
                /*
                 * proto: magic code for protocol 协议的魔数
                 * ver: version for protocol 协议版本
                 * type: request/response/request oneway Rpc命令类型
                 * cmdcode: code for remoting command 远程命令代码
                 * ver2:version for remoting command 远程命令版本
                 * requestId: id of request 请求编号
                 * codec: code for codec 序列化代码
                 * switch: function switch 协议功能开关
                 * (req)timeout: request timeout. 当命令类型是请求时,此位置为超时时间,4个字节
                 * (resp)respStatus: response status 当命令类型是回复时，此位置为回复状态,2个字节
                 * classLen: length of request or response class name 请求类和回复类的长度
                 * headerLen: length of header 头部长度
                 * cotentLen: length of content 内容长度
                 * className 类名
                 * header 协议
                 * content 内容
                 * crc (optional) 帧的CRC32(当ver1 > 1时存在)
                 */
                int index = out.writerIndex(); //写指针
                RpcCommand cmd = (RpcCommand) msg;
                //写入版本魔数 (byte) 2
                out.writeByte(RpcProtocolV2.PROTOCOL_CODE);
                //从连接属性中获取协议版本
                Attribute<Byte> version = ctx.channel().attr(Connection.VERSION);
                byte ver = RpcProtocolV2.PROTOCOL_VERSION_1;
                if (version != null && version.get() != null) {
                    ver = version.get();
                }
                //写入协议版本
                out.writeByte(ver);
                //写入RPC类型代码
                out.writeByte(cmd.getType());
                //写入RPC远程命令代码值
                out.writeShort(((RpcCommand) msg).getCmdCode().value());
                //写入远程命令版本
                out.writeByte(cmd.getVersion());
                //写入Rpc编号
                out.writeInt(cmd.getId());
                //写入协议序列化值
                out.writeByte(cmd.getSerializer());
                //写入协议功能开关
                out.writeByte(cmd.getProtocolSwitch().toByte());
                // 判断命令是RequestCommand还是ResponseCommand来写入超时还是回复状态值
                if (cmd instanceof RequestCommand) {
                    //timeout
                    out.writeInt(((RequestCommand) cmd).getTimeout());
                }
                if (cmd instanceof ResponseCommand) {
                    //response status
                    ResponseCommand response = (ResponseCommand) cmd;
                    out.writeShort(response.getResponseStatus().getValue());
                }
                //写入类长度
                out.writeShort(cmd.getClazzLength());
                //写入头部长度
                out.writeShort(cmd.getHeaderLength());
                //写入内容长度
                out.writeInt(cmd.getContentLength());
                //写入类
                if (cmd.getClazzLength() > 0) {
                    out.writeBytes(cmd.getClazz());
                }
                //写入头部
                if (cmd.getHeaderLength() > 0) {
                    out.writeBytes(cmd.getHeader());
                }
                //写入内容
                if (cmd.getContentLength() > 0) {
                    out.writeBytes(cmd.getContent());
                }
                //通过判断协议是v2且crc功能是开启的,对内容进行循环冗余校验
                if (ver == RpcProtocolV2.PROTOCOL_VERSION_2
                    && cmd.getProtocolSwitch().isOn(ProtocolSwitch.CRC_SWITCH_INDEX)) {
                    // compute the crc32 and write to out
                    byte[] frame = new byte[out.readableBytes()];
                    out.getBytes(index, frame);
                    out.writeInt(CrcUtil.crc32(frame));
                }
            } else {
                // 抛出异常
                String warnMsg = "msg type [" + msg.getClass() + "] is not subclass of RpcCommand";
                logger.warn(warnMsg);
            }
        } catch (Exception e) {
            logger.error("Exception caught!", e);
            throw e;
        }
    }
}
