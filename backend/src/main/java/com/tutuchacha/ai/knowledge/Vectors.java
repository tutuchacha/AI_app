package com.tutuchacha.ai.knowledge;

/** 向量字面量：MySQL 的 JSON 列与 pgvector 的 vector 输入格式都是 [v0,v1,...]。 */
final class Vectors {

    private Vectors() {
    }

    static String toLiteral(float[] vector) {
        StringBuilder literal = new StringBuilder(vector.length * 8).append('[');
        for (int i = 0; i < vector.length; i += 1) {
            if (i > 0) {
                literal.append(',');
            }
            literal.append(vector[i]);
        }
        return literal.append(']').toString();
    }
}
