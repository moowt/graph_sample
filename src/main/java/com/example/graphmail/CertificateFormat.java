package com.example.graphmail;

import java.nio.file.Path;
import java.util.Locale;

/** 証明書ファイルの形式。拡張子で判定する。 */
public enum CertificateFormat {
    /** PEM 形式。秘密鍵 (暗号化なし) と証明書を 1 ファイルに含める。 */
    PEM,
    /** PFX (PKCS#12) 形式。秘密鍵と証明書を含み、パスワードで保護できる。 */
    PFX;

    /**
     * ファイル名の拡張子から形式を判定する。
     *
     * @param path 証明書ファイルのパス
     * @return .pem なら PEM、.pfx / .p12 なら PFX
     * @throws IllegalArgumentException 対応していない拡張子の場合
     */
    public static CertificateFormat of(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".pem")) {
            return PEM;
        }
        if (name.endsWith(".pfx") || name.endsWith(".p12")) {
            return PFX;
        }
        throw new IllegalArgumentException("証明書ファイルは .pem / .pfx / .p12 のいずれかを指定してください: " + path);
    }

    /**
     * 対応している拡張子かどうかを返す。
     *
     * @param path 証明書ファイルのパス
     * @return .pem / .pfx / .p12 なら true
     */
    public static boolean isSupported(Path path) {
        try {
            of(path);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
