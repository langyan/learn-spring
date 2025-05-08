

# 1.设置Keycloak服务器
- 下载并运行 Keycloak
从官方网站下载Keycloak。
提取档案并导航至该bin文件夹。
启动Keycloak服务器：
bin/standalone.sh
Keycloak 现在将在 上运行http://localhost:8080/auth。
-  创建一个领域
转到 Keycloak 管理控制台http://localhost:8080/auth并登录（admin如果这是您第一次登录，请使用默认用户名和密码）。
单击Master（默认领域），然后单击Add Realm。
为新领域命名（例如spring-boot-realm），然后单击“创建”。
领域隔离安全配置并为用户、角色和客户端提供命名空间。
-  为 Spring Boot 创建客户端
在 Keycloak 管理控制台中，导航到“客户端”并单击“创建”。
设置客户端 ID（例如spring-boot-client）。
将客户端协议设置为OpenID Connect。
将Root URL设置为http://localhost:8081/（这将是您的 Spring Boot 应用程序的 URL）。
将有效重定向 URI设置为http://localhost:8081/*允许重定向回您的应用程序。
点击保存。此客户端将允许 Keycloak 与你的 Spring Boot 应用程序进行通信。
- 创建角色（可选）
导航到您的领域设置下的角色。
admin创建类似和的角色user，并将其分配给用户，以实现细粒度的访问控制。