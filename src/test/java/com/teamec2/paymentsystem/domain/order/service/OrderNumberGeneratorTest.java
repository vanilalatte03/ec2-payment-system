package com.teamec2.paymentsystem.domain.order.service;

import com.teamec2.paymentsystem.domain.order.entity.OrderNumberSequence;
import com.teamec2.paymentsystem.domain.order.repository.OrderNumberSequenceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.dao.DataIntegrityViolationException;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderNumberGeneratorTest {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 29);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-05-29T10:15:30Z"),
            ZoneId.of("Asia/Seoul")
    );

    @Mock
    OrderNumberSequenceRepository orderNumberSequenceRepository;

    @Test
    void 주문번호는_날짜와_6자리순번으로_생성된다() {
        // given
        OrderNumberGenerator orderNumberGenerator = new OrderNumberGenerator(orderNumberSequenceRepository, FIXED_CLOCK);
        String today = TODAY.format(DATE_FORMATTER);
        OrderNumberSequence sequence = OrderNumberSequence.create(TODAY);

        when(orderNumberSequenceRepository.findForUpdate(TODAY))
                .thenReturn(Optional.of(sequence));

        // when
        String orderNumber = orderNumberGenerator.generate();

        // then
        assertThat(orderNumber).isEqualTo("ORD-" + today + "-000001");
    }

    @Test
    void 주문번호를_여러번_생성하면_순번이_1씩_증가한다() {
        // given
        OrderNumberGenerator orderNumberGenerator = new OrderNumberGenerator(orderNumberSequenceRepository, FIXED_CLOCK);
        String today = TODAY.format(DATE_FORMATTER);
        OrderNumberSequence sequence = OrderNumberSequence.create(TODAY);

        when(orderNumberSequenceRepository.findForUpdate(TODAY))
                .thenReturn(Optional.of(sequence));

        // when
        String firstOrderNumber = orderNumberGenerator.generate();
        String secondOrderNumber = orderNumberGenerator.generate();

        // then
        assertThat(firstOrderNumber).isEqualTo("ORD-" + today + "-000001");
        assertThat(secondOrderNumber).isEqualTo("ORD-" + today + "-000002");
    }

    @Test
    void 오늘_순번이_없으면_새로_만든뒤_첫번째_주문번호를_생성한다() {
        // given
        OrderNumberGenerator orderNumberGenerator = new OrderNumberGenerator(orderNumberSequenceRepository, FIXED_CLOCK);
        String today = TODAY.format(DATE_FORMATTER);

        when(orderNumberSequenceRepository.findForUpdate(TODAY))
                .thenReturn(Optional.empty());
        when(orderNumberSequenceRepository.saveAndFlush(any(OrderNumberSequence.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        String orderNumber = orderNumberGenerator.generate();

        // then
        assertThat(orderNumber).isEqualTo("ORD-" + today + "-000001");
    }

    @Test
    void 오늘_순번_생성중_중복저장되면_다시조회한뒤_다음순번으로_주문번호를_생성한다() {
        // given
        OrderNumberGenerator orderNumberGenerator = new OrderNumberGenerator(orderNumberSequenceRepository, FIXED_CLOCK);
        String today = TODAY.format(DATE_FORMATTER);
        OrderNumberSequence existingSequence = OrderNumberSequence.create(TODAY);
        increaseSequence(existingSequence, 10);

        when(orderNumberSequenceRepository.findForUpdate(TODAY))
                .thenReturn(Optional.empty(), Optional.of(existingSequence));
        when(orderNumberSequenceRepository.saveAndFlush(any(OrderNumberSequence.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate order date"));

        // when
        String orderNumber = orderNumberGenerator.generate();

        // then
        assertThat(orderNumber).isEqualTo("ORD-" + today + "-000011");
    }

    private void increaseSequence(OrderNumberSequence sequence, int count) {
        for (int i = 0; i < count; i++) {
            sequence.increaseAndGet();
        }
    }
}
