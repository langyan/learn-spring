# learn-spring
spring



spring -a





spring init --build=maven --java-version=17 --dependencies=web --packaging=jar --a=lin-spring-loki --g=com.lin.spring.loki --com.lin.spring.loki lin-spring-loki;

spring init --build=maven --java-version=17 --dependencies=web,validation --packaging=jar --a=lin-spring-validation --g=com.lin.spring.validation --package-name=com.lin.spring.validation
-n=lin-spring-validation lin-spring-validation;


spring init --build=maven --java-version=17 --dependencies=web,validation --packaging=jar --a=lin-spring-keycloak --g=com.lin.spring.keycloak --package-name=com.lin.spring.keycloak  -n=lin-spring-keycloak lin-spring-keycloak;


spring init --build=maven --java-version=17 --dependencies=web,validation --packaging=jar --a=lin-spring-security-simple-jwt --g=com.lin.spring.security --package-name=com.lin.spring.security  -n=lin-spring-security-simple-jwt lin-spring-security-simple-jwt;


spring init --boot-version=3.4.4 --build=maven --java-version=17 --dependencies=web,validation --packaging=jar --a=lin-spring-micrometer --g=com.lin.spring.micrometer --package-name=com.lin.spring.micrometer  -n=lin-spring-micrometer lin-spring-micrometer;


spring init --boot-version=3.4.4 --build=maven --java-version=17 --dependencies=webflux,lombok,web,validation,devtools --packaging=jar --a=lin-spring-flux --g=com.lin.spring.flux --package-name=com.lin.spring.flux  -n=lin-spring-flux lin-spring-flux

spring init --boot-version=3.4.4 --build=maven --java-version=17 --dependencies=jpa,lombok,web,validation,devtools --packaging=jar --a=lin-spring-jpa --g=com.lin.spring.jpa --package-name=com.lin.spring.jpa  -n=lin-spring-jpa lin-spring-jpa

spring init --boot-version=3.4.4 --build=maven --java-version=17 --dependencies=jpa,lombok,web,validation,devtools --packaging=jar --a=lin-spring-security-dao-auth --g=com.lin.spring.security --package-name=com.lin.spring.security -n=lin-spring-security-dao-auth lin-spring-security-dao-auth


spring init --boot-version=3.4.4 --build=maven --java-version=17 --dependencies=jpa,lombok,web,validation,devtools,h2 --packaging=jar --a=lin-spring-jpa-specification --g=com.lin.spring.jpa --package-name=com.lin.spring.jpa -n=lin-spring-jpa-specification lin-spring-jpa-specification

spring init --boot-version=3.5.0 --build=maven --java-version=17 --dependencies=webflux,lombok,web,validation,devtools  --packaging=jar --a=lin-spring-mongodb --g=com.lin.spring.mongodb --package-name=com.lin.spring.mongodb -n=lin-spring-mongodb lin-spring-mongodb


spring init --boot-version=3.5.0 --build=maven --java-version=17 --dependencies=jpa,lombok,web,validation,devtools,security,mysql  --packaging=jar --a=lin-spring-otp --g=com.lin.spring.otp --package-name=com.lin.spring.otp -n=lin-spring-otp lin-spring-otp


spring init --boot-version=3.5.0 --build=maven --java-version=17 --dependencies=jpa,lombok,web,validation,devtools,security,h2  --packaging=jar --a=lin-spring-tenant --g=com.lin.spring.tenant --package-name=com.lin.spring.tenant -n=lin-spring-tenant lin-spring-tenant

spring init --boot-version=3.5.0 --build=maven --java-version=17 --dependencies=jpa,lombok,web,validation,devtools,security,h2  --packaging=jar --a=lin-spring-docker --g=com.lin.spring.docker --package-name=com.lin.spring.docker -n=lin-spring-docker-images lin-spring-docker-images

spring init --boot-version=3.5.0 --build=maven --java-version=21 --dependencies=lombok,web,validation,devtools,h2  --packaging=jar --a=lin-spring-jackson-deserialization --g=com.lin.spring.jackson --package-name=com.lin.spring.jackson -n=lin-spring-jackson-deserialization lin-spring-jackson-deserialization

spring init --boot-version=3.5.0 --build=maven --java-version=21 --dependencies=lombok,web,validation,devtools,h2  --packaging=jar --a=lin-spring-file-upload --g=com.lin.spring.file --package-name=com.lin.spring.file -n=lin-spring-file-upload lin-spring-file-upload


spring init --boot-version=3.5.0 --build=maven --java-version=21 --dependencies=lombok,web,validation,devtools,h2,websocket,redis,thymeleaf  --packaging=jar --a=lin-spring-websocket --g=com.lin.spring.websocket --package-name=com.lin.spring.websocket -n=lin-spring-websocket lin-spring-websocket



# docker 启动
- docker-compose up -d  # 重新启动服务

- docker-compose down -v  #停止并删除现有容器
