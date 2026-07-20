package com.inf.farlands.agent;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import org.objectweb.asm.*;

public class WorldGenAgent {

    public static void premain(String args, Instrumentation inst) {
        inst.addTransformer(new BoundingBoxTransformer(), true);
    }
}

class BoundingBoxTransformer implements ClassFileTransformer {

    @Override
    public byte[] transform(
        ClassLoader loader,
        String className,
        Class<?> classBeingRedefined,
        ProtectionDomain pd,
        byte[] buf
    ) {
        if (
            !"net/minecraft/world/level/levelgen/structure/BoundingBox".equals(
                className
            )
        ) return buf;
        System.err.println("[InfFarlands Agent] Patching BoundingBox.<init>");
        ClassReader cr = new ClassReader(buf);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
        cr.accept(
            new ClassVisitor(Opcodes.ASM9, cw) {
                @Override
                public MethodVisitor visitMethod(
                    int access,
                    String name,
                    String desc,
                    String sig,
                    String[] exs
                ) {
                    MethodVisitor mv = super.visitMethod(
                        access,
                        name,
                        desc,
                        sig,
                        exs
                    );
                    if (
                        name.equals("<init>") && desc.equals("(IIIIII)V")
                    ) return new BoundingBoxPatcher(mv);
                    return mv;
                }
            },
            0
        );
        return cw.toByteArray();
    }
}

class BoundingBoxPatcher extends MethodVisitor {

    BoundingBoxPatcher(MethodVisitor mv) {
        super(Opcodes.ASM9, mv);
    }

    @Override
    public void visitCode() {
        mv.visitCode();
        swap(mv, 1, 4);
        swap(mv, 2, 5);
        swap(mv, 3, 6);
    }

    private void swap(MethodVisitor mv, int a, int b) {
        mv.visitVarInsn(Opcodes.ILOAD, a);
        mv.visitVarInsn(Opcodes.ILOAD, b);
        Label skip = new Label();
        mv.visitJumpInsn(Opcodes.IF_ICMPLE, skip);
        mv.visitVarInsn(Opcodes.ILOAD, a);
        mv.visitVarInsn(Opcodes.ISTORE, 7);
        mv.visitVarInsn(Opcodes.ILOAD, b);
        mv.visitVarInsn(Opcodes.ISTORE, a);
        mv.visitVarInsn(Opcodes.ILOAD, 7);
        mv.visitVarInsn(Opcodes.ISTORE, b);
        mv.visitLabel(skip);
    }
}
