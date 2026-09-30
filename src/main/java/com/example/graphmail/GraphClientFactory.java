package com.example.graphmail;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.identity.DeviceCodeCredentialBuilder;
import com.microsoft.graph.serviceclient.GraphServiceClient;

/** 認証モードに応じて GraphServiceClient を生成する。 */
public final class GraphClientFactory {

    /** 委任アクセスで要求するスコープ (Graph のアクセス許可名)。 */
    private static final String[] DELEGATED_SCOPES = {"User.Read", "Mail.Read"};

    /** アプリケーションアクセスでは .default を指定し、付与済みのアプリケーション許可をすべて使う。 */
    private static final String[] APP_SCOPES = {"https://graph.microsoft.com/.default"};

    private GraphClientFactory() {
    }

    public static GraphServiceClient create(AppConfig config) {
        return switch (config.authMode()) {
            case DEVICE_CODE -> {
                TokenCredential credential = new DeviceCodeCredentialBuilder()
                        .clientId(config.clientId())
                        .tenantId(config.tenantId())
                        // 表示されたURLをブラウザで開き、コードを入力してサインインする
                        .challengeConsumer(challenge -> System.out.println("\n" + challenge.getMessage() + "\n"))
                        .build();
                yield new GraphServiceClient(credential, DELEGATED_SCOPES);
            }
            case CLIENT_SECRET -> {
                TokenCredential credential = new ClientSecretCredentialBuilder()
                        .clientId(config.clientId())
                        .tenantId(config.tenantId())
                        .clientSecret(config.clientSecret())
                        .build();
                yield new GraphServiceClient(credential, APP_SCOPES);
            }
        };
    }
}
