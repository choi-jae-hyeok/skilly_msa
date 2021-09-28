# skilly : 커피캡슐판매점

# 서비스 시나리오

- 기능적 요구사항
1. 고객이 커피캡슐을 신청한다.
2. 고객이 결제한다.
3. 결제가 완료되면 주문내역을 보낸다.
4. 주문내역의 커피캡슐 배송을 시작한다.
5. 주문상태를 고객이 조회 할 수 있다.
6. 고객이 주문을 취소 할 수 있다.
7. 결제 취소시 배송이 같이 취소 된다.

- 비기능적 요구사항
1. 트랜젝션
   1. 결제 취소 시 커피캡슐 배송이 진행되지 않는다 → Sync 호출
2. 장애격리
   1. 결제에 장애가 발생해도 주문취소는 가능해야 한다 →Async(event-driven), Eventual Consistency
   2. 결제가 과부화되면 주문은 잠시 후 처리하도록 유도한다 → Circuit breaker, fallback
3. 성능
   1. 고객이 주문상태를 주문내역 조회에서 확인할 수 있어야 한다 → CQRS

# 분석/설계
   
### Event Storming 결과
![Slide2](https://user-images.githubusercontent.com/18024566/134931292-8eecc8e0-bfc9-46ba-b1da-334ee82761ed.jpg)

![Slide3](https://user-images.githubusercontent.com/18024566/134931346-2e65706f-c379-42c7-8fb2-ed91942d7de5.jpg)

![Slide4](https://user-images.githubusercontent.com/18024566/134931358-acc8d67c-cbd2-4b1f-a7c8-b32a515255e1.jpg)

![Slide5](https://user-images.githubusercontent.com/18024566/134931376-63b10706-3d3f-4dc9-ad3f-b75ea919398e.jpg)

![Slide6](https://user-images.githubusercontent.com/18024566/134931392-57e8f7cd-0918-4475-8bfd-3689ec051fa3.jpg)

![Slide7](https://user-images.githubusercontent.com/18024566/134931403-69986902-0596-48cb-8895-519506c7cdca.jpg)

![Slide8](https://user-images.githubusercontent.com/18024566/134931426-576ceee9-3d83-4aaa-9b32-ffc67621f30d.jpg)

![Slide9](https://user-images.githubusercontent.com/18024566/134931444-38f02b48-d95f-4394-95c8-9fdcb33a638c.jpg)

## 헥사고날 아키텍처 다이어그램 도출
![diagram](https://user-images.githubusercontent.com/18024566/134932311-61d45260-2a31-4ff6-a09c-ea217148a452.jpg)

# 구현
- 분석/설계 단계에서 도출된 헥사고날 아키텍처에 따라, 구현한 각 서비스를 로컬에서 실행하는 방법은 아래와 같다 (각각의 포트넘버는 8081 ~ 8084, 8088 이다)

```
cd Order
mvn spring-boot:run  

cd Pay
mvn spring-boot:run

cd Delivery
mvn spring-boot:run 

cd MyPage
mvn spring-boot:run  

cd gateway
mvn spring-boot:run 

```

## GateWay 적용
- API GateWay를 통하여 마이크로 서비스들의 집입점을 통일할 수 있다. 다음과 같이 GateWay를 적용하였다.

```yaml
server:
  port: 8088
---

spring:
  profiles: default
  cloud:
    gateway:
      routes:
        - id: Order
          uri: http://localhost:8081
          predicates:
            - Path=/orders/** 
        - id: Pay
          uri: http://localhost:8082
          predicates:
            - Path=/pays/** 
        - id: Delivery
          uri: http://localhost:8083
          predicates:
            - Path=/deliveries/** 
        - id: MyPage
          uri: http://localhost:8084
          predicates:
            - Path= /myPages/**
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins:
              - "*"
            allowedMethods:
              - "*"
            allowedHeaders:
              - "*"
            allowCredentials: true


---

spring:
  profiles: docker
  cloud:
    gateway:
      routes:
        - id: Order
          uri: http://Order:8080
          predicates:
            - Path=/orders/** 
        - id: Pay
          uri: http://Pay:8080
          predicates:
            - Path=/pays/** 
        - id: Delivery
          uri: http://Delivery:8080
          predicates:
            - Path=/deliveries/** 
        - id: MyPage
          uri: http://MyPage:8080
          predicates:
            - Path= /myPages/**
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins:
              - "*"
            allowedMethods:
              - "*"
            allowedHeaders:
              - "*"
            allowCredentials: true

server:
  port: 8080
```

- gateway service.yaml에 loadBalancer 적용
```yml
apiVersion: v1
kind: Service
metadata:
  name: gateway
  labels:
    app: gateway
spec:
  type: LoadBalancer
  ports:
    - port: 8080
      targetPort: 8080
  selector:
    app: gateway
```

- Gateway External IP

![Gateway External IP](https://user-images.githubusercontent.com/18024566/134941493-3f2886d9-3c45-4968-b0fa-31594b0ff841.PNG)

## DDD 의 적용
- 각 서비스내에 도출된 핵심 Aggregate Root 객체를 Entity 로 선언하였다: (예시는 Order 마이크로 서비스).이때 가능한 현업에서 사용하는 언어 (유비쿼터스 랭귀지)를 그대로 사용하였다.

**Order 마이크로 서비스의 Order.java**
```java
package store;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import store.external.Pay;

@Entity
@Table(name = "Order_table")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    private String customerId;
    private String customerName;
    private String coffeeId;
    private String coffeeName;
    private Integer qty;
    private Double amount;
    private String orderStatus;
    private String address;

    @PostPersist
    public void onPostPersist() {
        System.out.println("################## Order onPostPersist Ordered");
        // configMap 설정 // add by jm
        String cfgServiceType = System.getenv("CFG_SERVICE_TYPE");
        if (cfgServiceType == null)
            cfgServiceType = "DEVELOP";
        System.out.println("################## CFG_SERVICE_TYPE: " + cfgServiceType);

        // kafka에 push
        Ordered ordered = new Ordered();
        BeanUtils.copyProperties(this, ordered);
        ordered.setOrderStatus("completed");
        // ordered.publishAfterCommit(); // modify by jm
        ordered.publish();

        // Following code causes dependency to external APIs
        // it is NOT A GOOD PRACTICE. instead, Event-Policy mapping is recommended.
        // store.external.Pay pay = new store.external.Pay(); // modify by jm
        Pay pay = new Pay();
        BeanUtils.copyProperties(this, pay);

        // feignclient 호출
        OrderApplication.applicationContext.getBean(store.external.PayService.class).pay(pay);
    }

    // add by jm
    @PostUpdate
    public void onPostUpdate() {
        System.out.println("################## Order onPostUpdate OrderCancelled");
        // kafka에 push
        OrderCancelled orderCancelled = new OrderCancelled();
        BeanUtils.copyProperties(this, orderCancelled);
        orderCancelled.setOrderStatus("cancelled");
        orderCancelled.publishAfterCommit();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCoffeeId() {
        return coffeeId;
    }

    public void setCoffeeId(String coffeeId) {
        this.coffeeId = coffeeId;
    }

    public String getCoffeeName() {
        return coffeeName;
    }

    public void setCoffeeName(String coffeeName) {
        this.coffeeName = coffeeName;
    }

    public Integer getQty() {
        return qty;
    }

    public void setQty(Integer qty) {
        this.qty = qty;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

}
```
- Entity Pattern 과 Repository Pattern 을 적용하여 JPA 를 통하여 다양한 데이터소스 유형 (RDB or NoSQL) 에 대한 별도의 처리가 없도록 데이터 접근 어댑터를 자동 생성하기 위하여 Spring Data REST 의 RestRepository 를 적용하였다

**Order 마이크로 서비스의 OrderRepository.java**
```JAVA
package store;

import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(collectionResourceRel = "orders", path = "orders")
public interface OrderRepository extends PagingAndSortingRepository<Order, Long> {

}

```

- DDD 적용 후 REST API의 테스트를 통하여 정상적으로 동작하는 것을 확인할 수 있었다.

**Order서비스 커피캡슐 주문**
```
http POST http://52.231.192.155:8080/orders customerId="cust1" customerName="Jack" coffeeId="001" coffeeName="Yirgacheffe" qty=5 amount=1000 orderStatus="completed" address="seoul"  
```

![Order POST](https://user-images.githubusercontent.com/18024566/134940173-bb3060da-2c72-466a-9efe-a8b2fe965a5a.PNG)


**Order/Pag/Delivery/MyPage서비스 신청정보 조회**
```
http http://52.231.192.155:8080/orders 
http http://52.231.192.155:8080/pays 
http http://52.231.192.155:8080/deliveries 
http http://52.231.192.155:8080/myPages 
```
![Order 조회](https://user-images.githubusercontent.com/18024566/134940287-22b91d9f-ccf1-41e1-8e89-15a7fb2c9ca8.PNG)
![Pay 조회](https://user-images.githubusercontent.com/18024566/134940297-0f9e8c27-491e-4551-a106-5509590fed26.PNG)
![Delivery 조회](https://user-images.githubusercontent.com/18024566/134940305-6792d593-fb85-469c-b4f0-27e52b0be092.PNG)
![MyPage 조회](https://user-images.githubusercontent.com/18024566/134940313-2d0b92ef-94c9-404b-9800-76a7be4d87c7.PNG)


## CQRS/Saga/Correlation-key
- CQRS : Materialized View를 구현하여, 타 마이크로서비스의 데이터 원본에 접근없이(Composite 서비스나 조인SQL 등 없이)도 내 서비스의 화면 구성과 잦은 조회가 가능하게 구현해 두었다. 본 프로젝트에서 View 역할은 MyPages 서비스가 수행한다.

주문/ 결제 / 배송 서비스의 전체 현황 및 상태 조회를 제공하기 위해 MyPage를 구성하였다.

**Order 서비스를 통해 커피캡슐 주문**

![Order POST](https://user-images.githubusercontent.com/18024566/134940692-dd5851bd-5ea4-4bf8-a8a9-71ce28a1f762.PNG)

**Order 실행 후 MyPages**

![MyPage 조회](https://user-images.githubusercontent.com/18024566/134940745-7b8ba72d-9087-4e88-8a6f-236eef459a27.PNG)

- Correlation-key 

Correlation Key를 활용하기 위해 Id를 Key값으로 사용하였으며 신청된 교재를 동일한 Id로 취소한다.

신청 취소가 되면 OrderStatus가 cancelled로 Update 되는 것을 볼 수 있다.

**Order서비스 주문 취소**
```
http PUT http://52.231.192.155:8080/orders/1 customerId="cust1" customerName="Jack" coffeeId="001" coffeeName="Yirgacheffe" qty=5 amount=1000 orderStatus="cancelled" address="seoul" 
```

![Order 취소](https://user-images.githubusercontent.com/18024566/134940891-a8edf6f8-8d51-47f5-a1af-0cd91bc4d94d.PNG)

위와 같이 하게되면 Order > Pay > Delivery > MyPage 순서로 신청이 처리된다.

![Order 취소](https://user-images.githubusercontent.com/18024566/134940989-04de44f6-06a1-4989-9971-c943c1c5e631.PNG)
![Pay 취소](https://user-images.githubusercontent.com/18024566/134940968-7fe56083-db04-45c9-9e94-a4a59f28f465.PNG)
![Delivery 취소](https://user-images.githubusercontent.com/18024566/134940951-ec43d05c-12c7-40b6-8840-63bec278da73.PNG)
![MyPage 취소](https://user-images.githubusercontent.com/18024566/134940944-e8ca27ab-f619-41ae-9851-4fcf10ca5fb5.PNG)

위 결과로 서로 다른 마이크로 서비스 간에 ID값으로 상호 연결되어 있음을 알 수 있다.

## 폴리글랏 퍼시스턴스
- Order 서비스의 DB와 MyPage의 DB를 다른 DB를 사용하여 폴리글랏 퍼시스턴스를 만족시키고 있다.(인메모리 DB인 hsqldb 사용)

**Order의 pom.xml DB 설정 코드**

![Order DB](https://user-images.githubusercontent.com/18024566/134941165-a49450e1-330f-477f-b6b9-7318061a3887.PNG)

**MyPage의 pom.xml DB 설정 코드**

![MyPage DB](https://user-images.githubusercontent.com/18024566/134941252-390ee331-f625-44de-8669-a923564526cf.PNG)

## 동기식 호출 과 Fallback 처리

- 분석단계에서의 조건 중 하나로 결제 서비스(Pay)와 배송 서비스(Delivery) 간의 호출은 동기식 일관성을 유지하는 트랜잭션으로 처리하기로 하였다. 호출 프로토콜은 Rest Repository에 의해 노출되어있는 REST 서비스를 FeignClient를 이용하여 호출하도록 한다.

pay서비스의 external.DeliveryService.jaeva 내에 delivery서비스를 호출하기 위하여 FeignClient를 이용하여 Service 대행 인터페이스(Proxy) 를 구현

**pay/external/DeliveryService.java**
```java
package store.external;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@FeignClient(name = "Delivery", url = "${api.url.delivery}")
public interface DeliveryService {
    // command
    @RequestMapping(method = RequestMethod.POST, path = "/deliveries", consumes = "application/json")
    public void deliveryCancel(@RequestBody Delivery delivery);

}

```

### 동작 확인

**잠시 배송 서비스(Delivery) 중지**

![동기식호출 일시 중단](https://user-images.githubusercontent.com/18024566/134949299-c9d42f4a-d716-4e90-8840-3ac2541fc73b.PNG)


**결제 취소 요청시 에러 발생**

![결제취소시 에러 발생](https://user-images.githubusercontent.com/18024566/134949481-0878e5e0-90bb-48a2-991c-0dab45accdf1.PNG)


**배송 서비스 재기동 후 정상 처리**

![재기동 후 정상 처리](https://user-images.githubusercontent.com/18024566/134949614-0ec231c5-bb18-4318-b2ea-00581d6ee374.PNG)


**Fallback 설정**
```java
package store.external;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@FeignClient(name = "Pay", url = "${api.url.pay}", fallback = PayServiceImpl.class)
public interface PayService {
    @RequestMapping(method = RequestMethod.POST, path = "/pays", consumes = "application/json")
    public void pay(@RequestBody Pay pay);
}

```
```java
package store.external;

import org.springframework.stereotype.Service;

@Service
public class PayServiceImpl implements PayService {
    @Override
    public void pay(Pay pay) {
        System.out.println("@@@@@@@@@@@@@@@@@@@@@ Pay service is BUSY @@@@@@@@@@@@@@@@@@@@@");
        System.out.println("@@@@@@@@@@@@@@@@@@@@@   Try again later   @@@@@@@@@@@@@@@@@@@@@");
    }
}

```

**Fallback 결과**

![Fallback처리](https://user-images.githubusercontent.com/18024566/134949811-6451e436-4448-4364-a3f5-c24a8791222a.PNG)

## 비동기식 호출
- Order 서비스 내 Order.jav에서 아래와 같이 서비스 Pub 구현
``` JAVA
  ...
  @PostUpdate
    public void onPostUpdate() {
        System.out.println("################## Order onPostUpdate OrderCancelled");
        // kafka에 push
        OrderCancelled orderCancelled = new OrderCancelled();
        BeanUtils.copyProperties(this, orderCancelled);
        orderCancelled.setOrderStatus("cancelled");
        orderCancelled.publishAfterCommit();
    }
```

- Pay 서비스 내 PolicyHandler.java에서 아래와 같이 Sub 구현
``` JAVA
public class PolicyHandler {
    @Autowired
    PayRepository payRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverOrderCancelled_PayCancel(@Payload OrderCancelled orderCancelled) {

        if (!orderCancelled.validate())
            return;

        System.out.println("\n\n##### listener PayCancel : " + orderCancelled.toJson() + "\n\n");

        // 객체 조회
        Optional<Pay> Optional = payRepository.findById(orderCancelled.getId());

        if (Optional.isPresent()) {
            System.out.println("\n\n##### listener PayCancel Optional ID : " + orderCancelled.getId() + "/CoffeeName"
                    + orderCancelled.getCoffeeName() + "\n\n");
            Pay pay = Optional.get();

            // 객체에 이벤트의 eventDirectValue 를 set 함
            pay.setId(orderCancelled.getId());
            pay.setCustomerId(orderCancelled.getCustomerId());
            pay.setCustomerName(orderCancelled.getCustomerName());
            pay.setCoffeeId(orderCancelled.getCoffeeId());
            pay.setCoffeeName(orderCancelled.getCoffeeName());
            pay.setQty(orderCancelled.getQty());
            pay.setOrderStatus("cancelled");
            pay.setAddress(orderCancelled.getAddress());

            // 레파지 토리에 save
            payRepository.save(pay);
        }

    } 
```
- 비동기식 호출은 다른 서비스가 비정상이여도 이상없이 동작가능하여, Pay 서비스에 장애가 발새하여도 rder 서비스는 정상 동작을 확인

**Pay 서비스 내림**

![비동기식 - pay 내림](https://user-images.githubusercontent.com/18024566/134950688-2be395ff-eac2-49b0-8799-6bbf81df5a70.PNG)

**주문취소 정상 확인**

![비동기식 - order 취소 정상](https://user-images.githubusercontent.com/18024566/134950731-c5b7da76-0a98-4fce-a3fa-64009952fc77.PNG)

# 운영

## CI/CD
* 카프카 설치(Windows)
```
A. chocolatey 설치
-	cmd.exe를 관리자 권한으로 실행합니다.
-	다음 명령줄을 실행합니다.
@"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -InputFormat None -ExecutionPolicy Bypass -Command " [System.Net.ServicePointManager]::SecurityProtocol = 3072; iex ((New-Object System.Net.WebClient).DownloadString('https://chocolatey.org/install.ps1'))" && SET "PATH=%PATH%;%ALLUSERSPROFILE%\chocolatey\bin"

B. Helm 설치
cmd.exe에서 아래 명령어 실행 .
choco install kubernetes-helm

C. Helm 에게 권한을 부여하고 초기화
kubectl --namespace kube-system create sa tiller
kubectl create clusterrolebinding tiller --clusterrole cluster-admin --serviceaccount=kube-system:tiller

D. Kafka 설치 및 실행
helm repo add incubator https://charts.helm.sh/incubator 
helm repo update 
kubectl create ns kafka 
helm install my-kafka --namespace kafka incubator/kafka 
kubectl get po -n kafka -o wide

E. Kafka 실행 여부
kubectl -n kafka exec -it my-kafka-0 -- /bin/sh
ps –ef  | grep kafka

```
* Topic 생성
```
kubectl -n kafka exec my-kafka-0 -- /usr/bin/kafka-topics --zookeeper my-kafka-zookeeper:2181 --topic store --create --partitions 1 --replication-factor 1
```
* Topic 확인
```
kubectl -n kafka exec my-kafka-0 -- /usr/bin/kafka-topics --zookeeper my-kafka-zookeeper:2181 --list
```
* 이벤트 발행하기
```
kubectl -n kafka exec -ti my-kafka-0 -- /usr/bin/kafka-console-producer --broker-list my-kafka:9092 --topic store
```
* 이벤트 수신하기
```
kubectl -n kafka exec -ti my-kafka-0 -- /usr/bin/kafka-console-consumer --bootstrap-server my-kafka:9092 --topic store --from-beginning
```

## ConfigMap
* Order 서비스 deployment.yml 파일에 설정
```
env:
   - name: CFG_SERVICE_TYPE
     valueFrom:
       configMapKeyRef:
         name: servicetype
         key: svctype
```

* Configmap 생성, 정보 확인(servicetype - 운영환경 : PRODUCT / 개발환경 : DEVELOP)
```
kubectl create configmap servicetype --from-literal=svctype=PRODUCT -n default -n default
kubectl get configmap servicetype -o yaml
```
![캡처](https://user-images.githubusercontent.com/18024566/135023085-50278ca8-be17-4b2f-99ec-76f8e834fb6c.PNG)


**Order 마이크로 서비스의 Order.java**
```java 
@Entity
@Table(name="Apply_table")
public class Apply {
    ...
    @PostPersist
    public void onPostPersist() {
        System.out.println("################## Order onPostPersist Ordered");
        // configMap 설정 // add by jm
        String cfgServiceType = System.getenv("CFG_SERVICE_TYPE");
        if (cfgServiceType == null)
            cfgServiceType = "DEVELOP";
        System.out.println("################## CFG_SERVICE_TYPE: " + cfgServiceType);
    }
    ...
}
```

* Order 데이터 1건 추가 후 로그 확인
```
kubectl logs -f order-5d49c95c8c-zd4zm
```

![product](https://user-images.githubusercontent.com/18024566/135023361-8adf5f2d-b441-456c-a7eb-068fbc6a51f8.PNG)

## Deploy / Pipeline

* build 하기
```
cd Order
mvn package 

cd ..
cd Pay
mvn package

cd ..
cd Delivery
mvn package

cd ..
cd MyPage
mvn package

cd ..
cd gateway
mvn package
```

* Azure 레지스트리에 도커 이미지 push, deploy, 서비스생성(yaml파일 이용한 deploy)
```
cd .. 
cd Order
az acr build --registry skillyacr --image skillyacr.azurecr.io/order:v1 . 
kubectl apply -f kubernetes/deployment.yml 
kubectl apply -f kubernetes/service.yaml 

cd .. 
cd Pay
az acr build --registry skillyacr --image skillyacr.azurecr.io/pay:v1 . 
kubectl apply -f kubernetes/deployment.yml 
kubectl apply -f kubernetes/service.yaml 

cd .. 
cd Delivery
az acr build --registry skillyacr --image skillyacr.azurecr.io/delivery:v1 . 
kubectl apply -f kubernetes/deployment.yml 
kubectl apply -f kubernetes/service.yaml 

cd .. 
cd MyPage
az acr build --registry skillyacr --image skillyacr.azurecr.io/mypage:v1 . 
kubectl apply -f kubernetes/deployment.yml 
kubectl apply -f kubernetes/service.yaml 

cd .. 
cd gateway
az acr build --registry skillyacr --image skillyacr.azurecr.io/gateway:v4 . 
kubectl apply -f kubernetes/deployment.yml 
kubectl apply -f kubernetes/service.yaml 
```

* Service, Pod, Deploy 상태 확인

![배포완료](https://user-images.githubusercontent.com/18024566/135023547-ee8d230a-aca3-4750-b1f1-1d7237c69697.PNG)

* deployment.yml  참고
```
1. image 설정
2. env 설정 (config Map) 
3. readiness 설정 (무정지 배포)
4. liveness 설정 (self-healing)
5. resource 설정 (autoscaling)
```

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order
  labels:
    app: order
spec:
  replicas: 1
  selector:
    matchLabels:
      app: order
  template:
    metadata:
      labels:
        app: order
    spec:
      containers:
        - name: order
          image: skillyacr.azurecr.io/order:v8
          ports:
            - containerPort: 8080
          # autoscale start
          resources:
              limits:
               cpu: 500m
              requests:
                cpu: 200m
           autoscale end
          ### config map start
          env:
            - name: CFG_SERVICE_TYPE
              valueFrom:
                configMapKeyRef:
                  name: servicetype
                  key: svctype
          ### config map end         
          readinessProbe:
            httpGet:
              path: '/actuator/health'
              port: 8080
            initialDelaySeconds: 10
            timeoutSeconds: 2
            periodSeconds: 5
            failureThreshold: 10
          #livenessProbe:
            httpGet:
              path: '/actuator/health'
              port: 8080
            initialDelaySeconds: 120
            timeoutSeconds: 2
            periodSeconds: 5
            failureThreshold: 5
```

## Circuit Breaker
* 서킷 브레이킹 프레임워크의 선택: Spring FeignClient + Hystrix 옵션을 사용하여 구현함
* Order -> Pay 와의 Req/Res 연결에서 요청이 과도한 경우 CirCuit Breaker 통한 격리
* Hystrix 를 설정: 요청처리 쓰레드에서 처리시간이 610 밀리가 넘어서기 시작하여 어느정도 유지되면 CB 회로가 닫히도록 (요청을 빠르게 실패처리, 차단) 설정

```yaml
# Order서비스 application.yml

feign:
  hystrix:
    enabled: true

hystrix:
  command:
    default:
      execution.isolation.thread.timeoutInMilliseconds: 610
```

* 피호출 서비스(결제:pay) 의 부하 처리
```java
// Pay 서비스 Pay.java

 @PostPersist
    public void onPostPersist(){
        PayCompleted payCompleted = new PayCompleted();
        BeanUtils.copyProperties(this, payCompleted);
        payCompleted.setApplyStatus("completed");
        payCompleted.publishAfterCommit();

        try {
                 Thread.currentThread().sleep((long) (400 + Math.random() * 220));
         } catch (InterruptedException e) {
                 e.printStackTrace();
         }
```
* siege\kubernetes\deployment.yml
```yaml
apiVersion: v1
kind: Pod
metadata:
  name: siege
spec:
  containers:
  - name: siege
    image: apexacme/siege-nginx
```

* siege pod 생성
```
cd siege\kubernetes
kubectl apply -f deployment.yml
```

* 부하테스터 siege 툴을 통한 서킷 브레이커 동작 확인: 동시사용자 50명 60초 동안 실시
```
kubectl exec -it pod/siege -c siege -- /bin/bash
siege –c50 –t60S  -v --content-type "application/json" 'http://40.89.192.251:8080/orders POST {"customerId":"cust1", "coffeeId":"001", "qty": "10", "amount":"1000"}' 
```
![서킷브레이커(1)](https://user-images.githubusercontent.com/18024566/135023952-bceb07be-92e9-4721-ba6f-bb6c84235489.PNG)

![서킷브레이커(2)](https://user-images.githubusercontent.com/18024566/135023972-3a3ec58a-137e-4b74-b142-e844c00041ff.PNG)

## 오토스케일 아웃
* 앞서 서킷 브레이커(CB) 는 시스템을 안정되게 운영할 수 있게 해줬지만 사용자의 요청을 100% 받아들여주지 못했기 때문에 이에 대한 보완책으로 자동화된 확장 기능을 적용하고자 한다.

* Order 서비스 deployment.yml 설정
```yaml
          resources:
              limits:
                cpu: 500m
              requests:
                cpu: 200m
```
* 다시 배포해준다.
```
cd Order
mvn package
az acr build --registry skillyacr --image skillyacr.azurecr.io/order:v8 . 
kubectl apply -f kubernetes/deployment.yml

```

* Order 서비스에 대한 replica 를 동적으로 늘려주도록 HPA 를 설정한다. 설정은 CPU 사용량이 50프로를 넘어서면 replica 를 2개까지 늘려준다

```
kubectl autoscale deploy Order --min=1 --max=2 --cpu-percent=50
```
* siege pod 생성
```
cd siege\kubernetes
kubectl apply -f deployment.yml
```

* 부하테스터 siege 툴을 통한 서킷 브레이커 동작 확인: 동시사용자 50명 60초 동안 실시
```
kubectl exec -it pod/siege -c siege -- /bin/bash
siege –c50 –t60S  -v --content-type "application/json" 'http://40.89.192.251:8080/orders POST {"customerId":"cust1", "coffeeId":"001", "qty": "10", "amount":"1000"}' 
```

* 오토스케일이 어떻게 되고 있는지 모니터링을 걸어둔다
```
kubectl get deploy order -w
```
![오토스케일](https://user-images.githubusercontent.com/18024566/135024343-1a4e28c8-779b-49ea-9ad9-cb5ec2e5c074.PNG)


## 무정지 재배포(Readiness Probe)

* 먼저 무정지 재배포가 100% 되는 것인지 확인하기 위해서 Autoscaler 이나 CB 설정을 제거함

- seige 로 배포작업 직전에 워크로드를 모니터링 함.
```
kubectl exec -it pod/siege -c siege -- /bin/bash
siege –c50 –t60S  -v --content-type "application/json" 'http://40.89.192.251:8080/orders POST {"customerId":"cust1", "coffeeId":"001", "qty": "10", "amount":"1000"}' 
```

- 새버전으로의 배포 시작
```yaml
cd Order
mvn package
az acr build --registry skillyacr --image skillyacr.azurecr.io/order:v8 . 
kubectl apply -f kubernetes/deployment.yml
```

- seige 의 화면으로 넘어가서 Availability 가 100% 미만으로 떨어졌는지 확인

![무정지배포(100%미만)](https://user-images.githubusercontent.com/18024566/135024483-8bdc1d45-f383-465e-a931-374f8282b961.PNG)

배포기간중 Availability 가 평소 100%에서 90% 대로 떨어지는 것을 확인. 원인은 쿠버네티스가 성급하게 새로 올려진 서비스를 READY 상태로 인식하여 서비스 유입을 진행한 것이기 때문. 이를 막기위해 Readiness Probe 를 설정함:

* Order 서비스 deployment.yml 파일에 Readiness Probe 부분 설정

```yaml
    readinessProbe:
      httpGet:
        path: '/actuator/health'
        port: 8080
      initialDelaySeconds: 10
      timeoutSeconds: 2
      periodSeconds: 5
      failureThreshold: 10
```

* 디플로이 시작
```yaml
cd Order
mvn package
az acr build --registry skillyacr --image skillyacr.azurecr.io/order:v9 . 
kubectl apply -f kubernetes/deployment.yml
```

- 동일한 시나리오로 재배포 한 후 Availability 확인:

![무정지배포(정상(](https://user-images.githubusercontent.com/18024566/135024550-133e213f-ebc1-43c0-a552-d7eaa3bfa9a9.PNG)

배포기간 동안 Availability 가 변화없기 때문에 무정지 재배포가 성공한 것으로 확인됨.



## Self-healing (Liveness Probe)
* Delivery 서비스 deployment.yml   livenessProbe 설정을 port 8089로 변경 후 배포 하여 liveness probe 가 동작함을 확인 
```
    livenessProbe:
      httpGet:
        path: '/actuator/health'
        port: 8089
      initialDelaySeconds: 5
      periodSeconds: 5
```

- Delivery 서비스 프토 변경 확인
```
kubectl describe deploy delivery
```
![셀프힐링](https://user-images.githubusercontent.com/18024566/135024648-12c2cd9c-76c8-422b-9dea-3d6976bd7fee.PNG)

- Pod 재시작 확인
```
kubectl get pod -w
```
![image](https://github.com/jinmojeon/elearningStudentApply/blob/main/Images/10-2-liveness-pod.png)
