package com.teamec2.paymentsystem.domain.order.service;

import com.teamec2.paymentsystem.domain.order.entity.OrderNumberSequence;
import com.teamec2.paymentsystem.domain.order.repository.OrderNumberSequenceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class OrderNumberGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String ORDER_NUMBER_FORMAT = "ORD-%s-%06d";

    private final OrderNumberSequenceRepository orderNumberSequenceRepository;
    private final Clock clock;
    private final TransactionOperations requiresNewTransaction;

    @Autowired
    public OrderNumberGenerator(
            OrderNumberSequenceRepository orderNumberSequenceRepository,
            PlatformTransactionManager transactionManager
    ) {
        this(
                orderNumberSequenceRepository,
                Clock.systemDefaultZone(),
                requiresNew(transactionManager)
        );
    }

    OrderNumberGenerator(OrderNumberSequenceRepository orderNumberSequenceRepository, Clock clock) {
        this(orderNumberSequenceRepository, clock, withoutTransaction());
    }

    private OrderNumberGenerator(
            OrderNumberSequenceRepository orderNumberSequenceRepository,
            Clock clock,
            TransactionOperations requiresNewTransaction
    ) {
        this.orderNumberSequenceRepository = orderNumberSequenceRepository;
        this.clock = clock;
        this.requiresNewTransaction = requiresNewTransaction;
    }

    // 주문번호는 서버가 자동으로 만드는 고유값입니다.
    // 날짜별 순번 row를 비관락으로 잠근 뒤 증가시켜 ORD-20260529-000001 형태로 만듭니다.
    public String generate() {
        LocalDate today = LocalDate.now(clock);
        String date = today.format(DATE_FORMATTER);
        int sequenceNumber = nextNumber(today);

        return ORDER_NUMBER_FORMAT.formatted(date, sequenceNumber);
    }

    private int nextNumber(LocalDate orderDate) {
        try {
            return increaseOrCreate(orderDate);
        } catch (DataIntegrityViolationException exception) {
            return increaseExisting(orderDate);
        }
    }

    private int increaseOrCreate(LocalDate orderDate) {
        return requiresNewTransaction.execute(status ->
                orderNumberSequenceRepository.findForUpdate(orderDate)
                        .map(OrderNumberSequence::increaseAndGet)
                        .orElseGet(() -> createFirst(orderDate))
        );
    }

    private int increaseExisting(LocalDate orderDate) {
        return requiresNewTransaction.execute(status -> {
            OrderNumberSequence sequence = orderNumberSequenceRepository.findForUpdate(orderDate)
                    .orElseThrow(() -> new IllegalStateException("Order number sequence was not created."));

            return sequence.increaseAndGet();
        });
    }

    private int createFirst(LocalDate orderDate) {
        OrderNumberSequence sequence = OrderNumberSequence.create(orderDate);
        int sequenceNumber = sequence.increaseAndGet();
        orderNumberSequenceRepository.saveAndFlush(sequence);

        return sequenceNumber;
    }

    private static TransactionOperations requiresNew(PlatformTransactionManager transactionManager) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        return transactionTemplate;
    }

    private static TransactionOperations withoutTransaction() {
        return new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(null);
            }
        };
    }
}
