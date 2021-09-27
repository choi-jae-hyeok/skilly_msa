package store;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import store.config.kafka.KafkaProcessor;

@Service
public class MyPageViewHandler {

    @Autowired
    private MyPageRepository myPageRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void whenOrdered_then_CREATE_1(@Payload Ordered ordered) {
        try {

            if (!ordered.validate())
                return;

            // view 객체 생성
            MyPage myPage = new MyPage();
            // view 객체에 이벤트의 Value 를 set 함

            myPage.setId(ordered.getId());
            myPage.setCustomerId(ordered.getCustomerId());
            myPage.setCustomerName(ordered.getCustomerName());
            myPage.setCoffeeId(ordered.getCoffeeId());
            myPage.setCoffeeName(ordered.getCoffeeName());
            myPage.setQty(ordered.getQty());
            myPage.setAmount(ordered.getAmount());
            myPage.setOrderStatus("completed");
            myPage.setAddress(ordered.getAddress());
            // view 레파지 토리에 save
            myPageRepository.save(myPage);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @StreamListener(KafkaProcessor.INPUT)
    public void whenOrderCancelled_then_UPDATE_1(@Payload OrderCancelled orderCancelled) {
        try {
            if (!orderCancelled.validate())
                return;
            // view 객체 조회
            Optional<MyPage> myPageOptional = myPageRepository.findById(orderCancelled.getId());

            if (myPageOptional.isPresent()) {
                MyPage myPage = myPageOptional.get();
                // view 객체에 이벤트의 eventDirectValue 를 set 함
                myPage.setOrderStatus("cancelled");
                // view 레파지 토리에 save
                myPageRepository.save(myPage);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @StreamListener(KafkaProcessor.INPUT)
    public void whenPayCompleted_then_UPDATE_2(@Payload PayCompleted payCompleted) {
        try {
            if (!payCompleted.validate())
                return;
            // view 객체 조회
            Optional<MyPage> myPageOptional = myPageRepository.findById(payCompleted.getId());

            if (myPageOptional.isPresent()) {
                MyPage myPage = myPageOptional.get();
                // view 객체에 이벤트의 eventDirectValue 를 set 함
                myPage.setOrderStatus("completed");
                // view 레파지 토리에 save
                myPageRepository.save(myPage);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @StreamListener(KafkaProcessor.INPUT)
    public void whenPayCancelled_then_UPDATE_3(@Payload PayCancelled payCancelled) {
        try {
            if (!payCancelled.validate())
                return;
            // view 객체 조회
            Optional<MyPage> myPageOptional = myPageRepository.findById(payCancelled.getId());

            if (myPageOptional.isPresent()) {
                MyPage myPage = myPageOptional.get();
                // view 객체에 이벤트의 eventDirectValue 를 set 함
                myPage.setOrderStatus("cancelled");
                // view 레파지 토리에 save
                myPageRepository.save(myPage);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @StreamListener(KafkaProcessor.INPUT)
    public void whenDeliveried_then_UPDATE_4(@Payload Deliveried deliveried) {
        try {
            if (!deliveried.validate())
                return;
            // view 객체 조회
            Optional<MyPage> myPageOptional = myPageRepository.findById(deliveried.getId());

            if (myPageOptional.isPresent()) {
                MyPage myPage = myPageOptional.get();
                // view 객체에 이벤트의 eventDirectValue 를 set 함
                myPage.setOrderStatus("completed");
                // view 레파지 토리에 save
                myPageRepository.save(myPage);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @StreamListener(KafkaProcessor.INPUT)
    public void whenDeliveryCancelled_then_UPDATE_5(@Payload DeliveryCancelled deliveryCancelled) {
        try {
            if (!deliveryCancelled.validate())
                return;
            // view 객체 조회
            Optional<MyPage> myPageOptional = myPageRepository.findById(deliveryCancelled.getId());

            if (myPageOptional.isPresent()) {
                MyPage myPage = myPageOptional.get();
                // view 객체에 이벤트의 eventDirectValue 를 set 함
                myPage.setOrderStatus("cancelled");
                // view 레파지 토리에 save
                myPageRepository.save(myPage);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
