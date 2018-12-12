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
package com.alipay.remoting.rpc;

import com.alipay.remoting.CommandCode;
import com.alipay.remoting.InvokeContext;
import com.alipay.remoting.ProtocolCode;
import com.alipay.remoting.RemotingCommand;
import com.alipay.remoting.config.ConfigManager;
import com.alipay.remoting.config.switches.ProtocolSwitch;
import com.alipay.remoting.exception.CodecException;
import com.alipay.remoting.exception.DeserializationException;
import com.alipay.remoting.exception.SerializationException;
import com.alipay.remoting.rpc.protocol.RpcDeserializeLevel;
import com.alipay.remoting.rpc.protocol.RpcProtocol;

/**
 * 远程命令
 * Remoting command. <br>
 * A remoting command stands for a kind of transfer object in the network communication layer.
 * 远程命令代表网络通信层中的一种传输对象
 * @author jiangping
 * @version $Id: RpcCommand.java, v 0.1 2015-9-6 PM5:26:31 tao Exp $
 */
public abstract class RpcCommand implements RemotingCommand {

    /** For serialization 序列化使用 */
    private static final long serialVersionUID = -3570261012462596503L;

    /**
     * Code which stands for the command.
     * 代表命令的代码
     */
    private CommandCode       cmdCode;
    /* command version  命令版本 */
    private byte              version          = 0x1;
    private byte              type;
    /**
     * Serializer, see the Configs.SERIALIZER_DEFAULT for the default serializer.
     * 序列化程序，请参阅Configs.SERIALIZER_DEFAULT以获取默认序列化程序
     * Notice: this can not be changed after initialized at runtime. 在运行时初始化后无法更改
     */
    private byte              serializer       = ConfigManager.serializer;
    /**
     * protocol switches 协议开关
     */
    private ProtocolSwitch    protocolSwitch   = new ProtocolSwitch();
    private int               id;
    /** The length of clazz class的长度 */
    private short             clazzLength      = 0;
    private short             headerLength     = 0;
    private int               contentLength    = 0;
    /** The class of content 内容类 */
    private byte[]            clazz;
    /** Header is used for transparent transmission. 头部用于透明传输 */
    private byte[]            header;
    /** The bytes format of the content of the command. 命令内容的字节格式 */
    private byte[]            content;
    /** invoke context of each rpc command. 调用每个rpc命令的上下文 */
    private InvokeContext     invokeContext;

    public RpcCommand() {
    }

    public RpcCommand(byte type) {
        this();
        this.type = type;
    }

    public RpcCommand(CommandCode cmdCode) {
        this();
        this.cmdCode = cmdCode;
    }

    public RpcCommand(byte type, CommandCode cmdCode) {
        this(cmdCode);
        this.type = type;
    }

    public RpcCommand(byte version, byte type, CommandCode cmdCode) {
        this(type, cmdCode);
        this.version = version;
    }

    /**
     * Serialize  the class header and content.
     * 序列化类,头部,内容
     * @throws Exception
     */
    @Override
    public void serialize() throws SerializationException {
        this.serializeClazz();
        this.serializeHeader(this.invokeContext);
        this.serializeContent(this.invokeContext);
    }

    /**
     * Deserialize the class header and content.
     * 反序列类,头部,内容
     * @throws Exception
     */
    @Override
    public void deserialize() throws DeserializationException {
        this.deserializeClazz();
        this.deserializeHeader(this.invokeContext);
        this.deserializeContent(this.invokeContext);
    }

