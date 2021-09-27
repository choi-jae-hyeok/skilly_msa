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