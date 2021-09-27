package store;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PostPersist;
import javax.persistence.PostUpdate;
import javax.persistence.Table;

import org.springframework.beans.BeanUtils;

@Entity
@Table(name = "Delivery_table")
public class Delivery {

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
        Deliveried deliveried = new Deliveried();
        BeanUtils.copyProperties(this, deliveried);
        deliveried.setOrderStatus("completed");
        deliveried.publishAfterCommit();

        // DeliveryCancelled deliveryCancelled = new DeliveryCancelled();
        // BeanUtils.copyProperties(this, deliveryCancelled);
        // deliveryCancelled.publishAfterCommit();
    }

    // add by jjm
    @PostUpdate
    public void onPostUpdate() {
        DeliveryCancelled deliveryCancelled = new DeliveryCancelled();
        BeanUtils.copyProperties(this, deliveryCancelled);
        deliveryCancelled.setOrderStatus("cancelled");
        deliveryCancelled.publishAfterCommit();
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