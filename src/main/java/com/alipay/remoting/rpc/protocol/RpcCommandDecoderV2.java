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

import java.net.InetSocketAddress;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alipay.remoting.CommandCode;
import com.alipay.remoting.CommandDecoder;
import com.alipay.remoting.ResponseStatus;
import com.alipay.remoting.config.switches.ProtocolSwitch;
import com.alipay.remoting.rpc.HeartbeatAckCommand;
import com.alipay.remoting.rpc.HeartbeatCommand;
import com.alipay.remoting.rpc.RequestCommand;
import com.alipay.remoting.rpc.ResponseCommand;
import com.alipay.remoting.rpc.RpcCommandType;
import com.alipay.remoting.util.CrcUtil;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;

/**
 * Command decoder for Rpc v2.
 * Rpc的命令解码器
 *
 * @author jiangping
 * @version $Id: RpcCommandDecoderV2.java, v 0.1 2017-05-27 PM5:15:26 tao Exp $
 */
public class RpcCommandDecoderV2 implements CommandDecoder {

    private static final Logger logger = LoggerFactory.getLogger("RpcRemoting");

    private int                 lessLen;

    {
        lessLen = RpcProtocolV2.getResponseHeaderLength() < RpcProtocolV2.getRequestHeaderLength() ? RpcProtocolV2
            .getResponseHeaderLength() : RpcProtocolV2.getRequestHeaderLength();
    }

