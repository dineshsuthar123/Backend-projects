package com.naresh.urlshortener.config;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class ReadReplicaRoutingDataSource extends AbstractRoutingDataSource {

    private final List<String> replicaKeys;
    private final AtomicInteger cursor = new AtomicInteger();

    ReadReplicaRoutingDataSource(List<String> replicaKeys) {
        this.replicaKeys = replicaKeys;
    }

    @Override
    protected Object determineCurrentLookupKey() {
        if (!replicaKeys.isEmpty() && TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            int index = Math.floorMod(cursor.getAndIncrement(), replicaKeys.size());
            return replicaKeys.get(index);
        }
        return "primary";
    }
}
