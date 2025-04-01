
## mvn clean package
`
E:\source_files\spring-boot-k8s-demo>mvn clean package
`

## docker build -t my-spring-boot-k8s-app .

E:\source_files\spring-boot-k8s-demo>docker build -t my-spring-boot-k8s-app .

## kubectl apply -f k8s-deployment.yaml

E:\source_files\spring-boot-k8s-demo\Kubernetes>kubectl apply -f k8s-deployment.yaml

E:\source_files\spring-boot-k8s-demo\Kubernetes>kubectl get deployments

E:\source_files\spring-boot-k8s-demo\Kubernetes>kubectl get services

E:\source_files\spring-boot-k8s-demo\Kubernetes>kubectl get pods      
NAME                                   READY   STATUS             RESTARTS   AGE
spring-boot-k8s-app-74f5575f6f-2z82g   0/1     ImagePullBackOff   0          84m

E:\source_files\spring-boot-k8s-demo\Kubernetes>kubectl describe pod spring-boot-k8s-app-74f5575f6f-2z82g  
Name:             spring-boot-k8s-app-74f5575f6f-2z82g
Namespace:        default
Priority:         0
Service Account:  default
Node:             docker-desktop/192.168.65.3
Start Time:       Tue, 01 Apr 2025 12:09:30 +0800
Labels:           app=spring-boot-k8s
                  pod-template-hash=74f5575f6f
Annotations:      <none>
Status:           Pending
IP:               10.1.0.8
IPs:
  IP:           10.1.0.8
Controlled By:  ReplicaSet/spring-boot-k8s-app-74f5575f6f
Containers:
  spring-boot-k8s-app:
    Container ID:
    Image:          my-spring-boot-k8s-app:latest
    Image ID:
    Port:           8080/TCP
    Host Port:      0/TCP
    State:          Waiting
      Reason:       ImagePullBackOff
    Ready:          False
    Restart Count:  0
    Environment:    <none>
    Mounts:
      /var/run/secrets/kubernetes.io/serviceaccount from kube-api-access-ml46t (ro)
Conditions:
  Type                        Status
  PodReadyToStartContainers   True
  Initialized                 True
  Ready                       False
  ContainersReady             False
  PodScheduled                True
Volumes:
  kube-api-access-ml46t:
    Type:                    Projected (a volume that contains injected data from multiple sources)
    TokenExpirationSeconds:  3607
    ConfigMapName:           kube-root-ca.crt
    ConfigMapOptional:       <nil>
    DownwardAPI:             true
QoS Class:                   BestEffort
Node-Selectors:              <none>
Tolerations:                 node.kubernetes.io/not-ready:NoExecute op=Exists for 300s
                             node.kubernetes.io/unreachable:NoExecute op=Exists for 300s
Events:
  Type    Reason   Age                    From     Message
  ----    ------   ----                   ----     -------
  Normal  BackOff  2m18s (x281 over 67m)  kubelet  Back-off pulling image "my-spring-boot-k8s-app:latest"


  ## 本地开发
  ### 查看本地镜像  docker images | grep my-spring-boot-k8s-app
  ### 修改k8s-deployment.yaml文件
  spec:
  containers:
  - name: spring-boot-k8s-app
    image: my-spring-boot-k8s-app:latest
    imagePullPolicy: Never  # 强制使用本地镜像，不拉取