    /**
     * @see CommandDecoder#decode(ChannelHandlerContext, ByteBuf, List)
     */
    @Override
    public void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // 请求报文头部和回复报文头部的最小长度
        // the less length between response header and request header
        if (in.readableBytes() >= lessLen) {
            //保存当前的读指针
            in.markReaderIndex();
            //读取协议魔数
            byte protocol = in.readByte();
            //恢复读指针到原来的位置,即 in.mark..位置
            in.resetReaderIndex();
            if (protocol == RpcProtocolV2.PROTOCOL_CODE) {
                /*
                 * ver: version for protocol
                 * type: request/response/request oneway
                 * cmdcode: code for remoting command
                 * ver2:version for remoting command
                 * requestId: id of request
                 * codec: code for codec
                 * switch: function switch
                 * (req)timeout: request timeout
                 * (resp)respStatus: response status
                 * classLen: length of request or response class name
                 * headerLen: length of header
                 * contentLen: length of content
                 * className
                 * header
                 * content
                 */
                if (in.readableBytes() > 2 + 1) {
                    int startIndex = in.readerIndex();
                    in.markReaderIndex();
                    in.readByte(); //protocol code 魔数
                    byte version = in.readByte(); //protocol version 协议版本
                    byte type = in.readByte(); //type RPC命令类型
                    if (type == RpcCommandType.REQUEST || type == RpcCommandType.REQUEST_ONEWAY) {
                        //decode request 因已经读取三个byte了,所以需要减3
                        if (in.readableBytes() >= RpcProtocolV2.getRequestHeaderLength() - 3) {
                            short cmdCode = in.readShort();
                            byte ver2 = in.readByte();
                            int requestId = in.readInt();
                            byte serializer = in.readByte();
                            byte protocolSwitchValue = in.readByte();
                            int timeout = in.readInt();
                            short classLen = in.readShort();
                            short headerLen = in.readShort();
                            int contentLen = in.readInt();
                            byte[] clazz = null;
                            byte[] header = null;
                            byte[] content = null;

                            // decide the at-least bytes length for each version
                            int lengthAtLeastForV1 = classLen + headerLen + contentLen;
                            //判断协议是否开启CRC,如有,最小bytes长度加4
                            boolean crcSwitchOn = ProtocolSwitch.isOn(
                                ProtocolSwitch.CRC_SWITCH_INDEX, protocolSwitchValue);
                            int lengthAtLeastForV2 = classLen + headerLen + contentLen;
                            if (crcSwitchOn) {
                                lengthAtLeastForV2 += 4;// crc int
                            }

                            // 如果满足V1协议且长度大于最小V1协议长度 或 满足V2协议且长度大于最小V2协议长度,则继续读取
                            // continue read
                            if ((version == RpcProtocolV2.PROTOCOL_VERSION_1 && in.readableBytes() >= lengthAtLeastForV1)
                                || (version == RpcProtocolV2.PROTOCOL_VERSION_2 && in
                                    .readableBytes() >= lengthAtLeastForV2)) {
                                // 读取类
                                if (classLen > 0) {
                                    clazz = new byte[classLen];
                                    in.readBytes(clazz);
                                }
                                // 读取头部
                                if (headerLen > 0) {
                                    header = new byte[headerLen];
                                    in.readBytes(header);
                                }
                                // 读取内容
                                if (contentLen > 0) {
                                    content = new byte[contentLen];
                                    in.readBytes(content);
                                }
                                if (version == RpcProtocolV2.PROTOCOL_VERSION_2 && crcSwitchOn) {
                                    //校验内容
                                    checkCRC(in, startIndex);
                                }
                            } else {// not enough data 不足够的数据,重置读指针
                                in.resetReaderIndex();
                                return;
                            }

                            RequestCommand command;
                            //判断是心跳命令还是请求命令
                            if (cmdCode == CommandCode.HEARTBEAT_VALUE) {
                                command = new HeartbeatCommand();
                            } else {
                                command = createRequestCommand(cmdCode);
                            }
                            //封装实体
                            command.setType(type);
                            command.setVersion(ver2);
                            command.setId(requestId);
                            command.setSerializer(serializer);
                            command.setProtocolSwitch(ProtocolSwitch.create(protocolSwitchValue));
                            command.setTimeout(timeout);
                            command.setClazz(clazz);
                            command.setHeader(header);
                            command.setContent(content);

                            out.add(command);
                        } else {
                            in.resetReaderIndex();
                        }
                    } else if (type == RpcCommandType.RESPONSE) {
                        //decode response
                        if (in.readableBytes() >= RpcProtocolV2.getResponseHeaderLength() - 3) {
                            short cmdCode = in.readShort();
                            byte ver2 = in.readByte();
                            int requestId = in.readInt();
                            byte serializer = in.readByte();
                            byte protocolSwitchValue = in.readByte();
                            short status = in.readShort();
                            short classLen = in.readShort();
                            short headerLen = in.readShort();
                            int contentLen = in.readInt();
                            byte[] clazz = null;
                            byte[] header = null;
                            byte[] content = null;

                            // decide the at-least bytes length for each version
                            int lengthAtLeastForV1 = classLen + headerLen + contentLen;
                            boolean crcSwitchOn = ProtocolSwitch.isOn(
                                ProtocolSwitch.CRC_SWITCH_INDEX, protocolSwitchValue);
                            int lengthAtLeastForV2 = classLen + headerLen + contentLen;
                            if (crcSwitchOn) {
                                lengthAtLeastForV2 += 4;// crc int
                            }

                            // continue read
                            if ((version == RpcProtocolV2.PROTOCOL_VERSION_1 && in.readableBytes() >= lengthAtLeastForV1)
                                || (version == RpcProtocolV2.PROTOCOL_VERSION_2 && in
                                    .readableBytes() >= lengthAtLeastForV2)) {
                                if (classLen > 0) {
                                    clazz = new byte[classLen];
                                    in.readBytes(clazz);
                                }
                                if (headerLen > 0) {
                                    header = new byte[headerLen];
                                    in.readBytes(header);
                                }
                                if (contentLen > 0) {
                                    content = new byte[contentLen];
                                    in.readBytes(content);
                                }
                                if (version == RpcProtocolV2.PROTOCOL_VERSION_2 && crcSwitchOn) {
                                    checkCRC(in, startIndex);
                                }
                            } else {// not enough data
                                in.resetReaderIndex();
                                return;
                            }
                            ResponseCommand command;
                            if (cmdCode == CommandCode.HEARTBEAT_VALUE) {
                                command = new HeartbeatAckCommand();
                            } else {
                                command = createResponseCommand(cmdCode);
                            }
                            command.setType(type);
                            command.setVersion(ver2);
                            command.setId(requestId);
                            command.setSerializer(serializer);
                            command.setProtocolSwitch(ProtocolSwitch.create(protocolSwitchValue));
                            command.setResponseStatus(ResponseStatus.valueOf(status));
                            command.setClazz(clazz);
                            command.setHeader(header);
                            command.setContent(content);
                            command.setResponseTimeMillis(System.currentTimeMillis());
                            command.setResponseHost((InetSocketAddress) ctx.channel()
                                .remoteAddress());

                            out.add(command);
                        } else {
                            in.resetReaderIndex();
                        }
                    } else {
                        String emsg = "Unknown command type: " + type;
                        logger.error(emsg);
                        throw new RuntimeException(emsg);
                    }
                }

            } else {
                //发现魔数异常，抛出不知道的协议错误!
                String emsg = "Unknown protocol: " + protocol;
                logger.error(emsg);
                throw new RuntimeException(emsg);
            }

        }
    }

    private void checkCRC(ByteBuf in, int startIndex) {
        int endIndex = in.readerIndex();
        int expectedCrc = in.readInt();
        byte[] frame = new byte[endIndex - startIndex];
        in.getBytes(startIndex, frame, 0, endIndex - startIndex);
        int actualCrc = CrcUtil.crc32(frame);
        if (expectedCrc != actualCrc) {
            String err = "CRC check failed!";
            logger.error(err);
            throw new RuntimeException(err);
        }
    }

    private ResponseCommand createResponseCommand(short cmdCode) {
        ResponseCommand command = new RpcResponseCommand();
        command.setCmdCode(RpcCommandCode.valueOf(cmdCode));
        return command;
    }

    private RpcRequestCommand createRequestCommand(short cmdCode) {
        RpcRequestCommand command = new RpcRequestCommand();
        command.setCmdCode(RpcCommandCode.valueOf(cmdCode));
        command.setArriveTime(System.currentTimeMillis());
        return command;
    }

}
