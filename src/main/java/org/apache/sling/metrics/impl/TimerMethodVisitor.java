/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.apache.sling.metrics.impl;

import javax.annotation.Nonnull;

import org.apache.sling.metrics.api.MetricsUtil;
import org.apache.sling.metrics.api.TimerContext;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import com.codahale.metrics.Timer.Context;

/**
 * Adapts method calls wrapping the byte code of the method with calls to a meter.
 */
public class TimerMethodVisitor extends GeneratorAdapter {

    private static final String CONTEXT_CL = TimerContext.class.getName().replace('.', '/');
    private static final String METRICS_UTIL_CL = MetricsUtil.class.getName().replace('.', '/');
    private static final String METRICS_UTIL_GETMETER_DESC = "(Ljava/lang/String;)L"+CONTEXT_CL+";";
    private int contextid;
    private String timerName;

    public TimerMethodVisitor(@Nonnull MethodVisitor mv, int access, @Nonnull String name, @Nonnull String descriptor, @Nonnull String timerName) {
        super(Opcodes.ASM4, mv, access, name, descriptor);
        this.timerName = timerName;
    }

    @Override
    public void visitCode() {
        System.err.println("Adding Start Timer "+timerName);
        mv.visitLdcInsn(timerName);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, METRICS_UTIL_CL, "getTimer", METRICS_UTIL_GETMETER_DESC, false);
        // store the meter.
        contextid = newLocal(Type.getType(Context.class));
        mv.visitVarInsn(Opcodes.ASTORE, contextid);
        super.visitCode();
        System.err.println("Adding Stop Timer");
        mv.visitVarInsn(Opcodes.ALOAD, contextid);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, CONTEXT_CL, "stop", "()V", true);
        System.err.println("Done visit Code "+timerName);
    };
    
}
