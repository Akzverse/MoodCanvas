package com.moodcanvas.util;
import java.nio.charset.StandardCharsets;import java.security.*;import java.util.HexFormat;
public final class PasswordUtil {private PasswordUtil(){}public static String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}}
