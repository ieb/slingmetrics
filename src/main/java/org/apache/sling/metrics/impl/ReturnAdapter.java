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
import javax.annotation.Nullable;

import org.apache.sling.metrics.api.MetricsUtil;
import org.apache.sling.metrics.api.ReturnCapture;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;

public class ReturnAdapter extends AdviceAdapter {

    private static final String RETURN_CAPTURE_CL = ReturnCapture.class.getName().replace('.', '/');
    private static final String RETURN_CAPTURE_DESC = "(Ljava/lang/Object;Ljava/lang/String;)V";
    private static final String RETURN_METHOD_CAPTURE_DESC = "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)V";
    private static final String COUNT_CAPTURE = "countCapture";
    private static final String MARK_CAPTURE = "markCapture";
    

    private String recorderMethodName;
    private String timerName;
    private String keyMethodName;
    

    public ReturnAdapter(@Nonnull MethodVisitor mv, int access, @Nonnull String name,
            @Nonnull String desc, @Nonnull String timerName, @Nullable String keyMethodName,
            boolean mark) {
        super(Opcodes.ASM4, mv, access, name, desc);
        if (mark) {
            recorderMethodName = MARK_CAPTURE;
        } else {
            recorderMethodName = COUNT_CAPTURE;
        }
        this.keyMethodName = keyMethodName;
        this.timerName = name;
        System.err.println(" Return Adapter on "+name+" "+desc);
    }
    

    @Override
    protected void onMethodExit(int opcode) {
        switch(opcode) {
            case Opcodes.IRETURN:
            case Opcodes.LRETURN:
            case Opcodes.FRETURN:
            case Opcodes.DRETURN:
            case Opcodes.ARETURN:
                
               try {
                   addReturnMetric(opcode);
               } catch (Exception e) {
                   e.printStackTrace();
               }
        }
        super.onMethodExit(opcode);
    }
    
    
    public void addReturnMetric(int opcode) {
        
        System.err.println(" Return value being called ");
        // make a copt of the top of the stack so the return value can ba passed to the resource
        // Class.record(returnValue, timerName);
        if (Opcodes.DRETURN == opcode || Opcodes.LRETURN == opcode) {
            System.err.println("DUP2");
            mv.visitInsn(Opcodes.DUP2);            
        } else {
            System.err.println("DUP");
            mv.visitInsn(Opcodes.DUP);
        }
        
        System.err.println("Name is "+keyMethodName);
        
        if (keyMethodName == null ) {
            mv.visitLdcInsn(timerName);
            System.err.println("Call static "+RETURN_CAPTURE_CL+" "+recorderMethodName+" "+RETURN_CAPTURE_DESC);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, RETURN_CAPTURE_CL, recorderMethodName, RETURN_CAPTURE_DESC, false);            
        } else {
            mv.visitLdcInsn(timerName);
            mv.visitLdcInsn(keyMethodName);
            System.err.println("Call static "+RETURN_CAPTURE_CL+" "+recorderMethodName+" "+RETURN_METHOD_CAPTURE_DESC);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, RETURN_CAPTURE_CL, recorderMethodName, RETURN_METHOD_CAPTURE_DESC, false);            
        }
    }

}
