package com.example.graphmail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** config.properties から読み込む設定値。 */
public record AppConfig(AuthMode authMode, String clientId, String tenantId,
                        String clientSecret, String targetUser) {

    public enum AuthMode {
        /** 委任アクセス (ユーザーがサインイン)。個人アカウントはこちら。 */
        DEVICE_CODE("device-code"),
        /** アプリケーションアクセス (サインインなし)。企業テナント専用。 */
        CLIENT_SECRET("client-secret");

        private final String key;

        AuthMode(String key) {
            this.key = key;
        }

        static AuthMode of(String value) {
            for (AuthMode m : values()) {
                if (m.key.equalsIgnoreCase(value)) {
                    return m;
                }
            }
            throw new IllegalArgumentException(
                    "auth.mode は device-code か client-secret を指定してください: " + value);
        }
    }

    public static AppConfig load(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IllegalStateException(
                    path.toAbsolutePath() + " が見つかりません。config.properties.example をコピーして作成してください。");
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        }

        AuthMode mode = AuthMode.of(props.getProperty("auth.mode", "device-code").trim());
        String clientId = required(props, "client.id");
        String tenantId = required(props, "tenant.id");
        String clientSecret = null;
        String targetUser = null;

        if (mode == AuthMode.CLIENT_SECRET) {
            if (isConsumerOrMultiTenant(tenantId)) {
                throw new IllegalStateException(
                        "client-secret モードでは tenant.id に企業テナントの ID を指定してください"
                        + " (consumers / common / organizations は不可)。");
            }
            clientSecret = required(props, "client.secret");
            targetUser = required(props, "target.user");
        }
        return new AppConfig(mode, clientId, tenantId, clientSecret, targetUser);
    }

    private static boolean isConsumerOrMultiTenant(String tenantId) {
        return tenantId.equalsIgnoreCase("consumers")
                || tenantId.equalsIgnoreCase("common")
                || tenantId.equalsIgnoreCase("organizations");
    }

    private static String required(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("config.properties に " + key + " を設定してください。");
        }
        return value.trim();
    }
}
