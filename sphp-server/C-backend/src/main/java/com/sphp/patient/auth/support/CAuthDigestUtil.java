package com.sphp.patient.auth.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * C端认证摘要工具。
 */
public final class CAuthDigestUtil {

    /**
     * 计算字符串的 SHA-256 十六进制摘要。
     *
     * @param input 待摘要字符串
     * @return 小写十六进制摘要
     * @throws IllegalStateException 当前运行环境不支持 SHA-256 时抛出
     */
    public static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }

    /**
     * 防止工具类被实例化。
     */
    private CAuthDigestUtil() {
    }
}
