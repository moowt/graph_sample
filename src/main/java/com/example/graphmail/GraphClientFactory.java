package com.example.graphmail;

import com.azure.core.credential.TokenCredential;
import com.example.graphmail.AppConfig.Cloud;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.identity.DeviceCodeCredentialBuilder;
import com.microsoft.graph.serviceclient.GraphServiceClient;

/** 認証モードに応じて GraphServiceClient を生成する。 */
public final class GraphClientFactory {

    /** 委任アクセスで要求する Graph のアクセス許可。 */
    private static final String[] DELEGATED_PERMISSIONS = {"User.Read", "Mail.Read"};

    private GraphClientFactory() {
    }

    public static GraphServiceClient create(AppConfig config) {
        Cloud cloud = config.cloud();
        GraphServiceClient client = switch (config.authMode()) {
            case DEVICE_CODE -> {
                TokenCredential credential = new DeviceCodeCredentialBuilder()
                        .clientId(config.clientId())
                        .tenantId(config.tenantId())
                        .authorityHost(cloud.authorityHost())
                        // 表示されたURLをブラウザで開き、コードを入力してサインインする。
                        // 標準出力は JSON 専用にするため、標準エラー出力に出す
                        .challengeConsumer(challenge -> System.err.println("\n" + challenge.getMessage() + "\n"))
                        .build();
                yield new GraphServiceClient(credential, delegatedScopes(cloud));
            }
            case CLIENT_SECRET -> {
                TokenCredential credential = new ClientSecretCredentialBuilder()
                        .clientId(config.clientId())
                        .tenantId(config.tenantId())
                        .clientSecret(config.clientSecret())
                        .authorityHost(cloud.authorityHost())
                        .build();
                // .default で、付与済みのアプリケーション許可をすべて使う
                yield new GraphServiceClient(credential, cloud.graphEndpoint() + "/.default");
            }
        };
        // SDK の既定はグローバル版の Graph なので、接続先を差し替える
        client.getRequestAdapter().setBaseUrl(cloud.graphEndpoint() + "/v1.0");
        return client;
    }

    /**
     * 委任アクセスのスコープをクラウドの Graph リソース付きで組み立てる。
     * "Mail.Read" のような短縮形はグローバル版の Graph と解釈されるため、完全な形で指定する。
     */
    private static String[] delegatedScopes(Cloud cloud) {
        String[] scopes = new String[DELEGATED_PERMISSIONS.length];
        for (int i = 0; i < scopes.length; i++) {
            scopes[i] = cloud.graphEndpoint() + "/" + DELEGATED_PERMISSIONS[i];
        }
        return scopes;
    }
}
