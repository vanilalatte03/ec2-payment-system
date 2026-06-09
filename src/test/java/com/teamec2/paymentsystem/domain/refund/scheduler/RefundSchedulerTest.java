package com.teamec2.paymentsystem.domain.refund.scheduler;

import com.teamec2.paymentsystem.domain.refund.repository.RefundOutboxRepository;
import com.teamec2.paymentsystem.domain.refund.service.RefundProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundSchedulerTest {

    @Mock
    RefundOutboxRepository refundOutboxRepository;

    @Mock
    RefundProcessor refundProcessor;

    @InjectMocks
    RefundScheduler refundScheduler;

    @Test
    void 대기환불처리_처리대상outbox를_20개제한으로조회하고_각각처리한다() {
        // given
        when(refundOutboxRepository.findDuePendingIds(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(1L, 2L));

        // when
        refundScheduler.processPendingRefunds();

        // then
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(refundOutboxRepository).findDuePendingIds(any(LocalDateTime.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
        verify(refundProcessor).process(1L);
        verify(refundProcessor).process(2L);
    }

    @Test
    void 대기환불처리_일부outbox처리중예외가나도_다음outbox를계속처리한다() {
        // given
        when(refundOutboxRepository.findDuePendingIds(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(1L, 2L, 3L));
        doThrow(new RuntimeException("processor failed")).when(refundProcessor).process(2L);

        // when
        refundScheduler.processPendingRefunds();

        // then
        verify(refundProcessor).process(1L);
        verify(refundProcessor).process(2L);
        verify(refundProcessor).process(3L);
    }

    @Test
    void 오래된Processing환불처리_10분이전처리중outbox를조회해_복구한다() {
        // given
        when(refundOutboxRepository.findStaleProcessingIds(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(10L, 11L));

        // when
        refundScheduler.processStaleProcessingRefunds();

        // then
        ArgumentCaptor<LocalDateTime> thresholdCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(refundOutboxRepository).findStaleProcessingIds(thresholdCaptor.capture(), pageableCaptor.capture());
        assertThat(thresholdCaptor.getValue()).isBefore(LocalDateTime.now().minusMinutes(9));
        assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
        verify(refundProcessor).recoverStaleProcessing(10L);
        verify(refundProcessor).recoverStaleProcessing(11L);
    }

    @Test
    void 오래된Processing환불처리_복구중예외가나도_다음outbox를계속복구한다() {
        // given
        when(refundOutboxRepository.findStaleProcessingIds(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(10L, 11L, 12L));
        doThrow(new RuntimeException("recovery failed")).when(refundProcessor).recoverStaleProcessing(11L);

        // when
        refundScheduler.processStaleProcessingRefunds();

        // then
        verify(refundProcessor).recoverStaleProcessing(10L);
        verify(refundProcessor).recoverStaleProcessing(11L);
        verify(refundProcessor).recoverStaleProcessing(12L);
    }
}
