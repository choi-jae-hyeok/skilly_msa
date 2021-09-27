# skilly : 커피캡슐판매점

# 서비스 시나리오

기능적 요구사항
1. 고객이 커피캡슐를 신청한다.
2. 고객이 결제한다.
3. 결제가 완료되면 주문내역을 보낸다.
4. 주문내역의 커피캡슐 배송을 시작한다.
5. 주문상태를 고객이 조회 할 수 있다.
6. 고객이 주문을 취소 할 수 있다.
7. 결제 취소시 배송이 같이 취소 된다.

비기능적 요구사항
1. 트랜젝션
   1. 결제 취소 시 커피캡슐 배송이 진행되지 않는다 → Sync 호출
2. 장애격리
   1. 배송에서 장애가 발생해도 결제와 주문은 가능해야 한다 →Async(event-driven), Eventual Consistency
   1. 결제가 과부화되면 결제를 잠시 후 처리하도록 유도한다 → Circuit breaker, fallback
3. 성능
   1. 고객이 주문상태를 주문내역 조회에서 확인할 수 있어야 한다 → CQRS

# 분석/설계
   
## Event Storming 결과

![EventStormingV1](https://github.com/choi-jae-hyeok/skilly_msa/blob/main/Image/Slide2.JPG)

![EventStormingV2](https://github.com/choi-jae-hyeok/skilly_msa/blob/main/Image/Slide3.JPG)

![EventStormingV3](https://github.com/choi-jae-hyeok/skilly_msa/blob/main/Image/Slide4.JPG)

![EventStormingV4](https://github.com/choi-jae-hyeok/skilly_msa/blob/main/Image/Slide5.JPG)

![EventStormingV5](https://github.com/choi-jae-hyeok/skilly_msa/blob/main/Image/Slide6.JPG)

![EventStormingV6](https://github.com/choi-jae-hyeok/skilly_msa/blob/main/Image/Slide7.JPG)

![EventStormingV7](https://github.com/choi-jae-hyeok/skilly_msa/blob/main/Image/Slide8.JPG)

![EventStormingV8](https://github.com/choi-jae-hyeok/skilly_msa/blob/main/Image/Slide9.JPG)

## 헥사고날 아키텍처 다이어그램 도출
![증빙1](https://github.com/jinmojeon/elearningStudentApply/blob/main/Images/1-hex_diagram.png)

# 구현
- 분석/설계 단계에서 도출된 헥사고날 아키텍처에 따라, 구현한 각 서비스를 로컬에서 실행하는 방법은 아래와 같다 (각각의 포트넘버는 8081 ~ 8084, 8088 이다)
```
cd Apply
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
        - id: Apply
          uri: http://localhost:8081
          predicates:
            - Path=/applies/** 
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
        - id: Apply
          uri: http://Apply:8080
          predicates:
            - Path=/applies/** 
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

**Gateway External IP**

![증빙1](https://github.com/jinmojeon/elearningStudentApply/blob/main/Images/3-gateway.png)


## DDD 의 적용
- 각 서비스내에 도출된 핵심 Aggregate Root 객체를 Entity 로 선언하였다: (예시는 Apply 마이크로 서비스).이때 가능한 현업에서 사용하는 언어 (유비쿼터스 랭귀지)를 그대로 사용하였다.

**Apply 마이크로 서비스의 Apply.java**
```java 
package store;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import store.external.Pay;

@Entity
@Table(name="Apply_table")
public class Apply {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private String studentId;
    private String studentName;
    private String bookId;
    private String bookName;
    private Integer qty;
    private Double amount;
    private String applyStatus;
    private String address;

    @PostPersist
    public void onPostPersist(){
        Applied applied = new Applied();
        BeanUtils.copyProperties(this, applied);
        applied.setApplyStatus("completed");
        applied.publish(); 
        
        Pay pay = new Pay();
        BeanUtils.copyProperties(this, pay);
        ApplyApplication.applicationContext.getBean(store.external.PayService.class).pay(pay);
    }
    
    @PostUpdate
    public void onPostUpdate(){
        ApplyCancelled applyCancelled = new ApplyCancelled();
        BeanUtils.copyProperties(this, applyCancelled);
        applyCancelled.setApplyStatus("cancelled");
        applyCancelled.publishAfterCommit();
    }
    
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }
    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }
    public String getBookId() {
        return bookId;
    }

    public void setBookId(String bookId) {
        this.bookId = bookId;
    }
    public String getBookName() {
        return bookName;
    }

    public void setBookName(String bookName) {
        this.bookName = bookName;
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
    public String getApplyStatus() {
        return applyStatus;
    }

    public void setApplyStatus(String applyStatus) {
        this.applyStatus = applyStatus;
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

**Apply 마이크로 서비스의 ApplyRepository.java**
```JAVA
package store;

import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

@RepositoryRestResource(collectionResourceRel="applies", path="applies")
public interface ApplyRepository extends PagingAndSortingRepository<Apply, Long>{

}

```

- DDD 적용 후 REST API의 테스트를 통하여 정상적으로 동작하는 것을 확인할 수 있었다.

**Apply서비스 교재 신청**
```
http POST http://20.196.242.11:8080/applies studentId="student1" studentName="홍길동" qty=10 amount=1000 applyStatus="completed" address="seoul" bookId="001" bookName="book001"
```

![증빙1](https://github.com/jinmojeon/elearningStudentApply/blob/main/Images/2-1-ddd-reg.png)

**Apply/Pag/Delivery/MyPage서비스 신청정보 조회**
```
http http://20.196.242.11:8080/applies
http http://20.196.242.11:8080/pays
http http://20.196.242.11:8080/deliveries
http http://20.196.242.11:8080/myPages
```

![증빙2](https://github.com/jinmojeon/elearningStudentApply/blob/main/Images/2-2-ddd-retrieve-1.png)

![증빙3](https://github.com/jinmojeon/elearningStudentApply/blob/main/Images/2-2-ddd-retrieve-2.png)


## CQRS/Saga/Correlation-key
- CQRS : Materialized View를 구현하여, 타 마이크로서비스의 데이터 원본에 접근없이(Composite 서비스나 조인SQL 등 없이)도 내 서비스의 화면 구성과 잦은 조회가 가능하게 구현해 두었다. 본 프로젝트에서 View 역할은 MyPages 서비스가 수행한다.

신청 / 결제 / 배송 서비스의 전체 현황 및 상태 조회를 제공하기 위해 MyPage를 구성하였다.

신규 교재 신청 정보를 등록한다.

**Apply 등록**

![증빙3](https://github.com/jinmojeon/elearningStudentApply/blob/main/Images/2-1-ddd-reg.png)

MyPage CQRS 결과는 아래와 같다

**Apply 실행 후 MyPages**

![증빙4](https://github.com/jinmojeon/elearningStudentApply/blob/main/Images/4-0-apply.png)

- Correlation-key 

Correlation을 Key를 활용하기 위해 Id를 Key값으로 사용하였으며 신청된 교재를 동일한 Id로 취소한다.

신청 취소가 되면 ApplyStatus가 cancelled로 Update 되는 것을 볼 수 있다.

**Apply서비스 교재 신청**
```
http PUT http://20.196.242.11:8080/applies/1 studentId="student1" studentName="홍길동" qty=10 amount=1000 applyStatus="cancelled" address="seoul" bookId="001" bookName="book001"
```

![증빙4](https://github.com/jinmojeon/elearningStudentApply/blob/main/Images/4-2-apply.png)

위와 같이 하게되면 Apply > Pay > Delivery > MyPage 순서로 신청이 처리된다.

![증빙4](https://github.com/jinmojeon/elearningStudentApply/blob/main/Images/4-3-apply.png)

위 결과로 서로 다른 마이크로 서비스 간에 ID값으로 상호 연결되어 있음을 알 수 있다.

## 폴리글랏 퍼시스턴스
- Apply 서비스의 DB와 MyPage의 DB를 다른 DB를 사용하여 폴리글랏 퍼시스턴스를 만족시키고 있다.(인메모리 DB인 hsqldb 사용)

**Apply의 pom.xml DB 설정 코드**

![증빙5](https://github.com/jinmojeon/elearningStudentApply/blob/main/Images/5-1-h2.png)

**MyPage의 pom.xml DB 설정 코드**

![증빙6](https://github.com/jinmojeon/elearningStudentApply/blob/main/Images/5-2-hsql.png)

**MyPage의 hsqldb 적용 서버 로그**

![증빙6](https://github.com/jinmojeon/elearningStudentApply/blob/main/Images/5-3-hsql-log.png)

## 동기식 호출 과 Fallback 처리

- 분석단계에서의 조건 중 하나로 결제 서비스(Pay)와 배송 서비스(Delivery) 간의 호출은 동기식 일관성을 유지하는 트랜잭션으로 처리하기로 하였다. 호출 프로토콜은 Rest Repository에 의해 노출되어있는 REST 서비스를 FeignClient를 이용하여 호출하도록 한다.

결제서비스를 호출하기 위하여 Stub과 (FeignClient) 를 이용하여 Service 대행 인터페이스 (Proxy) 를 구현

**Pay 서비스 내 external.DeliveryService**
```java
package store.external;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Date;

@FeignClient(name="Delivery", url="${api.url.delivery}") 
public interface DeliveryService {
    // command
    @RequestMapping(method = RequestMethod.POST, path = "/deliveries", consumes = "application/json")
    public void deliveryCancel(@RequestBody Delivery delivery);

}

```

### 동작 확인

**잠시 배송 서비스(Delivery) 중지**

![증빙7](https://github.com/jinmojeon/elearningStudentApply/blob/main/Images/6-1-delivery_stop.png)

**신청 취소 요청시 결제 서비스(Pay) 변화 없음**

```
http PUT http://20.196.242.11:8080/pay/1 studentId="student1" studentName="홍길동" qty=10 amount=1000 applyStatus="cancelled" address="seoul" bookId="001" bookName="book001"
```

![증빙8](https://github.com/jinmojeon/elearningStudentApply/blob/main/Images/6-2-1-cancel.png)

![증빙8](https://github.com/jinmojeon/elearningStudentApply/blob/main/Images/6-2-2-cancel.png)

**배송 서비스(Delivery) 기동 후 신청취소**

![증빙9](https://github.com/jinmojeon/elearningStudentApply/blob/main/Images/6-3-delete.png)

**결제 서비스(Pay) 상태를 보면 신청 정상 취소 처리**

![증빙9](https://github.com/jinmojeon/elearningStudentApply/blob/main/Images/6-4-paycancelled.png)

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
        System.out.println("@@@@@@@@@@@@@@@@@@@@@ StudentApply Pay service is BUSY @@@@@@@@@@@@@@@@@@@@@");
        System.out.println("@@@@@@@@@@@@@@@@@@@@@   Try again later   @@@@@@@@@@@@@@@@@@@@@");
    }
}

```

**Fallback 결과(Apply데이터 추가 시)**

![image](https://github.com/jinmojeon/elearningStudentApply/blob/main/Images/6-5-fallback.png)

## 비동기식 호출 / 시간적 디커플링 / 장애격리 / 최종 (Eventual) 일관성 테스트
- 결제가 이루어진 후에 배송 서비스로 이를 알려주는 행위는 동기식이 아니라 비동기식으로 처리하여 배송를 위하여 결제가 블로킹 되지 않도록 처리한다.
이를 위하여 결제서비스에 기록을 남긴 후에 곧바로 결제완료 되었다는 도메인 이벤트를 카프카로 송출한다(Publish)
``` JAVA
  ...
    @PostPersist
    public void onPostPersist(){
        // kafka publish
        PayCompleted payCompleted = new PayCompleted();
        BeanUtils.copyProperties(this, payCompleted);
        payCompleted.setApplyStatus("completed");
        payCompleted.publishAfterCommit();  
    }  
```

- 배송 서비스에서는 결제완료 이벤트에 대해서 이를 수신하여 자신의 정책을 처리하도록 PolicyHandler 를 구현한다:
``` JAVA
public class PolicyHandler{
 ...
    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverPayCompleted_Delivery(@Payload PayCompleted payCompleted){

        if (!payCompleted.validate()) return;
        
        Delivery delivery = new Delivery();
        delivery.setId(payCompleted.getId());
        delivery.setStudentId(payCompleted.getStudentId());
        delivery.setStudentName(payCompleted.getStudentName());
        delivery.setBookId(payCompleted.getBookId());
        delivery.setBookName(payCompleted.getBookName());
        delivery.setQty(payCompleted.getQty());
        delivery.setAmount(payCompleted.getAmount());
        delivery.setApplyStatus("completed");
        delivery.setDeliveryAddress(payCompleted.getAddress());
        deliveryRepository.save(delivery);
    
    }    
```

- 배송 서비스는 교재신청/결제와 완전히 분리되어있으며, 이벤트 수신에 따라 처리되기 때문에, 배송 서비스가 유지보수 등의 이류로 인해 잠시 내려간 상태라도 신청을 받는데 문제가 없다:
```
# 배송 서비스 (Delivery) 를 잠시 내려놓음
# 교재신청 처리 후 교재신청 및 결제 처리 Event 진행 확인
```

**Apply 신청**
![9](https://github.com/jinmojeon/elearningStudentApply/blob/main/Images/6-6-async-1.png)

**Kafka Publish 정보**
![10](https://github.com/jinmojeon/elearningStudentApply/blob/main/Images/6-7-async-2.png)

```
# 배송 서비스 기동
```

**배송 서비스(Delivery) Subscribe 정보**
![11](https://github.com/jinmojeon/elearningStudentApply/blob/main/Images/6-8-async-3.png)



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

* 소스 가져오기
```
git clone https://github.com/jinmojeon/elearningStudentApply.git
```

## ConfigMap
* Apply 서비스 deployment.yml 파일에 설정
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
![image](https://github.com/jinmojeon/elearningStudentApply/blob/main/Images/7-1-configmap.png)


**Apply 마이크로 서비스의 Apply.java**
```java 
@Entity
@Table(name="Apply_table")
public class Apply {
    ...
    @PostPersist
    public void onPostPersist(){
        String cfgServiceType = System.getenv("CFG_SERVICE_TYPE");
        if(cfgServiceType == null) cfgServiceType = "DEVELOP";
        System.out.println("################## CFG_SERVICE_TYPE: " + cfgServiceType);
    }
    ...
}
```

* Apply 데이터 1건 추가 후 로그 확인
```
kubectl logs -f {pod ID}
```
![image](https://github.com/jinmojeon/elearningStudentApply/blob/main/Images/7-2-configmap-print.png)


## Deploy / Pipeline

* build 하기
```
cd C:\Lv2Assessment\Source\elearningStudentApply

cd Apply
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
cd Apply
az acr build --registry grp01 --image grp01.azurecr.io/apply:v1 .
kubectl apply -f kubernetes/deployment.yml 
kubectl apply -f kubernetes/service.yaml 

cd .. 
cd Pay
az acr build --registry grp01 --image grp01.azurecr.io/pay:v1 .
kubectl apply -f kubernetes/deployment.yml 
kubectl apply -f kubernetes/service.yaml 

cd .. 
cd Delivery
az acr build --registry grp01 --image grp01.azurecr.io/delivery:v1 .
kubectl apply -f kubernetes/deployment.yml 
kubectl apply -f kubernetes/service.yaml 

cd .. 
cd MyPage
az acr build --registry grp01 --image grp01.azurecr.io/mypage:v1 .
kubectl apply -f kubernetes/deployment.yml 
kubectl apply -f kubernetes/service.yaml 

cd .. 
cd gateway
az acr build --registry grp01 --image grp01.azurecr.io/gateway:v1 .
kubectl apply -f kubernetes/deployment.yml 
kubectl apply -f kubernetes/service.yaml 
```

* Service, Pod, Deploy 상태 확인
![image](https://github.com/jinmojeon/elearningStudentApply/blob/main/Images/7-3-getall.png)


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
  name: apply
  labels:
    app: apply
spec:
  replicas: 1
  selector:
    matchLabels:
      app: apply
  template:
    metadata:
      labels:
        app: apply
    spec:
      containers:
        - name: apply
          image: grp01.azurecr.io/apply:v2
          ports:
            - containerPort: 8080
          env:
            - name: CFG_SERVICE_TYPE
              valueFrom:
                configMapKeyRef:
                  name: servicetype
                  key: svctype      
          readinessProbe:
            httpGet:
              path: '/actuator/health'
              port: 8080
            initialDelaySeconds: 10
            timeoutSeconds: 2
            periodSeconds: 5
            failureThreshold: 10
          livenessProbe:
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
* Apply -> Pay 와의 Req/Res 연결에서 요청이 과도한 경우 CirCuit Breaker 통한 격리
* Hystrix 를 설정: 요청처리 쓰레드에서 처리시간이 610 밀리가 넘어서기 시작하여 어느정도 유지되면 CB 회로가 닫히도록 (요청을 빠르게 실패처리, 차단) 설정

```yaml
# Apply서비스 application.yml

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

* C:\Lv2Assessment\Source\elearningStudentApply\Util\siege\kubernetes\deployment.yml
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
cd C:\Lv2Assessment\Source\elearningStudentApply\Util\siege\kubernetes
kubectl apply -f deployment.yml
```

* 부하테스터 siege 툴을 통한 서킷 브레이커 동작 확인: 동시사용자 100명 60초 동안 실시
```
kubectl exec -it pod/siege -c siege -- /bin/bash
siege -c100 -t60S  -v --content-type "application/json" 'http://{EXTERNAL-IP}:8080/applies POST {"studentId":"test123", "bookId":"bok123", "qty": "11", "amount":"2000"}'
siege –c100 –t60S  -v --content-type "application/json" 'http://20.196.242.11:8080/applies POST {"studentId":"test123", "bookId":"bok123", "qty": "11", "amount":"2000"}'
```
![image](https://github.com/jinmojeon/elearningStudentApply/blob/main/Images/7-4-siege.png)

![image](https://github.com/jinmojeon/elearningStudentApply/blob/main/Images/7-5-1-siege-result.png)



## 오토스케일 아웃
* 앞서 서킷 브레이커(CB) 는 시스템을 안정되게 운영할 수 있게 해줬지만 사용자의 요청을 100% 받아들여주지 못했기 때문에 이에 대한 보완책으로 자동화된 확장 기능을 적용하고자 한다.

* Apply 서비스 deployment.yml 설정
```yaml
          resources:
              limits:
                cpu: 500m
              requests:
                cpu: 200m
```
* 다시 배포해준다.
```
cd C:\Lv2Assessment\Source\elearningStudentApply\Apply
mvn package
az acr build --registry grp01 --image grp01.azurecr.io/apply:v2 .
kubectl apply -f kubernetes/deployment.yml

```

* Apply 서비스에 대한 replica 를 동적으로 늘려주도록 HPA 를 설정한다. 설정은 CPU 사용량이 50프로를 넘어서면 replica 를 2개까지 늘려준다

```
kubectl autoscale deploy apply --min=1 --max=2 --cpu-percent=50
```

* C:\Lv2Assessment\Source\elearningStudentApply\Util\siege\kubernetes\deployment.yml
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
cd C:\Lv2Assessment\Source\elearningStudentApply\Util\siege\kubernetes
kubectl apply -f deployment.yml
```

* 부하테스터 siege 툴을 통한 서킷 브레이커 동작 확인: 동시사용자 100명 60초 동안 실시
```
kubectl exec -it pod/siege -c siege -- /bin/bash
siege -c100 -t60S  -v --content-type "application/json" 'http://{EXTERNAL-IP}:8080/applies POST {"studentId":"test123", "bookId":"bok123", "qty": "11", "amount":"2000"}'
siege –c100 –t60S  -v --content-type "application/json" 'http://20.196.242.11:8080/applies POST {"studentId":"test123", "bookId":"bok123", "qty": "11", "amount":"2000"}'
```

* 오토스케일이 어떻게 되고 있는지 모니터링을 걸어둔다
```
kubectl get deploy apply -w
```
![image](https://github.com/jinmojeon/elearningStudentApply/blob/main/Images/8-1-autoscale-w.png)
```
kubectl get pod
```
![image](https://github.com/jinmojeon/elearningStudentApply/blob/main/Images/8-2-autoscale-pod.png)



## 무정지 재배포(Readiness Probe)

* 먼저 무정지 재배포가 100% 되는 것인지 확인하기 위해서 Autoscaler 이나 CB 설정을 제거함

- seige 로 배포작업 직전에 워크로드를 모니터링 함.
```
siege –c100 -t120S -r10 --content-type "application/json" 'http://Apply:8080/applies POST {"studentId":"test123", "bookId":"bok123", "qty": "11", "amount":"2000"}'


** SIEGE 4.0.5
** Preparing 100 concurrent users for battle.
The server is now under siege...

HTTP/1.1 201     0.68 secs:     207 bytes ==> POST http://Apply:8080/applies
HTTP/1.1 201     0.68 secs:     207 bytes ==> POST http://Apply:8080/applies
HTTP/1.1 201     0.70 secs:     207 bytes ==> POST http://Apply:8080/applies
HTTP/1.1 201     0.70 secs:     207 bytes ==> POST http://Apply:8080/applies
:

```

- 새버전으로의 배포 시작
```yaml
cd C:\Lv2Assessment\Source\elearningStudentApply\Apply
mvn package
az acr build --registry grp01 --image grp01.azurecr.io/apply:v2 .
kubectl apply -f kubernetes/deployment.yml
```

- seige 의 화면으로 넘어가서 Availability 가 100% 미만으로 떨어졌는지 확인
```
Transactions:		        3078 hits
Availability:		       70.45 %
Elapsed time:		       120 secs
Data transferred:	        0.34 MB
Response time:		        5.60 secs
Transaction rate:	       17.15 trans/sec
Throughput:		        0.01 MB/sec
Concurrency:		       96.02

```
배포기간중 Availability 가 평소 100%에서 70% 대로 떨어지는 것을 확인. 원인은 쿠버네티스가 성급하게 새로 올려진 서비스를 READY 상태로 인식하여 서비스 유입을 진행한 것이기 때문. 이를 막기위해 Readiness Probe 를 설정함:

* Apply 서비스 deployment.yml 파일에 Readiness Probe 부분 설정

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
cd C:\Lv2Assessment\Source\elearningStudentApply\Apply
mvn package
az acr build --registry grp01 --image grp01.azurecr.io/apply:v3 .
kubectl apply -f kubernetes/deployment.yml
```

- 동일한 시나리오로 재배포 한 후 Availability 확인:

![image](https://github.com/jinmojeon/elearningStudentApply/blob/main/Images/9-2-readiness-seige.png)

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
![image](https://github.com/jinmojeon/elearningStudentApply/blob/main/Images/10-1-liveness-port.png)

- Pod 재시작 확인
```
kubectl get pod -w
```
![image](https://github.com/jinmojeon/elearningStudentApply/blob/main/Images/10-2-liveness-pod.png)
