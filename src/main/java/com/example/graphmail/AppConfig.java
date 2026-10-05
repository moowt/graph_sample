package com.example.graphmail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * config.properties から読み込む設定値。
 *
 * <p>record の {@code toString()} はすべての値を含むため、{@code clientSecret} が出力される点に注意。
 *
 * @param cloud        接続先のクラウド (グローバル版 / 中国版)
 * @param authMode     認証モード
 * @param clientId     Entra ID アプリ登録のクライアント ID
 * @param tenantId     テナント ID (consumers / organizations などの特殊値を含む)
 * @param clientSecret クライアントシークレット (client-secret モードのみ。それ以外は null)
 * @param targetUser   取得対象のメールボックスの UPN またはユーザー ID (client-secret モードのみ。それ以外は null)
 */
public record AppConfig(Cloud cloud, AuthMode authMode, String clientId, String tenantId,
                        String clientSecret, String targetUser) {

    /** 接続先のクラウド。認証とGraphのエンドポイントがクラウドごとに異なる。 */
    public enum Cloud {
        /** グローバル版 Microsoft 365。 */
        GLOBAL("global", "https://login.microsoftonline.com", "https://graph.microsoft.com"),
        /** 21Vianet が運営する中国版 Microsoft 365。 */
        CHINA("china", "https://login.chinacloudapi.cn", "https://microsoftgraph.chinacloudapi.cn");

        private final String key;
        private final String authorityHost;
        private final String graphEndpoint;

        Cloud(String key, String authorityHost, String graphEndpoint) {
            this.key = key;
            this.authorityHost = authorityHost;
            this.graphEndpoint = graphEndpoint;
        }

        /**
         * トークンを発行する Entra ID のエンドポイントを返す。
         *
         * @return 認証エンドポイント (例: {@code https://login.microsoftonline.com})
         */
        public String authorityHost() {
            return authorityHost;
        }

        /**
         * Graph API のエンドポイントを返す。
         *
         * @return Graph のエンドポイント。末尾スラッシュ・バージョンなし (例: {@code https://graph.microsoft.com})
         */
        public String graphEndpoint() {
            return graphEndpoint;
        }

        static Cloud of(String value) {
            for (Cloud c : values()) {
                if (c.key.equalsIgnoreCase(value)) {
                    return c;
                }
            }
            throw new IllegalArgumentException("cloud は global か china を指定してください: " + value);
        }
    }

    /** 認証モード。委任アクセスかアプリケーションアクセスかで、使う資格情報と呼ぶ API が変わる。 */
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

    /**
     * 設定ファイルを読み込み、値を検証する。
     *
     * @param path 設定ファイル (properties 形式) のパス
     * @return 読み込んだ設定
     * @throws IOException ファイルの読み込みに失敗した場合
     * @throws IllegalStateException ファイルが無い、必須項目が無い、または組み合わせが不正な場合
     * @throws IllegalArgumentException cloud / auth.mode の値が不正な場合
     */
    public static AppConfig load(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IllegalStateException(
                    path.toAbsolutePath() + " が見つかりません。config.properties.example をコピーして作成してください。");
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        }

        Cloud cloud = Cloud.of(props.getProperty("cloud", "global").trim());
        AuthMode mode = AuthMode.of(props.getProperty("auth.mode", "device-code").trim());
        String clientId = required(props, "client.id");
        String tenantId = required(props, "tenant.id");
        if (cloud == Cloud.CHINA && tenantId.equalsIgnoreCase("consumers")) {
            throw new IllegalStateException("中国版には個人用 Microsoft アカウントが無いため、tenant.id=consumers は使えません。");
        }
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
        return new AppConfig(cloud, mode, clientId, tenantId, clientSecret, targetUser);
    }

    /**
     * 取得対象のメールボックスを差し替えたコピーを返す (client-secret モードのみ)。
     *
     * @param user 取得対象のメールボックスの UPN またはユーザー ID
     * @return target.user を差し替えた設定
     * @throws IllegalArgumentException client-secret モード以外で呼んだ場合
     */
    public AppConfig withTargetUser(String user) {
        if (authMode != AuthMode.CLIENT_SECRET) {
            throw new IllegalArgumentException("--user は client-secret モードでのみ指定できます。");
        }
        return new AppConfig(cloud, authMode, clientId, tenantId, clientSecret, user);
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
