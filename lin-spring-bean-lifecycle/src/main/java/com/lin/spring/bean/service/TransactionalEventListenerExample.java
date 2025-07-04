package com.lin.spring.bean.service;

import com.lin.spring.bean.model.CustomSpringEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TransactionalEventListenerExample {

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleBeforeCommit(CustomSpringEvent event) {
        System.out.println("Handling event BEFORE transaction commit.");
    }
}
