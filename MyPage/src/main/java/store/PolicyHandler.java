package store;

import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import store.config.kafka.KafkaProcessor;

@Service
public class PolicyHandler {

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverDeliveryCancelled_OrderStatusUpdate(@Payload DeliveryCancelled deliveryCancelled) {

        if (!deliveryCancelled.validate())
            return;

        System.out.println("\n\n##### listener OrderStatusUpdate : " + deliveryCancelled.toJson() + "\n\n");

        // Sample Logic //

    }

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverDeliveried_OrderStatusUpdate(@Payload Deliveried deliveried) {

        if (!deliveried.validate())
            return;

        System.out.println("\n\n##### listener OrderStatusUpdate : " + deliveried.toJson() + "\n\n");

        // Sample Logic //

    }

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverPayCompleted_OrderStatusUpdate(@Payload PayCompleted payCompleted) {

        if (!payCompleted.validate())
            return;

        System.out.println("\n\n##### listener OrderStatusUpdate : " + payCompleted.toJson() + "\n\n");

        // Sample Logic //

    }

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverPayCancelled_OrderStatusUpdate(@Payload PayCancelled payCancelled) {

        if (!payCancelled.validate())
            return;

        System.out.println("\n\n##### listener OrderStatusUpdate : " + payCancelled.toJson() + "\n\n");

        // Sample Logic //

    }

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverOrdered_OrderStatusUpdate(@Payload Ordered ordered) {

        if (!ordered.validate())
            return;

        System.out.println("\n\n##### listener OrderStatusUpdate : " + ordered.toJson() + "\n\n");

        // Sample Logic //

    }

    @StreamListener(KafkaProcessor.INPUT)
    public void wheneverOrderCancelled_OrderStatusUpdate(@Payload OrderCancelled orderCancelled) {

        if (!orderCancelled.validate())
            return;

        System.out.println("\n\n##### listener OrderStatusUpdate : " + orderCancelled.toJson() + "\n\n");

        // Sample Logic //

    }

    @StreamListener(KafkaProcessor.INPUT)
    public void whatever(@Payload String eventString) {
    }

}
