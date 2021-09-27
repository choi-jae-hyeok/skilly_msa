package store;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PostPersist;
import javax.persistence.PostUpdate;
import javax.persistence.Table;

import org.springframework.beans.BeanUtils;

import store.external.Delivery;
import store.external.DeliveryService;

@Entity
@Table(name = "Pay_table")
public class Pay {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)

    private Long id;
    private String customerId;
    private String customerName;
    private String coffeeId;
    private String coffeeName;
    private Integer qty;
    private double amount;
    private String orderStatus;
    private String address;

    @PostPersist
    public void onPostPersist() {
        System.out.println("################## Pay onPostPersist start");

        // modify by jjm
        // delay test시 주석해제
        // try {
        // Thread.currentThread().sleep((long) (400 + Math.random() * 220));
        // } catch (InterruptedException e) {
        // e.printStackTrace();
        // }
        // System.out.println("################## Pay onPostPersist currentThread end");

        // kafka publish
        PayCompleted payCompleted = new PayCompleted();
        BeanUtils.copyProperties(this, payCompleted);
        payCompleted.setOrderStatus("completed"); // modify by jjm
        payCompleted.publishAfterCommit();

        System.out.println("################## Pay onPostPersist end");
        // 임시주석처리
        // PayCancelled payCancelled = new PayCancelled();
        // BeanUtils.copyProperties(this, payCancelled);
        // payCancelled.publishAfterCommit();

        // Following code causes dependency to external APIs
        // it is NOT A GOOD PRACTICE. instead, Event-Policy mapping is recommended.

        // store.external.Delivery delivery = new store.external.Delivery();
        // // mappings goes here
        // PayApplication.applicationContext.getBean(store.external.DeliveryService.class).deliveryCancel(delivery);

    }

    // add by jjm
    @PostUpdate
    public void onPostUpdate() {
        System.out.println("################## Pay onPostUpdate start");
        // kafka publish
        PayCancelled payCancelled = new PayCancelled();
        BeanUtils.copyProperties(this, payCancelled);
        payCancelled.setOrderStatus("cancelled"); // 배송 완료 상태로 전달
        payCancelled.publish();

        // req/res 패턴 처리
        Delivery delivery = new Delivery();
        BeanUtils.copyProperties(payCancelled, delivery);
        // feignclient 호출
        PayApplication.applicationContext.getBean(DeliveryService.class).deliveryCancel(delivery);

        System.out.println("################## Pay onPostUpdate end");
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