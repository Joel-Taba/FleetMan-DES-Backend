// package com.yowyob.fleet.infrastructure.adapters.outbound.messaging;

// import com.yowyob.fleet.domain.model.Offer;
// import com.yowyob.fleet.domain.ports.out.OfferEventPublisherPort;
// import lombok.RequiredArgsConstructor;
// import org.springframework.beans.factory.annotation.Value;
// import lombok.extern.slf4j.Slf4j;
// import org.springframework.kafka.core.KafkaTemplate;
// import org.springframework.stereotype.Component;
// import reactor.core.publisher.Mono;

// @Slf4j
// @Component
// @RequiredArgsConstructor
// public class GeofenceAlertEventPublisherAdapter implements OfferEventPublisherPort {

//     private final KafkaTemplate<String, Offer> kafkaTemplate;

//     @Value("${application.kafka.topics.offer-created}")
//     private String topic;

//     @Override
//     public Mono<Void> publishOfferCreatedEvent(Offer offer) {
//         return Mono.fromFuture(kafkaTemplate.send(topic, offer.id().toString(), offer))
//                 .then();
//     }
// }