    /**
     * Deserialize according to mask.
     * 根据掩码反序列化
     * <ol>
     *     <li>If mask <= {@link RpcDeserializeLevel#DESERIALIZE_CLAZZ}, only deserialize clazz - only one part.</li>
     *     如果掩码 <= 反序列化等级(0x00),仅仅反序列化类, 一部分内容
     *     <li>If mask <= {@link RpcDeserializeLevel#DESERIALIZE_HEADER}, deserialize clazz and header - two parts.</li>
     *     如果掩码 <= 反序列化等级(0x01),反序列化类和头部，二部分内容
     *     <li>If mask <= {@link RpcDeserializeLevel#DESERIALIZE_ALL}, deserialize clazz, header and content - all three parts.</li>
     *     如果掩码 <= 反序列化等级(0x02),反序列化类，头部，内容。全部部分。
     * </ol>
     *
     * @param mask
     * @throws CodecException
     */
    public void deserialize(long mask) throws DeserializationException {
        if (mask <= RpcDeserializeLevel.DESERIALIZE_CLAZZ) {
            this.deserializeClazz();
        } else if (mask <= RpcDeserializeLevel.DESERIALIZE_HEADER) {
            this.deserializeClazz();
            this.deserializeHeader(this.getInvokeContext());
        } else if (mask <= RpcDeserializeLevel.DESERIALIZE_ALL) {
            this.deserialize();
        }
    }

    /**
     * Serialize content class.
     * 序列化内容类
     * @throws Exception
     */
    public void serializeClazz() throws SerializationException {

    }

    /**
     * Deserialize the content class.
     * 反序列化内容类
     * @throws Exception
     */
    public void deserializeClazz() throws DeserializationException {

    }

    /**
     * Serialize the header.
     * 序列化头部
     * @throws Exception
     */
    public void serializeHeader(InvokeContext invokeContext) throws SerializationException {
    }

    /**
     * Serialize the content.
     * 序列化内容
     * @throws Exception
     */
    @Override
    public void serializeContent(InvokeContext invokeContext) throws SerializationException {
    }

    /**
     * Deserialize the header.
     * 反序列化头部
     * @throws Exception
     */
    public void deserializeHeader(InvokeContext invokeContext) throws DeserializationException {
    }

    /**
     * Deserialize the content.
     * 反序列化内容
     * 
     * @throws Exception
     */
    @Override
    public void deserializeContent(InvokeContext invokeContext) throws DeserializationException {
    }

    @Override
    public ProtocolCode getProtocolCode() {
        return ProtocolCode.fromBytes(RpcProtocol.PROTOCOL_CODE);
    }

    @Override
    public CommandCode getCmdCode() {
        return cmdCode;
    }

    @Override
    public InvokeContext getInvokeContext() {
        return invokeContext;
    }

    @Override
    public byte getSerializer() {
        return serializer;
    }

    @Override
    public ProtocolSwitch getProtocolSwitch() {
        return protocolSwitch;
    }

    public void setCmdCode(CommandCode cmdCode) {
        this.cmdCode = cmdCode;
    }

    public byte getVersion() {
        return version;
    }

    public void setVersion(byte version) {
        this.version = version;
    }

    public byte getType() {
        return type;
    }

    public void setType(byte type) {
        this.type = type;
    }

    public void setSerializer(byte serializer) {
        this.serializer = serializer;
    }

    public void setProtocolSwitch(ProtocolSwitch protocolSwitch) {
        this.protocolSwitch = protocolSwitch;
    }

    @Override
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public byte[] getHeader() {
        return header;
    }

    public void setHeader(byte[] header) {
        if (header != null) {
            this.header = header;
            this.headerLength = (short) header.length;
        }
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        if (content != null) {
            this.content = content;
            this.contentLength = content.length;
        }
    }

    public short getHeaderLength() {
        return headerLength;
    }

    public int getContentLength() {
        return contentLength;
    }

    public short getClazzLength() {
        return clazzLength;
    }

    public byte[] getClazz() {
        return clazz;
    }

    public void setClazz(byte[] clazz) {
        if (clazz != null) {
            this.clazz = clazz;
            this.clazzLength = (short) clazz.length;
        }
    }

    public void setInvokeContext(InvokeContext invokeContext) {
        this.invokeContext = invokeContext;
    }
}
