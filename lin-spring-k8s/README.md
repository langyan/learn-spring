
## mvn clean package
`
E:\source_files\learn-spring\lin-spring-k8s>mvn clean package
`

## docker build -t lin-spring-k8s-app .

E:\source_files\learn-spring\lin-spring-k8s>docker build -t lin-spring-k8s-app .
[+] Building 1.4s (7/7) FINISHED   

View build details: docker-desktop://dashboard/build/desktop-linux/desktop-linux/vmjcvcyw4xm98p0r0tqjiqty2

## kubectl apply -f k8s-deployment.yaml

E:\source_files\learn-spring\lin-spring-k8s\k8s>kubectl apply -f k8s-deployment.yaml
deployment.apps/lin-spring-k8s-app created
service/lin-spring-k8s-service created

E:\source_files\learn-spring\lin-spring-k8s\k8s>kubectl get deployments
NAME                  READY   UP-TO-DATE   AVAILABLE   AGE
lin-spring-k8s-app    1/1     1            1           14s
spring-boot-k8s-app   1/1     1            1           137m

E:\source_files\learn-spring\lin-spring-k8s\k8s>kubectl get services
NAME                     TYPE           CLUSTER-IP       EXTERNAL-IP   PORT(S)          AGE
kubernetes               ClusterIP      10.96.0.1        <none>        443/TCP          4h31m
lin-spring-k8s-service   LoadBalancer   10.104.134.245   localhost     8080:30788/TCP   81m

E:\source_files\learn-spring\lin-spring-k8s\k8s>kubectl delete service lin-spring-k8s-service
service "lin-spring-k8s-service" deleted

E:\source_files\learn-spring\lin-spring-k8s\k8s>kubectl get deployments
NAME                 READY   UP-TO-DATE   AVAILABLE   AGE
lin-spring-k8s-app   1/1     1            1           82m

E:\source_files\learn-spring\lin-spring-k8s\k8s>kubectl delete deployment lin-spring-k8s-app 
deployment.apps "lin-spring-k8s-app" deleted

E:\source_files\learn-spring\lin-spring-k8s\k8s>kubectl get deployments
NAME                 READY   UP-TO-DATE   AVAILABLE   AGE
lin-spring-k8s-app   1/1     1            1           2m31s



  ## 本地开发
  ### 查看本地镜像  docker images | grep my-spring-boot-k8s-app
  ### 修改k8s-deployment.yaml文件
  spec:
  containers:
  - name: spring-boot-k8s-app
    image: my-spring-boot-k8s-app:latest
    imagePullPolicy: Never  # 强制使用本地镜像，不拉取