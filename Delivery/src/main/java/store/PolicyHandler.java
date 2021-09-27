package store;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import store.config.kafka.KafkaProcessor;

@Service
public class PolicyHandler {
    @Autowired
    DeliveryRepository deliveryRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverPayCompleted_Delivery(@Payload PayCompleted payCompleted) {

        if (!payCompleted.validate())
            return;

        System.out.println("\n\n##### listener Delivery : " + payCompleted.toJson() + "\n\n");

        System.out.println("\n\n##### listener Delivery Optional ID : " + payCompleted.getId() + "/CoffeeName"
                + payCompleted.getCoffeeName() + "\n\n");

        Delivery delivery = new Delivery();
        delivery.setId(payCompleted.getId());
        delivery.setCustomerId(payCompleted.getCustomerId());
        delivery.setCustomerName(payCompleted.getCustomerName());
        delivery.setCoffeeId(payCompleted.getCoffeeId());
        delivery.setCoffeeName(payCompleted.getCoffeeName());
        delivery.setQty(payCompleted.getQty());
        delivery.setAmount(payCompleted.getAmount());
        delivery.setOrderStatus("completed");
        delivery.setAddress(payCompleted.getAddress());
        deliveryRepository.save(delivery);

    }

    @StreamListener(KafkaProcessor.INPUT)
    public void whatever(@Payload String eventString) {
    }

}
