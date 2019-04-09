/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.core.bc;

import co.rsk.core.Coin;
import co.rsk.db.StateRootHandler;
import org.bouncycastle.util.encoders.Hex;
import org.ethereum.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * BlockTransactionExecutor has methods to reexecute a block with its transactions.
 * The main use case is:
 * - reexecute and log opcodes in JSON RPC debug_traceTransaction request
 * <p>
 * Created by ajlopez on 08/04/2019.
 */
public class BlockTransactionExecutor {
    private static final Logger logger = LoggerFactory.getLogger("blockexecutor");

    private final Repository repository;
    private final BlockExecutor.TransactionExecutorFactory transactionExecutorFactory;
    private final StateRootHandler stateRootHandler;

    public BlockTransactionExecutor(
            Repository repository,
            BlockExecutor.TransactionExecutorFactory transactionExecutorFactory,
            StateRootHandler stateRootHandler) {
        this.repository = repository;
        this.transactionExecutorFactory = transactionExecutorFactory;
        this.stateRootHandler = stateRootHandler;
    }

    /**
     * Execute a block, from initial state, returning the final state data.
     *
     * @param block        A block to validate
     * @param parent       The parent of the block to validate
     * @return BlockResult with the final state data.
     */
    public BlockResult execute(Block block, BlockHeader parent, boolean discardInvalidTxs) {
        return execute(block, parent, discardInvalidTxs, false);
    }

    public BlockResult executeAll(Block block, BlockHeader parent) {
        return execute(block, parent, false, true);
    }

    private BlockResult execute(Block block, BlockHeader parent, boolean discardInvalidTxs, boolean ignoreReadyToExecute) {
        logger.trace("applyBlock: block: [{}] tx.list: [{}]", block.getNumber(), block.getTransactionsList().size());

        byte[] lastStateRootHash = stateRootHandler.translate(parent).getBytes();
        Repository initialRepository = repository.getSnapshotTo(lastStateRootHash);

        Repository track = initialRepository.startTracking();
        int i = 1;
        long totalGasUsed = 0;
        Coin totalPaidFees = Coin.ZERO;
        List<TransactionReceipt> receipts = new ArrayList<>();
        List<Transaction> executedTransactions = new ArrayList<>();

        int txindex = 0;

        for (Transaction tx : block.getTransactionsList()) {
            logger.trace("apply block: [{}] tx: [{}] ", block.getNumber(), i);

            TransactionExecutor txExecutor = transactionExecutorFactory.newInstance(
                    tx,
                    txindex++,
                    block.getCoinbase(),
                    track,
                    block,
                    totalGasUsed
            );
            boolean readyToExecute = txExecutor.init();
            if (!ignoreReadyToExecute && !readyToExecute) {
                if (discardInvalidTxs) {
                    logger.warn("block: [{}] discarded tx: [{}]", block.getNumber(), tx.getHash());
                    continue;
                } else {
                    logger.warn("block: [{}] execution interrupted because of invalid tx: [{}]",
                                block.getNumber(), tx.getHash());
                    return BlockResult.INTERRUPTED_EXECUTION_BLOCK_RESULT;
                }
            }

            executedTransactions.add(tx);

            txExecutor.execute();
            txExecutor.go();
            txExecutor.finalization();

            logger.trace("tx executed");

            track.commit();

            logger.trace("track commit");

            long gasUsed = txExecutor.getGasUsed();
            totalGasUsed += gasUsed;
            Coin paidFees = txExecutor.getPaidFees();
            if (paidFees != null) {
                totalPaidFees = totalPaidFees.add(paidFees);
            }

            TransactionReceipt receipt = new TransactionReceipt();
            receipt.setGasUsed(gasUsed);
            receipt.setCumulativeGas(totalGasUsed);
            lastStateRootHash = initialRepository.getRoot();
            receipt.setTxStatus(txExecutor.getReceipt().isSuccessful());
            receipt.setTransaction(tx);
            receipt.setLogInfoList(txExecutor.getVMLogs());
            receipt.setStatus(txExecutor.getReceipt().getStatus());

            logger.trace("block: [{}] executed tx: [{}] state: [{}]", block.getNumber(), tx.getHash(),
                         Hex.toHexString(lastStateRootHash));

            logger.trace("tx[{}].receipt", i);

            i++;

            receipts.add(receipt);

            logger.trace("tx done");
        }

        return new BlockResult(executedTransactions, receipts, lastStateRootHash, totalGasUsed, totalPaidFees);
    }
}
