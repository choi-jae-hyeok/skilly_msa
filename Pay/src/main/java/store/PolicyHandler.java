package store;

import store.config.kafka.KafkaProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
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

    @StreamListener(KafkaProcessor.INPUT)
    public void whatever(@Payload String eventString) {
    }

}
