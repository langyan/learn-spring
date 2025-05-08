# learn-spring
spring



spring -a





spring init --build=maven --java-version=17 --dependencies=web --packaging=jar --a=lin-spring-loki --g=com.lin.spring.loki --com.lin.spring.loki lin-spring-loki;

spring init --build=maven --java-version=17 --dependencies=web,validation --packaging=jar --a=lin-spring-validation --g=com.lin.spring.validation --package-name=com.lin.spring.validation
-n=lin-spring-validation lin-spring-validation;


spring init --build=maven --java-version=17 --dependencies=web,validation --packaging=jar --a=lin-spring-keycloak --g=com.lin.spring.keycloak --package-name=com.lin.spring.keycloak  -n=lin-spring-keycloak lin-spring-keycloak;


spring init --build=maven --java-version=17 --dependencies=web,validation --packaging=jar --a=lin-spring-security-simple-jwt --g=com.lin.spring.security --package-name=com.lin.spring.security  -n=lin-spring-security-simple-jwt lin-spring-security-simple-jwt;


spring init --boot-version=3.4.4 --build=maven --java-version=17 --dependencies=web,validation --packaging=jar --a=lin-spring-micrometer --g=com.lin.spring.micrometer --package-name=com.lin.spring.micrometer  -n=lin-spring-micrometer lin-spring-micrometer;

# docker 启动
- docker-compose up -d  # 重新启动服务

- docker-compose down -v  #停止并删除现有容器
